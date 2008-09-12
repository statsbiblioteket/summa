/* $Id: SearchWS.java,v 1.2 2007/10/04 13:28:21 mv Exp $
 * $Revision: 1.2 $
 * $Date: 2007/10/04 13:28:21 $
 * $Author: mv $
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
package dk.statsbiblioteket.summa.web.services;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.SearchClient;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.commons.XmlOperations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * A class containing methods meant to be exposed as a web service
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mv")
public class SearchWS {
    private Log log;

    SearchClient searcher;
    Configuration conf;

    public SearchWS() {
        log = LogFactory.getLog(SearchWS.class);
    }

    /**
     * Get a single SearchClient based on the system configuration.
     * @return A SearchClient.
     */
    private synchronized SearchClient getSearchClient() {
        if (searcher == null) {
            if (conf == null) {
                conf = Configuration.getSystemConfiguration(true);
            }
            searcher = new SearchClient(conf);
        }
        return searcher;
    }

    /**
     * A simple way to query the index returning results sorted by relevance. The same as calling
     * simpleSearchSorted while specifying a normal sort on relevancy.
     * @param query The query to perform.
     * @param numberOfRecords The maximum number of records to return.
     * @param startIndex Where to start returning records from (used to implement paging).
     * @return An XML string containing the result or an error description.
     */
    public String simpleSearch(String query, int numberOfRecords, int startIndex) {
        return simpleSearchSorted(query, numberOfRecords, startIndex, DocumentKeys.SORT_ON_SCORE, false);
    }

    /**
     * A simple way to query the index wile being able to specify which field to sort by and whether the sorting
     * should be reversed.
     * @param query The query to perform.
     * @param numberOfRecords The maximum number of records to return.
     * @param startIndex Where to start returning records from (used to implement paging).
     * @param sortKey The field to sort by.
     * @param reverse Whether or not the sort should be reversed.
     * @return An XML string containing the result or an error description.
     */
    public String simpleSearchSorted(String query, int numberOfRecords, int startIndex, String sortKey, boolean reverse) {
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(DocumentKeys.SEARCH_QUERY, query);
        req.put(DocumentKeys.SEARCH_MAX_RECORDS, numberOfRecords);
        req.put(DocumentKeys.SEARCH_START_INDEX, startIndex);
        req.put(DocumentKeys.SEARCH_SORTKEY, sortKey);
        req.put(DocumentKeys.SEARCH_REVERSE, reverse);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);        

        try {
            res = getSearchClient().search(req);
            retXML = res.toXML();
        } catch (IOException e) {
            log.error("Error executing query: '" + query + "', " +
                    numberOfRecords + ", " +
                    startIndex + ", " +
                    sortKey + ", " +
                    reverse +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        }

        return retXML;
    }

    /**
     * A simple way to query the facet browser.
     * @param query The query to perform.
     * @return An XML string containing the facet result or an error description.
     */
    public String simpleFacet(String query) {
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(DocumentKeys.SEARCH_QUERY, query);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);

        try {
            res = getSearchClient().search(req);

            // parse string into dom
            Document dom;
            InputSource in = new InputSource();
            in.setCharacterStream(new StringReader(res.toXML()));
            dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);

            // remove any response not related to FacetResult
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList nl = (NodeList) xp.evaluate("/responsecollection/response", dom, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                NamedNodeMap attr = n.getAttributes();
                Node attr_name = attr.getNamedItem("name");
                if (attr_name != null) {
                    if (!"FacetResult".equals(attr_name.getNodeValue())) {
                        // this is not FacetResult so we remove it
                        n.getParentNode().removeChild(n);
                    }
                }
            }

            // transform dom back into a string
            Transformer tran = TransformerFactory.newInstance().newTransformer();
            tran.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            tran.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter sw = new StringWriter();
            tran.transform(new DOMSource(dom), new StreamResult(sw));

            retXML = sw.toString();
        } catch (IOException e) {
            log.error("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        } catch (TransformerException e) {
            log.error("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        } catch (SAXException e) {
            log.error("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        } catch (XPathExpressionException e) {
            log.error("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        } catch (ParserConfigurationException e) {
            log.error("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            // TODO: return a nicer error xml block
            retXML = "<error>Error performing query</error>";
        }

        return retXML;
    }
}



