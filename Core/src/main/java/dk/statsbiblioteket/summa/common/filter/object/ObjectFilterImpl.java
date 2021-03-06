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
import dk.statsbiblioteket.summa.common.util.PayloadMatcher;
import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * Convenience implementation of ObjectFilter, suitable as a super-class for
 * filters. The implementation can only be chained after other ObjectFilters.
 * </p><p>
 * This implementation handles the updating of timing and size statistics,
 * as well as calls to {@link #logStatusIfNeeded()}.
 * Sub-classes need only implement {@link #processPayload(Payload)}.
 * </p>
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_OK,
        author = "te",
        comment = "Class needs JavaDoc")
public abstract class ObjectFilterImpl extends ObjectFilterBase {

    public enum UNMATCH_ACTION {passthrough, discard, fail}
    /**
     * If any payload matching is defined (see {@link PayloadMatcher} for properties) and the source Payload does not
     * match, this is the action to take.
     * </p><p>
     * Optional. Default is passthrough.
     */
    public static final String CONF_UNMATCHED = "unmatched.action";
    public static final UNMATCH_ACTION DEFAULT_UNMATCHED = UNMATCH_ACTION.passthrough;

    /**
     * If no PayloadMatcher properties are defined, all incoming Payloads will match.
     */
    private final PayloadMatcher payloadMatcher;
    private final ObjectFilterImpl.UNMATCH_ACTION unmatchAction;

    private ObjectFilter source;
    private Payload processedPayload = null;

    public ObjectFilterImpl(Configuration conf) {
        super(conf);
        payloadMatcher = new PayloadMatcher(conf, "objectfilter", false);
        unmatchAction = ObjectFilterImpl.UNMATCH_ACTION.valueOf(
                conf.getString(ObjectFilterImpl.CONF_UNMATCHED, ObjectFilterImpl.DEFAULT_UNMATCHED.toString()));
//        log.info("Created " + this);
    }

    // if hasNext is true, a processed Payload is ready for delivery
    @Override
    public boolean hasNext() {
        if (processedPayload != null) {
            return true;
        }
        checkSource();
        outer:
        while (processedPayload == null && sourceHasNext()) {
            processedPayload = sourceNext();
            if (processedPayload == null) {
                log.debug(getName() + ": hasNext(): Got null from source. This is legal but unusual. " +
                          "Skipping to next payload");
                continue;
            }

            if (payloadMatcher.isMatcherActive() && !payloadMatcher.isMatch(processedPayload)) {
                //log.debug(getName() + "### active matcher & !matching with unaction " + unmatchAction +" for " + processedPayload);
                switch (unmatchAction) {
                    case discard: {
                        log.debug(getName() + ": Discarding payload as payloadMatcher matches and action is " +
                                  unmatchAction + ": " + processedPayload);
                        continue;
                    }
                    case fail: throw new RuntimeException(
                            "Raising exception as payloadMatcher matches and action is " + unmatchAction +
                            ": " + processedPayload);
                    case passthrough: {
                        // Breaking here means thet the Payload will be passed through unprocessed
                        continue outer; // Not sure how continue deals with switch inside while, so go on the defensive
                    }
                    default: throw new UnsupportedOperationException("Unknown action " + unmatchAction);
                }
            }

            final long startTime = System.nanoTime();
            boolean exception = true;
            try {
                log.trace("Processing Payload");
                boolean discard = !processPayload(processedPayload);
                sizeProcess.process(processedPayload);
                exception = false;
                if (discard) {
                    processedPayload.close();
                    processedPayload = null;
                    continue;
                }
            } catch (PayloadException e) {
                Logging.logProcess(
                        getName(),
                        "processPayload failed with explicit PayloadException, Payload discarded",
                        Logging.LogLevel.WARN, processedPayload, e);
                processedPayload.close();
                processedPayload = null;
                continue;
            } catch (Exception e) {
                Logging.logProcess(getName(), "processPayload failed, Payload discarded",
                                   Logging.LogLevel.WARN, processedPayload, e);
                processedPayload.close();
                //noinspection UnusedAssignment
                processedPayload = null;
                continue;
            } catch (Throwable t) {
                /* Whoops, this means major trouble, we dump everything we have and prepare to die */
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
            } finally {
                timingProcess.addNS(System.nanoTime() - startTime);
                if (!exception) {
                    logProcess(processedPayload, System.nanoTime() - startTime);
                }
            }

            logStatusIfNeeded();
            break;
        }
        return processedPayload != null;
    }

    private Payload sourceNext() {
        timingPull.start();
        try {
            Payload next = source.next();
            sizePull.process(next);
            return next;
        } finally {
        timingPull.stop();
        }
    }

    private boolean sourceHasNext() {
        timingPull.start();
        try {
            return source.hasNext();
        } finally {
            timingPull.stop(timingPull.getUpdates()); // hasNext does not count as a full update
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
     *
     * @param payload the Payload to process.
     * @return true if the processing resulted in a payload that should be
     * preserved, false if the Payload should be discarded. Note that
     * returning false will not raise any warnings and should be used
     * where the discarding of the Payload is an non-exceptional event.
     * An example of such use is the AbstractDiscardFilter.
     * If the Payload is to be discarded because of an error, throw
     * a PayloadException instead.
     * @throws PayloadException if it was not possible to process the Payload
     *                          and if this means that further processing of the Payload does
     *                          not make sense. Throwing this means that the Payload will be
     *                          discarded by ObjectFilterImpl.
     */
    protected abstract boolean processPayload(Payload payload) throws PayloadException;

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
        source = (ObjectFilter) filter;
    }

    @Override
    public void close(boolean success) {
        super.close(success);
        if (source != null) {
            source.close(success);
        }
    }

    private void checkSource() {
        if (source == null) {
            throw new IllegalStateException("No source defined for " + getClass().getSimpleName() + " filter");
        }
    }
}
