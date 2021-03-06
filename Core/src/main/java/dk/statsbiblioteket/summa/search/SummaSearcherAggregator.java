/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.search;

import dk.statsbiblioteket.summa.common.Logging;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.SubConfigurationsNotSupportedException;
import dk.statsbiblioteket.summa.common.util.DeferredSystemExit;
import dk.statsbiblioteket.summa.common.util.MachineStats;
import dk.statsbiblioteket.summa.common.util.Pair;
import dk.statsbiblioteket.summa.search.api.*;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.search.api.document.DocumentResponse;
import dk.statsbiblioteket.summa.search.document.DocIDCollector;
import dk.statsbiblioteket.summa.search.document.DocumentSearcher;
import dk.statsbiblioteket.util.Profiler;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Straight forward aggregator for remote SummaSearchers that splits a request
 * to all searchers and merges the results. No load-balancing.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
// TODO: Rewrite requests to adjust number of records to return for paging
public class SummaSearcherAggregator implements SummaSearcher {
    private static Log log = LogFactory.getLog(SummaSearcherAggregator.class);

    /**
     * A list of configurations for connections to remote SummaSearchers.
     * Each configuration should contain all relevant {@link SearchClient}
     * properties and optionally {@link #CONF_SEARCHER_DESIGNATION} and
     * {@link #CONF_SEARCHER_THREADS}.
     * </p><p>
     * Mandatory.
     */
    public static final String CONF_SEARCHERS = "search.aggregator.searchers";

    /**
     * The designation for a remote SummaSearcher.
     * </p><p>
     * Optional. Default is
     * {@link dk.statsbiblioteket.summa.common.rpc.ConnectionConsumer#CONF_RPC_TARGET}
     * for the given SummaSearcher.
     */
    public static final String CONF_SEARCHER_DESIGNATION = "search.aggregator.searcher.designation";

    /**
     * The maximum number of threads. A fixed list is created, so don't set
     * this too high.
     * </p><p>
     * Optional. Default is the number of remote searchers times 3.
     */
    public static final String CONF_SEARCHER_THREADS = "search.aggregator.threads";
    public static final int DEFAULT_SEARCHER_THREADS_FACTOR = 3;

    /**
     * A list of active searchers, referenced by their designation.
     * </p><p>
     * Optional. Default is all the searchers in {@link #CONF_SEARCHERS}.
     */
    public static final String CONF_ACTIVE = "search.aggregator.active";
    public static final String SEARCH_ACTIVE = CONF_ACTIVE;

    /**
     * A list of searchers that are not active. This is search-time only and meant for easy
     * disabling of specific searchers, instead of explicitly stating all the active searchers.
     */
    public static final String SEARCH_NOT_ACTIVE = "search.aggregator.notactive";

    /**
     * If true, any OutOfMemoryError detected, including those thrown by RMI-accessed services, will result in a
     * complete shutdown of the current JVM.
     * </p><p>
     * Optional. Default is true.
     */
    public static final String CONF_SHUTDOWN_ON_OOM = "summa.oom.shutdown";
    public static final boolean DEFAULT_SHUTDOWN_ON_OOM = true;

    private List<Pair<String, SearchClient>> searchers;
    private ExecutorService executor;
    private final List<String> defaultSearchers;
    private final Profiler profiler = new Profiler(Integer.MAX_VALUE, 100);
    private final MachineStats machineStats;
    private final boolean oomShutdown;

