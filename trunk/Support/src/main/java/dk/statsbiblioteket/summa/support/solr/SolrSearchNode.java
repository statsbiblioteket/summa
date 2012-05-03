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
package dk.statsbiblioteket.summa.support.solr;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.util.Pair;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetKeys;
import dk.statsbiblioteket.summa.search.SearchNodeImpl;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.summa.support.api.LuceneKeys;
import dk.statsbiblioteket.summa.support.summon.search.FacetQueryTransformer;
import dk.statsbiblioteket.summa.support.summon.search.SolrFacetRequest;
import dk.statsbiblioteket.summa.support.summon.search.SolrResponseBuilder;
import dk.statsbiblioteket.summa.support.summon.search.SummonResponseBuilder;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.lucene.queryparser.classic.ParseException;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.*;

/**
 * A wrapper for Solr web calls, transforming requests and responses from and to Summa equivalents.
 * */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class SolrSearchNode extends SearchNodeImpl {
    private static Log log = LogFactory.getLog(SolrSearchNode.class);

    // TODO: Assign mandatory ID, use it for timing and result delivery
    /**
     * The entry point for calls to Solr.
     * </p><p>
     * Optional. Default is localhost:8983 (Solr default).
     */
    public static final String CONF_SOLR_HOST = "solr.host";
    public static final String DEFAULT_SOLR_HOST = "localhost:8983";
    /**
     * The rest call at {@link #CONF_SOLR_HOST}.
     * </p><p>
     * Optional. Default is '/solr' (Solr default).
     */
    public static final String CONF_SOLR_RESTCALL = "solr.restcall";
    public static final String DEFAULT_SOLR_RESTCALL = "/solr/select";
    /**
     * The prefix will be added to all IDs returned by Solr.
     * </p><p>
     * Optional. Default is empty.
     */
    public static final String CONF_SOLR_IDPREFIX = "solr.id.prefix";
    public static final String DEFAULT_SOLR_IDPREFIX = "";
    /**
     * The default number of documents results to return from a search.
     * </p><p>
     * This can be overridden with {@link dk.statsbiblioteket.summa.search.api.document.DocumentKeys#SEARCH_MAX_RECORDS}.
     * </p><p>
     * Optional. Default is 15.
     */
    public static final String CONF_SOLR_DEFAULTPAGESIZE = "solr.defaultpagesize";
    public static final int DEFAULT_SOLR_DEFAULTPAGESIZE = 15;
    /**
     * The default facets if none are specified. The syntax is a comma-separated
     * list of facet names, optionally with max tags in paranthesis.
     * This can be overridden with {@link dk.statsbiblioteket.summa.facetbrowser.api.FacetKeys#SEARCH_FACET_FACETS}.
     * Specifying the empty string turns off faceting.
     * </p><p>
     * Optional. Default is {@link #DEFAULT_SOLR_FACETS}.
     */
    public static final String CONF_SOLR_FACETS = "solr.facets";
    public static final String DEFAULT_SOLR_FACETS = "";
    /**
     * The default number of tags tags to show in a facet if not specified
     * elsewhere.
     * </p><p>
     * Optional. Default is 15.
     */
    public static final String CONF_SOLR_FACETS_DEFAULTPAGESIZE = "solr.facets.defaultpagesize";
    public static final int DEFAULT_SOLR_FACETS_DEFAULTPAGESIZE = 15;
    /**
     * Whether facets should be searched with and or or.
     * Optional. Default is 'and'. Can only be 'and' or 'or'.
     */
    public static final String CONF_SOLR_FACETS_COMBINEMODE = "solr.facets.combinemode";
    public static final String DEFAULT_SOLR_FACETS_COMBINEMODE = "and";


    /**
     * If true, calls to Solr assumes that pure negative filters (e.g. "NOT foo NOT bar") are supported.
     * If false, pure negative filters are handled by rewriting the query to "(query) filter", so if query is "baz"
     * and the filter is "NOT foo NOT bar", the result is "(baz) NOT foo NOT bar".
     * Note that rewriting also requires the {@link DocumentKeys#SEARCH_FILTER_PURE_NEGATIVE} parameter to be true.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String CONF_SUPPORTS_PURE_NEGATIVE_FILTERS = "solr.purenegativefilters.support";
    public static final boolean DEFAULT_SUPPORTS_PURE_NEGATIVE_FILTERS = false;

    /**
     * If true, the SolrSearchNode does not attempt to extract facet-query from the query and passes the query and
     * filter through unmodified. Mainly used for debugging.
     * </p><p>
     * Optional. Default is false.
     */
    public static final String SEARCH_PASSTHROUGH_QUERY = "solr.passthrough.query";
    public static final boolean DEFAULT_PASSTHROUGH_QUERY = false;

    /**
     * Properties with this prefix are added to the Solr query. Values are
     * lists of Strings. If one or more #CONF_SOLR_PARAM_PREFIX are
     * specified as part of a search query, the parameters are added to the
     * configuration defaults. Existing params with the same key are
     * overwritten.
     * </p><p>
     * Optional. Default is empty.
     */
    public static final String CONF_SOLR_PARAM_PREFIX = "solrparam.";
    /**
     * Search-time variation of {@link #CONF_SOLR_PARAM_PREFIX}.
     */

    /**
     * If true, {@link DocumentKeys#SEARCH_FILTER} must contain simple facet queries only. A simple facet query is
     * one or more {@code facet:term} pairs, optionally prefixed with {@code -} or {@code NOT}.
     * </p><p>
     * Valid sample query: {@code foo:bar -zoo:baz +ak:ve AND loo:poo NOT bum:bam}.
     * </p><p>
     * Note: This is basically an ugly hack until we switch to treating facet filtering as first class.
     */
    public static final String SEARCH_SOLR_FILTER_IS_FACET = "solr.filterisfacet";

    //    private static final DateFormat formatter =
    //        new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
    protected SolrResponseBuilder responseBuilder;
    protected final String host;
    protected final String restCall;
    protected final String idPrefix;
    protected final int defaultPageSize;
    protected final int defaultFacetPageSize;
    protected final String defaultFacets;
    protected final String combineMode;
    protected final Map<String, List<String>> solrDefaultParams;
    protected final boolean supportsPureNegative;
    protected final FacetQueryTransformer facetQueryTransformer;

    public SolrSearchNode(Configuration conf) throws RemoteException {
        super(conf);
        setID(conf.getString(CONF_ID, "solr"));
        responseBuilder = createResponseBuilder(conf);
        solrDefaultParams = new HashMap<String, List<String>>();

        host = conf.getString(CONF_SOLR_HOST, DEFAULT_SOLR_HOST);
        restCall = conf.getString(CONF_SOLR_RESTCALL, DEFAULT_SOLR_RESTCALL);
        idPrefix =   conf.getString(CONF_SOLR_IDPREFIX, DEFAULT_SOLR_IDPREFIX);
        defaultPageSize = conf.getInt(CONF_SOLR_DEFAULTPAGESIZE, DEFAULT_SOLR_DEFAULTPAGESIZE);
        defaultFacetPageSize = conf.getInt(CONF_SOLR_FACETS_DEFAULTPAGESIZE, DEFAULT_SOLR_FACETS_DEFAULTPAGESIZE);
        defaultFacets = conf.getString(CONF_SOLR_FACETS, DEFAULT_SOLR_FACETS);
        combineMode = conf.getString(CONF_SOLR_FACETS_COMBINEMODE, DEFAULT_SOLR_FACETS_COMBINEMODE);
        supportsPureNegative = conf.getBoolean(
            CONF_SUPPORTS_PURE_NEGATIVE_FILTERS, DEFAULT_SUPPORTS_PURE_NEGATIVE_FILTERS);
        facetQueryTransformer = createFacetQueryTransformer(conf);
        readyWithoutOpen();
        log.info("Created SolrSearchNode(" + getID() + ")");
    }

    /**
     * Create a response builder from Solr to Summa responses. Override this to get parsing of responses that differ
     * from standard Solr.
     * @param conf base configuration for the transformer.
     * @return a search backend specific transformer.
     */
    protected SolrResponseBuilder createResponseBuilder(Configuration conf) {
        return new SolrResponseBuilder(conf);
    }

    /**
     * Create a transformer from Lucene search syntax queries into Solr facet queries. Override this to get specific
     * facet queries for searchers that differ from standard Solr.
     * @param conf base configuration for the transformer.
     * @return a search backend specific transformer.
     */
    protected FacetQueryTransformer createFacetQueryTransformer(Configuration conf) {
        return new FacetQueryTransformer(conf);
    }

    /**
     * Sort and optionally urlencodes a query string
     * @param queryParameters A Map<String, List<String>> containing the query parameters
     * @param urlencode Whether or not to urlencode the query parameters
     * @return A sorted and urlencoded query string
     */
    protected static String computeSortedQueryString(Map<String, List<String>> queryParameters, boolean urlencode) {
        List<String> parameterStrings = new ArrayList<String>();

        // for each parameter, get its key and values
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            // for each value, create a string in the format key=value
            for (String value : entry.getValue()) {
                if (urlencode) {
                    try {
                        parameterStrings.add(entry.getKey() + "=" + URLEncoder.encode(value, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("Unable to encode '" + value + "' to UTF-8. UTF-8 support is "
                                                   + "required for Summa to function", e);
                    }
                } else {
                    parameterStrings.add(entry.getKey() + "=" + value);
                }
            }
        }

        // sort the individual parameters
        Collections.sort(parameterStrings);
        return Strings.join(parameterStrings, "&");
    }

    @Override
    protected void managedSearch(Request request, ResponseCollection responses) throws RemoteException {
        try {
            barrierSearch(request, responses);
        } catch (StackOverflowError e) {
            String message = String.format(
                "Caught StackOverflow at outer level during handling of Solr request %s:\n%s",
                request.toString(true), reduceStackTrace(request, e));
            log.error(message, e);
            throw new RemoteException("SolrSearchNode.managedSearch: " + message);
        }
    }

    private void barrierSearch(
        Request request, ResponseCollection responses) throws RemoteException {
        long startTime = System.currentTimeMillis();
        if (request.containsKey(LuceneKeys.SEARCH_MORELIKETHIS_RECORDID)) {
            log.trace("MoreLikeThis search is not supported yet, returning immediately");
            return;
        }

        String rawQuery = getEmptyIsNull(request, DocumentKeys.SEARCH_QUERY);
        String filter =  getEmptyIsNull(request, DocumentKeys.SEARCH_FILTER);
        String sortKey = getEmptyIsNull(request, DocumentKeys.SEARCH_SORTKEY);
        if (DocumentKeys.SORT_ON_SCORE.equals(sortKey)) {
            sortKey = null; // null equals relevance ranking
        }
        boolean reverseSort = request.getBoolean(DocumentKeys.SEARCH_REVERSE, false);
        int startIndex = request.getInt(DocumentKeys.SEARCH_START_INDEX, 0);
        int maxRecords = request.getInt(DocumentKeys.SEARCH_MAX_RECORDS, defaultFacetPageSize);
        boolean collectdocIDs = request.getBoolean(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        boolean passThroughQuery = request.getBoolean(SEARCH_PASSTHROUGH_QUERY, DEFAULT_PASSTHROUGH_QUERY);

        if (rawQuery == null && filter == null) {
            log.debug("No filter or query, proceeding anyway as other params might be specified");
        }

        String query;
        query = "*".equals(rawQuery) ? "*:*" : rawQuery;

        String facetsDef =  request.getString(FacetKeys.SEARCH_FACET_FACETS, defaultFacets);
        if ("".equals(facetsDef)) {
            facetsDef = null;
        }
        SolrFacetRequest facets = null == facetsDef || "".equals(facetsDef) ? null :
                                  createFacetRequest(facetsDef, defaultFacetPageSize, combineMode);

        Map<String, List<String>> solrSearchParams = new HashMap<String, List<String>>(solrDefaultParams);
        for (Map.Entry<String, Serializable> entry : request.entrySet()) {
            convertSolrParam(solrSearchParams, entry);
        }

        if (query != null && !passThroughQuery) {
            query = convertQuery(query, solrSearchParams);
        }
        if ("".equals(query)) {
            query = null;
        }
        if (filter != null && !passThroughQuery && !request.getBoolean(SEARCH_SOLR_FILTER_IS_FACET, false)) {
            filter = convertQuery(filter, solrSearchParams);
        }
        if ("".equals(filter)) {
            filter = null;
        }

        long searchTime = -System.currentTimeMillis();
        log.trace("Performing search for '" + query + "' with facets '" + facets + "'");
        String solrResponse;
        String solrTiming;
        try {
            Pair<String, String> sums = solrSearch(
                request, filter, query, solrSearchParams, collectdocIDs ? facets : null,
                startIndex, maxRecords, sortKey, reverseSort, responses);
            solrResponse = sums.getKey();
            solrTiming = sums.getValue();
        } catch (StackOverflowError e) {
            String message = String.format("Caught StackOverflow while performing Solr request %s:\n%s",
                                           request.toString(true), reduceStackTrace(request, e));
            log.error(message, e);
            throw new RemoteException("SolrSearchNode.barrierSearch: " + message);
        }
        if (solrResponse == null || "".equals(solrResponse)) {
            throw new RemoteException("Solr search for '" + query + "' yielded empty result");
        }
        searchTime += System.currentTimeMillis();

        long buildResponseTime = -System.currentTimeMillis();
        long hitCount;
        try {
//            System.out.println(solrResponse.replace(">", ">\n"));
            hitCount = responseBuilder.buildResponses(request, facets, responses, solrResponse, solrTiming);
        } catch (XMLStreamException e) {
            String message = "Unable to transform Solr XML response to Summa response for '" + request + "'";
            if (log.isDebugEnabled()) {
                log.debug(message + ". Full XML follows:\n" + solrResponse);
            }
            throw new RemoteException(message, e);
        } catch (StackOverflowError e) {
            String message = String.format(
                "Caught StackOverflow while building response for Solr request %s\nReduced stack trace:\n%s\n"
                + "Reduced raw Solr response:\n%s",
                request.toString(true), reduceStackTrace(request, e),
                solrResponse.length() > 2000 ? solrResponse.substring(0, 2000) : solrResponse);
            log.error(message, e);
            throw new RemoteException("SolrSearchNode.barrierSearch: " + message);

        }
        buildResponseTime += System.currentTimeMillis();

        log.debug("fullSearch(..., " + filter + ", " + rawQuery + ", " + startIndex + ", " + maxRecords + ", "
                  + sortKey + ", " + reverseSort + ") with " + hitCount + " hits finished in " + searchTime + " ms ("
                  + searchTime + " ms for remote search " + "call, " + buildResponseTime + " ms for converting to "
                  + "Summa response)");
        responses.addTiming(getID() + ".search.buildresponses", buildResponseTime);
        responses.addTiming(getID() + ".search.total", (System.currentTimeMillis() - startTime));
    }

    // Override this to get search backend specific facet request syntax
    protected SolrFacetRequest createFacetRequest(String facetsDef, int defaultFacetPageSize, String combineMode) {
        return new SolrFacetRequest(facetsDef, defaultFacetPageSize, combineMode);
    }

    /**
     * Extracts parameters with key prefix "solrparam." and stores the truncated keys with their value(s) as a list of
     * Strings.
     * </p><p>
     * If the key is not prefixed by "solrparam.", it is ignored.
     * </p>
     * @param solrParam the map where the key/value is stored.
     * @param entry the source for the key/value pair.
     */
    protected void convertSolrParam(Map<String, List<String>> solrParam, Map.Entry<String, Serializable> entry) {
        if (!entry.getKey().startsWith(CONF_SOLR_PARAM_PREFIX)) {
            log.trace("convertSolrParam got unsupported key " + entry.getKey() + ". Ignoring entry");
            return;
        }
        String key = entry.getKey().substring(CONF_SOLR_PARAM_PREFIX.length(), entry.getKey().length());
        if (entry.getValue() instanceof String) {
            solrParam.put(key, Arrays.asList((String) entry.getValue()));
            if (log.isTraceEnabled()) {
                log.trace("convertSolrParam assigning " + key + ":" + entry.getValue());
            }
        } else if (entry.getValue() instanceof List) {
            ArrayList<String> values = new ArrayList<String>();
            for (Object v: (List)entry.getValue()) {
                if (v instanceof String) {
                    values.add((String)v);
                } else {
                    log.warn("Expected List entry of type String in Solr parameter " + key + ", but got Object of "
                             + "class " + v.getClass());
                }
            }
            if (values.size() == 0) {
                log.warn("Got empty list for Solr param " + key + ". Ignoring");
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("convertSolrParam assigning list " + key + ":" + Strings.join(values, ", "));
                }
                solrParam.put(key, values);
            }
        } else if (entry.getValue() instanceof String[]) {
            if (log.isTraceEnabled()) {
                log.trace("convertSolrParam assigning array " + key + ":"
                          + Strings.join((String[]) entry.getValue(), ", "));
            }
            solrParam.put(key, Arrays.asList((String[]) entry.getValue()));
        } else {
            log.warn("convertSolrParam expected String, List<String> or String[] for key " + key
                     + " but got value of class " + entry.getValue().getClass());
        }
    }

    private String getEmptyIsNull(Request request, String key) {
        String response = request.getString(key, null);
        return "".equals(response) ? null : response;
    }

    private boolean warmupCalled = false;
    @Override
    protected void managedWarmup(String request) {
        if (!warmupCalled) {
            log.debug("No warmup for '" + request + "' as warmup is handled externally. Further requests "
                      + "for warmup will be silently ignored");
            warmupCalled = true;
        }
    }

    @Override
    protected void managedOpen(String location) throws RemoteException {
        log.info("Open called with location '" + location + "' which is "
                 + "ignored by this search node as it is stateless");
    }

    @Override
    protected void managedClose() throws RemoteException {
        log.debug("managedClose() called. No effect as this search node is stateless");
    }


    /**
     * Optionally converts the query to conform to searcher specific syntax.
     * @param query            the input query.
     * @param solrSearchParams parameters that will be passed to Solr.
     * @return the converted query.
     */
    protected String convertQuery(String query, Map<String, List<String>> solrSearchParams) {
        log.trace("Default convertQuery does not change the query");
        return query;
    }

    /**
     * Perform a search in Solr. Override this to get different behaviour for search backends other than standard Solr.
     *
     * @param request    a standard Summa Search request, primarily filled with values from {@link DocumentKeys}.
     * @param filter     a Solr-style filter (same syntax as query). This is based on {@link DocumentKeys#SEARCH_FILTER}
     *                   but might have been rewritten.
     * @param query      a Solr-style query. This is based on {@link DocumentKeys#SEARCH_QUERY} but might have been
     *                   rewritten.
     * @param solrParams optional extended params for Solr. If not null, these will be added to the Solr request.
     * @param facets     which facets to request or null if no facets are wanted.
     * @param startIndex the index for the first Record to return, counting from 0.
     * @param maxRecords number of items per page.
     * @param sortKey    the field to sort on. If null, default ranking sort is used.
     * @param reverseSort if true, sort order is reversed.
     * @param responses  results are stored here.
     * @return XML with the search result as per Solr API followed by timing information.
     * @throws java.rmi.RemoteException if there were an error performing the remote search call.
     */
    protected Pair<String, String> solrSearch(
        Request request, String filter, String query, Map<String, List<String>> solrParams, SolrFacetRequest facets,
        int startIndex, int maxRecords, String sortKey, boolean reverseSort, ResponseCollection responses)
                                                                                                throws RemoteException {
        long buildQuery = -System.currentTimeMillis();
        log.trace("Calling simpleSearch(" + query + ", " + facets + ", " + startIndex + ", " + maxRecords + ")");
        Map<String, List<String>> queryMap;
        try {
            queryMap = buildSolrQuery(
                request, filter, query, solrParams, facets, startIndex, maxRecords, sortKey, reverseSort);
        } catch (ParseException e) {
            throw new RemoteException("Unable to build Solr query", e);
        }

        String queryString = computeSortedQueryString(queryMap, true);
        buildQuery += System.currentTimeMillis();
        log.trace("Parameter preparation done in " + buildQuery + "ms");
        String result;

        try {
            result = getData(restCall + "?" + queryString, responses);
        } catch (Exception e) {
            throw new RemoteException(
                "Unable to perform remote call to "  + host + restCall + " with argument '" + queryString, e);
        }
        log.trace("simpleSearch done in " + (System.currentTimeMillis() - buildQuery) + "ms");
        return new Pair<String, String>(result, "solr.buildquery:" + buildQuery);

    }

    private String getData(String command, ResponseCollection responses) throws IOException {
        StringBuilder retval = new StringBuilder();

        if (log.isDebugEnabled()) {
            log.debug("Performing Summon request for '" + command + "'");
        }

        URL url = new URL("http://" + host + command);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Host", host);
        conn.setRequestProperty("Accept", "application/xml");
        conn.setRequestProperty("Accept-Charset", "utf-8");
        conn.setConnectTimeout(1000);
        Long readStart = System.currentTimeMillis();
    	long summonConnect = -System.currentTimeMillis();
        conn.connect();
        summonConnect += System.currentTimeMillis();

        BufferedReader in;
        try {
        	long rawCall = -System.currentTimeMillis();
        	in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String str;

            while ((str = in.readLine()) != null) {
                retval.append(str);
            }
            log.trace("Reading from Solr done in " + (System.currentTimeMillis() - readStart) + "ms");
            in.close();
            rawCall += System.currentTimeMillis();
            responses.addTiming(getID() + ".connect", summonConnect);
            responses.addTiming(getID() + ".rawcall", rawCall);

        } catch (IOException e) {
            String error = String.format(
                "getData(host='%s', command='%s') for %s failed with error stream\n%s",
                "http://" + host, command, getID(),
                Strings.flush(new InputStreamReader(conn.getErrorStream(), "UTF-8")));
            log.warn(error, e);
            throw new IOException(error, e);
        }
        // TODO: Should we disconnect?

        return retval.toString();
    }

    /**
     * Generate a map of search backend specific request parameters.
     * @param request    a standard Summa Search request, primarily filled with values from {@link DocumentKeys}.
     * @param filter     a Solr-style filter (same syntax as query). This is based on {@link DocumentKeys#SEARCH_FILTER}
     *                   but might have been rewritten.
     * @param query      a Solr-style query. This is based on {@link DocumentKeys#SEARCH_QUERY} but might have been
     *                   rewritten.
     * @param solrParams optional extended params for Solr. If not null, these will be added to the Solr request.
     * @param facets     which facets to request or null if no facets are wanted.
     * @param startIndex the index for the first Record to return, counting from 0.
     * @param maxRecords number of items per page.
     * @param sortKey    the field to sort on. If null, default ranking sort is used.
     * @param reverseSort if true, sort order is reversed.
     * @return key-value map with multiple values/key.
     * @throws ParseException if the facets could not be parsed.
     */
    protected Map<String, List<String>> buildSolrQuery(
        Request request, String filter, String query, Map<String, List<String>> solrParams, SolrFacetRequest facets,
        int startIndex, int maxRecords, String sortKey, boolean reverseSort) throws ParseException {
        int startPage = maxRecords == 0 ? 0 : ((startIndex-1) / maxRecords);
        Map<String, List<String>> queryMap = new HashMap<String, List<String>>();

        if (filter != null) { // We allow missing filter
            boolean facetsHandled = false;
            if (request.getBoolean(SEARCH_SOLR_FILTER_IS_FACET, false)) {
                Map<String, List<String>> facetRequest = facetQueryTransformer.convertQueryToFacet(filter);
                if (facetRequest == null) {
                    log.debug("Unable to convert facet filter '" + filter + "' to Solr facet request. Switching to "
                              + "filter/query based handling");
                } else {
                    log.debug("Successfully converted filter '" + filter + "' to Solr facet query");
                    queryMap.putAll(facetRequest);
                    facetsHandled = true;
                }
            }
            if (!facetsHandled) {
                if (supportsPureNegative || !request.getBoolean(DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE, false)) {
                    queryMap.put("fq", Arrays.asList(filter)); // FilterQuery
                } else {
                    if (query == null) {
                        throw new IllegalArgumentException(
                            "No query and filter marked with '" + DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE
                            + "' is not possible in Solr. Filter is '" + filter + "'");
                    }
                    query = "(" + query + ") " + filter;
                    log.debug("Munging filter after query as the filter '" + filter + "' is marked '"
                              + DocumentKeys.SEARCH_FILTER_PURE_NEGATIVE + "' and Solr is set up to not support pure "
                              + "negative filters natively. resulting query is '" + query + "'");
                }
            }
        }
        if (query != null) { // We allow missing query
            queryMap.put("q", Arrays.asList(query));
        }

        queryMap.put("start", Arrays.asList(Integer.toString(startPage)));
        queryMap.put("rows", Arrays.asList(Integer.toString(maxRecords)));

        // TODO: Add support for sorting on multiple fields
        if (reverseSort && sortKey == null) {
            sortKey = "score"; // Relevance sorting
        }
        if (sortKey != null) {
            queryMap.put("sort", Arrays.asList(sortKey + " " + (reverseSort ? "desc" : "asc")));
        }

        if (facets != null) { // The facets to display
            queryMap.put("facet", Arrays.asList(Boolean.TRUE.toString()));
            facets.addFacetQueries(queryMap);
        }

        if (solrParams != null) {
            queryMap.putAll(solrParams);
        }
        return queryMap;
    }
}