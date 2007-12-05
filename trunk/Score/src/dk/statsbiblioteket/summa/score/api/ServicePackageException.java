/* $Id: ServicePackageException.java,v 1.3 2007/10/11 12:56:25 te Exp $
 * $Revision: 1.3 $
 * $Date: 2007/10/11 12:56:25 $
 * $Author: te $
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
package dk.statsbiblioteket.summa.score.api;

import dk.statsbiblioteket.summa.score.client.Client;
import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * A runtime exception thrown if there is an error loading a {@link Service}
 * or reading a service package file.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke",
        comment="The method needs Javadoc")
public class ServicePackageException extends RuntimeException {

    public ServicePackageException (Client client, String id, String msg) {
        super (msg + ", for client " + client + ", service '" + id + "'");
    }

}