    public SummaSearcherAggregator(Configuration conf) {
        preConstruction(conf);
        List<Configuration> searcherConfs;
        try {
            searcherConfs = conf.getSubConfigurations(CONF_SEARCHERS);
        } catch (SubConfigurationsNotSupportedException e) {
            throw new ConfigurationException("Storage doesn't support sub configurations", e);
        } catch (NullPointerException e) {
            throw new ConfigurationException("Unable to extract sub-configurations for " + CONF_SEARCHERS, e);
        }
        log.debug("Constructing SummaSearcherAggregator with " + searcherConfs.size() + " remote SummaSearchers");
        searchers = new ArrayList<>(searcherConfs.size());
        List<String> created = new ArrayList<>(searcherConfs.size());
        for (Configuration searcherConf: searcherConfs) {
            SearchClient searcher = createClient(searcherConf);
            String searcherName = searcherConf.getString(CONF_SEARCHER_DESIGNATION, searcher.getVendorId());
            created.add(searcherName);
            searchers.add(new Pair<>(searcherName, searcher));
            log.debug("Connected to " + searcherName + " at " + searcher.getVendorId());
        }
        this.defaultSearchers = conf.getStrings(CONF_ACTIVE, created);
        int threadCount = searchers.size() * DEFAULT_SEARCHER_THREADS_FACTOR;
        if (conf.valueExists(CONF_SEARCHER_THREADS)) {
            threadCount = conf.getInt(CONF_SEARCHER_THREADS);
        }
        oomShutdown = conf.getBoolean(CONF_SHUTDOWN_ON_OOM, DEFAULT_SHUTDOWN_ON_OOM);
        //noinspection DuplicateStringLiteralInspection
        log.debug("Creating Executor with " + threadCount + " threads");
        executor = Executors.newFixedThreadPool(threadCount);
        machineStats = conf.getBoolean(MachineStats.CONF_ACTIVE, true) ? new MachineStats(conf) : null;

        log.info("Constructed " + this);
    }

    protected void preConstruction(Configuration conf) {
        // Override if needed
    }

    /**
     * Creates a searchClient from the given configuration. Intended to be
     * overridden in subclasses that want special SearchClients.
     * @param searcherConf the configuration for the SearchClient.
     * @return a SearchClient build for the given configuration.
     */
    protected SearchClient createClient(Configuration searcherConf) {
        return new SearchClient(searcherConf);
    }

