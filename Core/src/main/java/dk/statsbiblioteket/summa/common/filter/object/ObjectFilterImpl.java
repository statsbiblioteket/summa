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
package dk.statsbiblioteket.summa.common.filter.object;

import dk.statsbiblioteket.summa.common.Logging;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Filter;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.util.RecordStatsCollector;
import dk.statsbiblioteket.util.Timing;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

/**
 * Convenience implementation of ObjectFilter, suitable as a super-class for
 * filters. The implementation can only be chained after other ObjectFilters.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_OK,
        author = "te",
        comment = "Class needs JavaDoc")
public abstract class ObjectFilterImpl implements ObjectFilter {
    private Log log = LogFactory.getLog(ObjectFilterImpl.class.getName() + "#" + this.getClass().getSimpleName());

    /**
     * The feedback level used to log statistics to the process log.
     * Valid values are FATAL, ERROR, WARN, INFO, DEBUG and TRACE.
     * </p><p>
     * Optional. Default is TRACE.
     */
    public static final String CONF_PROCESS_LOGLEVEL = "process.loglevel";
    public static final Logging.LogLevel DEFAULT_FEEDBACK = Logging.LogLevel.TRACE;

    /**
     * Log overall status in the class log on INFO for every x Payloads processed.
     * </p><p>
     * Optional. Default is 0 (disabled).
     */
    public static final String CONF_STATUS_EVERY = "process.status.every";
    public static final int DEFAULT_STATUS_EVERY = 0;

    private ObjectFilter source;

    private String name;
    private Payload processedPayload = null;
    protected boolean feedback = true;
    private final int everyStatus;
    // If true, process-time statistics are logged after processPayload-calls
    private Logging.LogLevel processLogLevel;

    private final Timing timing;
    private final Timing timingPull;
    private final Timing timingProcess;
    private final RecordStatsCollector statsPull;
    private final RecordStatsCollector statsProcess;

    public ObjectFilterImpl(Configuration conf) {
        name = conf.getString(CONF_FILTER_NAME, this.getClass().getSimpleName());
        processLogLevel = Logging.LogLevel.valueOf(conf.getString(CONF_PROCESS_LOGLEVEL, DEFAULT_FEEDBACK.toString()));
        everyStatus = conf.getInt(CONF_STATUS_EVERY, DEFAULT_STATUS_EVERY);
        timing = new Timing(name, null, "Payload");
        timingPull = timing.getChild("pull", null, "Payload");
        timingProcess = timing.getChild("process", null, "Payload");
        statsPull = new RecordStatsCollector("in", conf);
        statsProcess = new RecordStatsCollector("out", conf);
        log.info("Created " + this);
    }

    // if hasNext is true, a processed Payload is ready for delivery
    @Override
    public boolean hasNext() {
        if (processedPayload != null) {
            return true;
        }
        checkSource();
        while (processedPayload == null && sourceHasNext()) {
            processedPayload = source.next();
            statsPull.process(processedPayload);
            if (processedPayload == null) {
                log.debug("hasNext(): Got null from source. This is legal but unusual. Skipping to next payload");
                continue;
            }
            final long startTime = System.nanoTime();
            try {
                log.trace("Processing Payload");
                boolean discard = !processPayload(processedPayload);
                statsProcess.process(processedPayload);
                final long ns = System.nanoTime() - startTime;
                timingProcess.addNS(ns);
                Logging.logProcess(name, "processPayload #" + timingProcess.getUpdates()
                                         + " finished in " + ns/1000000 + "ms for " + name,
                                   processLogLevel, processedPayload);
                if (discard) {
                    processedPayload.close();
                    processedPayload = null;
                    continue;
                }
            } catch (PayloadException e) {
                timingProcess.addNS(System.nanoTime() - startTime);
                Logging.logProcess(
                        name,
                        "processPayload failed with explicit PayloadException, Payload discarded",
                        Logging.LogLevel.WARN, processedPayload, e);
                processedPayload.close();
                processedPayload = null;
                continue;
            } catch (Exception e) {
                timingProcess.addNS(System.nanoTime() - startTime);
                Logging.logProcess(name, "processPayload failed, Payload discarded",
                                   Logging.LogLevel.WARN, processedPayload, e);
                processedPayload.close();
                //noinspection UnusedAssignment
                processedPayload = null;
                continue;
            } catch (Throwable t) {
                timingProcess.addNS(System.nanoTime() - startTime);
                /* Woops, this means major trouble, we dump everything we have and prepare to die */
                String msg = "Unexpected error on payload " + processedPayload.toString();
                String content = "";
                Record rec = processedPayload.getRecord();
                if (rec != null) {
                    msg += ", enclosed record : " + rec.toString(true);
                    content = "\nRecord content:\n" + rec.getContentAsUTF8();
                } else {
                    msg += ", no enclosed record";
                }
                Logging.fatal(log, "ObjectFilterImpl.hasNext", msg + content, t);
                throw new Error(msg, t);
            }

            long spendTime = System.nanoTime() - startTime;
            String ms = Double.toString(spendTime / 1000000.0);
            if (log.isTraceEnabled()) {
                //noinspection DuplicateStringLiteralInspection
                log.trace(getName() + " processed " + processedPayload + ", #" + timingProcess.getUpdates()
                          + ", in " + ms + " ms using " + this);
            } else if (log.isDebugEnabled() && feedback) {
                log.debug(getName() + " processed " + processedPayload + ", #" + timingProcess.getUpdates()
                          + ", in " + ms + " ms");
            }
            break;
        }
        if (everyStatus > 0 && timingProcess.getUpdates() % everyStatus == 0) {
            log.info(this);
        }
        return processedPayload != null;
    }

    private boolean sourceHasNext() {
        timingPull.start();
        try {
            return source.hasNext();
        } finally {
            timingPull.stop();
        }
    }

    @Override
    public Payload next() {
        //noinspection DuplicateStringLiteralInspection
        log.trace("next() called");
        if (!hasNext()) {
            throw new IllegalStateException("No more Payloads available");
        }
        Payload toDeliver = processedPayload;
        processedPayload = null;
        return toDeliver;
    }

    /**
     * Perform implementation-specific processing of the given Payload.
     * @param payload the Payload to process.
     * @return true if the processing resulted in a payload that should be
     *         preserved, false if the Payload should be discarded. Note that
     *         returning false will not raise any warnings and should be used
     *         where the discarding of the Payload is an non-exceptional event.
     *         An example of such use is the AbstractDiscardFilter.
     *         If the Payload is to be discarded because of an error, throw
     *         a PayloadException instead.
     * @throws PayloadException if it was not possible to process the Payload
     *         and if this means that further processing of the Payload does
     *         not make sense. Throwing this means that the Payload will be
     *         discarded by ObjectFilterImpl.
     */
    protected abstract boolean processPayload(Payload payload) throws PayloadException;

    @Override
    public void remove() {
        // Do nothing as default
    }

    @Override
    public void setSource(Filter filter) {
        if (filter == null) {
            //noinspection DuplicateStringLiteralInspection
            throw new IllegalArgumentException("Source filter was null");
        }
        if (!(filter instanceof ObjectFilter)) {
            throw new IllegalArgumentException(
                    "Only ObjectFilters accepted as source. The filter provided was of class " + filter.getClass());
        }
        source = (ObjectFilter)filter;
    }

    // TODO: Consider if close is a wise action - what about pooled ingests?
    @Override
    public boolean pump() throws IOException {
        checkSource();
        if (!hasNext()) {
            log.trace("pump(): hasNext() returned false");
            return false;
        }
        Payload payload = next();
        if (payload != null) {
            //noinspection DuplicateStringLiteralInspection
            Logging.logProcess("ObjectFilterImpl",
                               "Calling close for Payload as part of pump()",
                               Logging.LogLevel.TRACE, payload);
            payload.close();
        }
        return hasNext();
    }

    @Override
    public void close(boolean success) {
        log.info(String.format("Closing down '%s'", this));
        if (source != null) {
            source.close(success);
        }
    }

    private void checkSource() {
        if (source == null) {
            throw new IllegalStateException("No source defined for " + getClass().getSimpleName() + " filter");
        }
    }

    /**
     * @return statistics on processed Payloads.
     */
    public String getProcessStats() {
        //noinspection DuplicateStringLiteralInspection
        return timing.toString(false, false) + " size(" + statsPull + ", " + statsProcess + ")";
    }

    /**
     * @return the name of the filter, if specified. Else the class name of the object.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName() + "(feedback=" + feedback + ", processLogLevel=" + processLogLevel
               + ", stats=" + getProcessStats() + ")";
    }
}
