/* $Id$
 * $Revision$
 * $Date$
 * $Author$
 *
 * The Summa project.
 * Copyright (C) 2005-2008  The State and University Library
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
package dk.statsbiblioteket.summa.search;

import java.rmi.RemoteException;

import dk.statsbiblioteket.util.qa.QAInfo;

/* TODO: consider adding notification on queries/response time if not too costly
http://java.sun.com/j2se/1.5.0/docs/guide/jmx/tutorial/essential.html#wp1053109
    */

/**
 * Exposes the underlying {@link SummaSearcher} as an MBean and extends with
 * several control and statistic mechanisms.
 * @see SummaSearcher
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public interface SummaSearcherMBean extends SummaSearcher {
}
