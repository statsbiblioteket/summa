/* $Id: MARCXMLDirectoryDigester.java,v 1.12 2007/10/05 10:20:24 te Exp $
 * $Revision: 1.12 $
 * $Date: 2007/10/05 10:20:24 $
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
package dk.statsbiblioteket.summa.ingest.MARCXML;

import java.io.File;

import dk.statsbiblioteket.summa.ingest.Digester;
import dk.statsbiblioteket.summa.ingest.ParserTask;
import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * Provides funtionality to digest a set of MARC-XML data dumps.<br>
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "hal")
public class MARCXMLDirectoryDigester extends Digester {

    protected ParserTask createParser(File f) {
        return new MARCParserTask(f,target,in);
    }

}