/* $Id$
 * $Revision$
 * $Date$
 * $Author$
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.storage.api.filter;

import java.io.IOException;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.rpc.ConnectionConsumer;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilterImpl;
import dk.statsbiblioteket.summa.storage.api.StorageWriterClient;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.Profiler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Connects to a Storage and ingests received Records into the storage.
 * The Storage is accessed via RMI at the address specified by
 * {@link ConnectionConsumer#CONF_RPC_TARGET}.
 * </p><p>
 * Note: This ObjectFilter can only be chained after another ObjectFilter.
 * </p><p>
 * Note: Only Record is stored. All other data in Payload is ignored.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class RecordWriter extends ObjectFilterImpl {
    private static final Log log = LogFactory.getLog(RecordWriter.class);

    /**
     * The Storage to connect to. This is a standard RMI address.
     * Example: //localhost:27000/summa-storage;
     * Deprecated: use {@link ConnectionConsumer#CONF_RPC_TARGET} instead.
     */
    private static final String DEPRECATED_CONF_STORAGE =
            "summa.storage.recordwriter.storage";

    private StorageWriterClient storage;
    private Profiler profiler = new Profiler();

    /**
     * Established an RMI connection to the Storage specified in configuration.
     * @param conf contains setup information.
     * @see {@link ConnectionConsumer#CONF_RPC_TARGET}.
     * @throws java.io.IOException if the RecordWriter could not be constructed.
     */
    public RecordWriter(Configuration conf) throws IOException {
        log.trace("Constructing RecordWriter");
        if (conf.valueExists(DEPRECATED_CONF_STORAGE)) {
            log.warn(String.format("Old Storage address configuration detected."
                                   + " The key %s has been replaced by %s",
                                   DEPRECATED_CONF_STORAGE,
                                   ConnectionConsumer.CONF_RPC_TARGET));
        }
        storage = new StorageWriterClient(conf);

        // TODO: Perform a check to see if the Storage is alive
    }

    /**
     * Flushes Records to Storage.
     * @param payload the Payload containing the Record to flush.
     */
    @Override
    protected void processPayload(Payload payload) {
        Record record = payload.getRecord();
        if (record == null) {
            throw new IllegalStateException("null received in Payload in next()"
                                            + ". This should not happen");
        }
        try {
            if (log.isTraceEnabled()) {
                //noinspection DuplicateStringLiteralInspection
                log.trace("Flushing " + record.toString(true));
            } else {
                log.debug("Flushing record " + record);
            }
            long startTime = System.nanoTime();
            storage.flush(record);
            if (log.isTraceEnabled()) {
                log.trace(String.format(
                        "Flushed record to Storage in %.5f ms: %s",
                        (System.nanoTime() - startTime) / 1000000.0,
                        record));
            }
            profiler.beat();
        } catch (IOException e) {
            log.error("Exception flushing " + record, e);
            // TODO: Consider checking for fatal errors (the connection is down)
        }
    }

    @Override
    public synchronized void close(boolean success) {
        super.close(success);
        log.info("Closing down RecordWriter. " + getProcessStats()
                 + ". Total time: " + profiler.getSpendTime());
    }

    // TODO: Close connection on EOF
}


