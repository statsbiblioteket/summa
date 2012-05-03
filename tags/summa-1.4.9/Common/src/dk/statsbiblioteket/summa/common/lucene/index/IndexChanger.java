/* $Id: IndexChanger.java,v 1.2 2007/10/04 13:28:19 te Exp $
 * $Revision: 1.2 $
 * $Date: 2007/10/04 13:28:19 $
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
/**
 * Created: te 2007-08-28 11:15:28
 * CVS:     $Id: IndexChanger.java,v 1.2 2007/10/04 13:28:19 te Exp $
 */
package dk.statsbiblioteket.summa.common.lucene.index;

import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * An IndexChanger notifies listeners when the underlying Lucene index changes.
 * This is a classical implementation of the observer pattern.
 * @see IndexChangeListener, IndexChangeEvent
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public interface IndexChanger {
    /**
     * Add a listener to this observable. If a change happens to the Lucene
     * index, the listernes will be notified.
     * @param listener a listener for index events.
     */
    public void addListener(IndexChangeListener listener);

    /**
     * Remove a listener from this observable.
     * @param listener a listener for index events.
     */
    public void removeListener(IndexChangeListener listener);

}