    /**
     * Merges response collections after the searches has been performed.
     * Intended to be overridden in subclasses than wants custom merging.
     * @param request the original request that resulted in the responses.
     * @param responses a collection of responses, one from each searcher.
     * @return a merge of the responses.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected ResponseCollection merge(Request request, List<ResponseHolder> responses) {
        ResponseCollection merged = new ResponseCollection();
        addMergedTiming(request, responses, merged);
        return merged;
    }

    protected void addMergedTiming(Request request, List<ResponseHolder> responses, ResponseCollection merged) {
        for (ResponseHolder holder: responses) {
            merged.addAll(holder.getResponses());
            if (!"".equals(holder.getResponses().getTopLevelTiming())) {
                merged.addTiming(holder.getResponses().getTopLevelTiming());
            }
        }
    }

    protected void preProcess(Request request) {
        log.trace("No preprocessing for this node");
    }

    // TODO: Add explicit handling of query rewriting for paging
    @Override
    public ResponseCollection search(Request request) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Starting search for " + request);
        }
        final long startTime = System.currentTimeMillis();
        if (machineStats != null) {
            machineStats.ping();
        }
        final String originalRequest = request.toString(true);
        preProcess(request);
        ResponseCollection merged = null;
        boolean success = false;
        try {
            if (searchers.isEmpty()) {
                throw new IOException("No remote summaSearchers connected");
            }

            long startIndex = request.getLong(DocumentKeys.SEARCH_START_INDEX, 0L);
            // TODO: We should over-ask maxRecords to raise probability of getting the right group merge-result
            List<String> getIDs = request.getStrings(DocumentKeys.SEARCH_IDS, (List<String>) null);
            long maxRecords = request.getLong(
                    DocumentKeys.SEARCH_MAX_RECORDS,
                    (long)(getIDs == null ? DocumentKeys.DEFAULT_MAX_RECORDS : getIDs.size()));
            if (startIndex > 0) {
                if (log.isTraceEnabled()) {
                    log.trace(
                            "Start index is " + startIndex + " and maxRecords is " + maxRecords
                            + ". Setting startIndex=0 and maxRecords=" + (startIndex + maxRecords) + " for " + request);
                }
                request.put(DocumentKeys.SEARCH_START_INDEX, 0);
                request.put(DocumentKeys.SEARCH_MAX_RECORDS, startIndex + maxRecords);
            }

            List<String> selected = request.getStrings(SEARCH_ACTIVE, defaultSearchers);
            List<String> notActives = request.getStrings(SEARCH_NOT_ACTIVE, (List<String>)null);
            if (notActives != null) {
                selected.removeAll(notActives);
            }
//            if (request.containsKey(DocumentKeys.SEARCH_MAX_RECORDS)) {
                // Ask for a bit more to guard against semi-random order for records with same score
//                request.put(DocumentKeys.SEARCH_MAX_RECORDS,
//                            request.getInt(DocumentKeys.SEARCH_MAX_RECORDS) + selected.size() * 2);
//            }
            List<Pair<String, Future<ResponseCollection>>> searchFutures = new ArrayList<>(selected.size());
            for (Pair<String, SearchClient> searcher: searchers) {
                if (selected.contains(searcher.getKey())) {
                    searchFutures.add(new Pair<>(
                            searcher.getKey(),
                            executor.submit(new SearcherCallable(searcher.getKey(), searcher.getValue(), request))));
                } else {
                    log.trace("search(...) skipping searcher " + searcher.getKey() + " as it is not asked for");
                }
            }
            log.trace("All searchers started, collecting and waiting");


            List<ResponseHolder> responses = new ArrayList<>(searchFutures.size());
            for (Pair<String, Future<ResponseCollection>> searchFuture: searchFutures) {
                try {
                    responses.add(new ResponseHolder(searchFuture.getKey(), request, searchFuture.getValue().get()));
                } catch (InterruptedException e) {
                    throw new IOException(
                            "Interrupted while waiting for searcher result from " + searchFuture.getKey(), e);
                } catch (ExecutionException e) {
                    throw new IOException(
                            "ExecutionException while requesting search result from " + searchFuture.getKey(), e);
                } catch (Exception e) {
                    throw new IOException("Exception while requesting search result from " + searchFuture.getKey(), e);
                }
            }
            merged = merge(request, responses);
            postProcessPaging(merged, startIndex, maxRecords);

            log.debug("Finished search in " + (System.currentTimeMillis() - startTime) + " ms");
            merged.addTiming("aggregator.searchandmergeall", System.currentTimeMillis() - startTime);
            success = true;
            return merged;
        } catch (Exception e) {
            if (oomShutdown && DeferredSystemExit.containsOOM(e)) {
                String message = "Inner OutOfMemoryError detected while performing aggregate search for "
                                 + request.toString(true) + ". Shutting down this JVM in 5 seconds";
                Logging.fatal(log, "SummaSearcherAggregator", message);
                new DeferredSystemExit(5, 5000);
                throw new IOException(message, e);
            } else {
                String message = "Encountered Exception while performing aggregate search for "
                                 + request.toString(true);
                log.warn(message, e);
                throw new IOException(message, e);
            }
        } finally {
            profiler.beat();
            if (merged == null) {
                queries.info("SummaSearcherAggregator finished "
                             + (success ? "successfully" : "unsuccessfully (see logs for errors)")
                             + " in " + (System.currentTimeMillis()-startTime) + "ms. "
                             + "Request was " + originalRequest + ". " + getStats());
            } else {
                if (merged.getTransient() != null && merged.getTransient().containsKey(DocumentSearcher.DOCIDS)) {
                    Object o = merged.getTransient().get(DocumentSearcher.DOCIDS);
                    if (o instanceof DocIDCollector) {
                        ((DocIDCollector)o).close();
                    }
                }
                if (queries.isInfoEnabled()) {
                    String hits = "N/A";
                    for (Response response: merged) {
                        if (response instanceof DocumentResponse) {  // If it's there, we might as well get some stats
                            hits = Long.toString(((DocumentResponse)response).getHitCount());
                        }
                    }
                    queries.info("SummaSearcherAggregator finished "
                                 + (success ? "successfully" : "unsuccessfully (see logs for errors)")
                                 + " in " + (System.currentTimeMillis()-startTime) / 1000000 + "ms with "
                                 + hits + " hits. Request was " + originalRequest
                                 + " with Timing(" + merged.getTiming() + "). " + getStats());
                }
            }
        }
    }

    public static class ResponseHolder {
        private final String searcherID;
        private final Request request;
        private final ResponseCollection responses;

        public ResponseHolder(String searcherID, Request request, ResponseCollection responses) {
            this.searcherID = searcherID;
            this.request = request;
            this.responses = responses;
        }

        public String getSearcherID() {
            return searcherID;
        }
        public Request getRequest() {
            return request;
        }
        public ResponseCollection getResponses() {
            return responses;
        }
    }

    private void postProcessPaging(ResponseCollection merged, long startIndex, long maxRecords) {
        // We over-ask so we always need this step
/*        if (startIndex == 0) {
            log.trace("No paging fix needed");
            return;
        }*/
        log.trace("Fixing paging with startIndex=" + startIndex + " and maxRecords=" + maxRecords);
        for (Response response: merged) {
            if (!(response instanceof DocumentResponse)) {
                continue;
            }
            DocumentResponse docResponse = (DocumentResponse)response;
/*            System.out.println("*** postAggregate Group count " + docResponse.getGroups().size());
            for (DocumentResponse.Group group: docResponse.getGroups()) {
                System.out.println("Group " + group.get(0).getScore() + " " + group.get(0).getFieldValue("group", "N/A"));
            }
  */
            docResponse.setStartIndex(startIndex);
            docResponse.setMaxRecords(maxRecords);
            if (docResponse.isGrouped()) {
                List<DocumentResponse.Group> groups = docResponse.getGroups();
                if (groups.size() < startIndex) {
                    groups.clear();
                } else {
                    // new ArrayList to ensure Serializability
                    groups = new ArrayList<>(
                            groups.subList((int)startIndex, (int)Math.min(groups.size(), startIndex+maxRecords)));
                }
                docResponse.setGroups(groups);
            } else {
                List<DocumentResponse.Record> records = docResponse.getRecords();
                if (records.size() < startIndex) {
                    records.clear();
                } else {
                    // new ArrayList to ensure Serializability
                    records = new ArrayList<>(
                            records.subList((int)startIndex, (int)Math.min(records.size(), startIndex+maxRecords)));
                }
                docResponse.setRecords(records);
            }
        }
    }

    @Override
    public void close() throws IOException {
        log.info("Close called for aggregator. Closing each searcher and shutting down executor");
        for (Pair<String, SearchClient> searchPair: searchers) {
            searchPair.getValue().close();
        }
        searchers.clear();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("close(): Timed out (1 minute) when waiting for executor to terminate");
            }
        } catch (InterruptedException e) {
            log.warn("close(): Interrupted while awaiting termination on executor", e);
        }
    }

    private static class SearcherCallable implements Callable<ResponseCollection> {
        private String designation;
        private SearchClient client;
        private Request request;

        private SearcherCallable(String designation, SearchClient client, Request request) {
            //noinspection DuplicateStringLiteralInspection
            log.trace("Creating " + designation + " Future");
            this.designation = designation;
            this.client = client;
            this.request = request;
        }

        @Override
        public ResponseCollection call() throws Exception {
            long searchStart = System.currentTimeMillis();
            ResponseCollection result = client.search(request);
            result.addTiming("aggregator.searchcall." + designation, System.currentTimeMillis() - searchStart);
            return result;
        }

        public String getDesignation() {
            return designation;
        }
    }

    private String getStats() {
        return "Stats(#queries=" + profiler.getBeats()
               + ", q/s(last " + profiler.getBpsSpan() + ")=" + profiler.getBps(true);
    }

    @Override
    public String toString() {
        String s = "";
        for (Pair<String, SearchClient> searcher: searchers) {
            if (!s.isEmpty()) {
                s += ", ";
            }
            s+= searcher.getKey();
        }
        return "SummaSearcherAggregator(searchers=[" + s + "], defaultSearchers=[" + Strings.join(defaultSearchers)
               + "], shutdownOnOOM=" + oomShutdown + ", " + getStats() + ")";
    }
}
