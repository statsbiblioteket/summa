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
package dk.statsbiblioteket.summa.storage.filter;

import java.rmi.RemoteException;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilterImpl;
import dk.statsbiblioteket.summa.storage.io.Access;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Connects to a MetadataStorage and ingests received Records into the storage.
 * The MetadataStorage is accessed via RMI at the address specified by
 * {@link #CONF_STORAGE}.
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
     * Example: //localhost:6789/storage;
     */
    public static final String CONF_STORAGE =
            "summa.storage.RecordWriter.MetadataStorage";

    private Access access;

    /**
     * Established an RMI connection to the Storage specified in configuration.
     * @param configuration contains setup information.
     * @see {@link #CONF_STORAGE}.
     */
    public RecordWriter(Configuration configuration) {
        log.trace("Constructing RecordWriter");
        access = FilterCommons.getAccess(configuration, CONF_STORAGE);
        // TODO: Perform a check to see if the MetadataStorage is alive
    }

    /**
     * Flushes Records to Storage.
     * @param payload the Payload containing the Record to flush.
     */
    protected void processPayload(Payload payload) {
        Record record = payload.getRecord();
        if (record == null) {
            throw new IllegalStateException("null received in Payload in next()"
                                            + ". This should not happen");
        }
        try {
            if (log.isTraceEnabled()) {
                //noinspection DuplicateStringLiteralInspection
                log.trace("Flushing " + record);
            } else {
                log.debug("Flushing record '" + record.getId() + "'");
            }
            access.flush(record);
        } catch (RemoteException e) {
            log.error("Exception flushing " + record, e);
            // TODO: Consider checking for fatal errors (the connection is down)
        }
    }

    // TODO: Close connection on EOF
}