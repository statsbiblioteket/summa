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
package dk.statsbiblioteket.summa.web.services;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetKeys;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetResultExternal;
import dk.statsbiblioteket.summa.facetbrowser.api.IndexKeys;
import dk.statsbiblioteket.summa.facetbrowser.api.IndexResponse;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.SearchClient;
import dk.statsbiblioteket.summa.search.api.document.*;
import dk.statsbiblioteket.summa.support.api.*;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.util.xml.DOM;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing methods meant to be exposed as a web service
 *
 * @author Mads Villadsen <mailto:mv@statsbiblioteket.dk>
 * @author Henrik Bitsch Kirk <mailto:hbk@statsbiblioteket.dk>
 */
@WebService
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mv, hbk")
public class SearchWS {
    private Log log;

    static SearchClient searcher;
    static SearchClient suggester;
    static SearchClient didyoumean;
    Configuration conf;

    /**
     * Local XML output factory.
     */
    private static XMLOutputFactory xmlOutputFactory =
                                                 XMLOutputFactory.newInstance();

    /**
     * Did-You-Mean XML namespace.
     */
    public static final String NAMESPACE =
                            "http://statsbiblioteket.dk/summa/2009/SearchError";
    public static final String SEARCH_ERROR_RESPONSE = "searcherror";
    public static final String NAME_TAG = "name";
    public static final String ERROR_TAG = "error";
    public static final String MESSAGE_TAG = "message";
    public static final String VERSION_TAG = "version";
    public static final String VERSION = "1.0";


    public SearchWS() {
        log = LogFactory.getLog(SearchWS.class);
    }

    /**
     * Get a single SearchClient based on the system configuration.
     * @return A SearchClient.
     */
    private synchronized SearchClient getSearchClient() {
        if (searcher == null) {
            try {
                searcher = new SearchClient(getConfiguration()
                                      .getSubConfiguration("summa.web.search"));
            } catch (IOException e) {
                log.error("Failed to load subConfiguration for search.", e);
            }
        }
        return searcher;
    }

    /**
     * Get a single SearchClient for Suggest based on the system configuration.
     * @return A SearchClient to be used for Suggest.
     */
    private synchronized SearchClient getSuggestClient() {
        if (suggester == null) {
            try {
                suggester = new SearchClient(getConfiguration()
                                     .getSubConfiguration("summa.web.suggest"));
            } catch (IOException e) {
                log.error("Failed to load subConfiguration for suggest.", e);
            }
        }
        return suggester;
    }

    /**
     * Get a single DidYouMeanClient for Did-you-Mean service, based on the
     * system configuration.
     * @return A DidYouMeanClient which can be used for Did-You-Mean services.
     */
    private synchronized SearchClient getDidYouMeanClient() {
        if (didyoumean == null) {
            try {
                didyoumean = new SearchClient(getConfiguration()
                                  .getSubConfiguration("summa.web.didyoumean"));
            } catch (IOException e) {
                log.error("Failed to load subConfiguration for didYouMean.", e);
            }
        }
        return didyoumean;
    }

    /**
     * Get the a Configuration object. First trying to load the configuration
     * from the location specified in the JNDI property
     * java:comp/env/confLocation, and if that fails, then the System
     * Configuration will be returned.
     * @return The Configuration object.
     */
    private Configuration getConfiguration() {
        if (conf == null) {
            InitialContext context;
            try {
                context = new InitialContext();
                String paramValue = (String)
                                   context.lookup("java:comp/env/confLocation");
                log.debug("Trying to load configuration from: " + paramValue);
                conf = Configuration.load(paramValue);
            } catch (NamingException e) {
                log.warn("Failed to lookup env-entry. Trying to load system " 
                         + "configuration.", e);
                conf = Configuration.getSystemConfiguration(true);
            }
        }

        return conf;
    }

    /**
     * Given search query and maximum number of result, this method returns a
     * XML block containing the suggestions given by the did-you-mean service.
     * @param query user given search query.
     * @param maxSuggestions maximum number of returned suggestions.
     * @return XML block containing the suggestions given the did-you-mean
     * services.
     */
    @WebMethod
    public String didYouMean(String query, int maxSuggestions) {
        log.trace("didYouMean('" + query + "', " + maxSuggestions + ")");
        long startTime = System.currentTimeMillis();
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(DidYouMeanKeys.SEARCH_QUERY, query);
        req.put(DidYouMeanKeys.SEARCH_MAX_RESULTS, maxSuggestions);

        try {
            res = getDidYouMeanClient().search(req);
            Document dom = DOM.stringToDOM(res.toXML());
            Node subDom = DOM.selectNode(dom,
                    "/responsecollection/response[@name='DidYouMeanResponse']/DidYouMeanResponse");
            retXML = DOM.domToString(subDom);
        } catch (IOException e) {
            log.warn("Error executing didYouMean: '" + query + "', " +
                    maxSuggestions +
                    ". Error was: ", e);
            String mes = "Error performing didYouMean query";
            retXML = getErrorXML(DidYouMeanResponse.DIDYOUMEANRESPONSE, mes, e);
        } catch (TransformerException e) {
            log.warn("Error executing didYouMean: '" + query + "', " +
                    maxSuggestions +
                    ". Error was: ", e);
            String mes = "Error performing didYouMean query";
            retXML = getErrorXML(DidYouMeanResponse.DIDYOUMEANRESPONSE, mes, e);
        }

        log.debug("didYouMean('" + query + "', " + maxSuggestions
                  + ") finished in " + (System.currentTimeMillis() - startTime)
                  + "ms");
        return retXML;
    }

    /**
     * Given a prefix this method returns other queries that start with the same
     * prefix.
     * @param prefix The prefix that the returned queries must start with.
     * @param maxSuggestions The maximum number of queries to be returned.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String getSuggestions(String prefix, int maxSuggestions) {
        log.trace("getSuggestion('" + prefix + "', " + maxSuggestions + ")");
        long startTime = System.currentTimeMillis();
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(SuggestKeys.SEARCH_PREFIX, prefix);
        req.put(SuggestKeys.SEARCH_MAX_RESULTS, maxSuggestions);

        try {
            res = getSuggestClient().search(req);
            Document dom = DOM.stringToDOM(res.toXML());
            Node subDom = DOM.selectNode(dom,
                    "/responsecollection/response[@name='SuggestResponse']/QueryResponse/suggestions");
            retXML = DOM.domToString(subDom);
        } catch (IOException e) {
            log.warn("Error executing getSuggestions: '" + prefix + "', " +
                    maxSuggestions +
                    ". Error was: ", e);
            String mes = "Error performing getSuggestions";
            retXML = getErrorXML(SuggestResponse.NAME, mes, e);
        } catch (TransformerException e) {
            log.warn("Error executing getSuggestions: '" + prefix + "', " +
                    maxSuggestions +
                    ". Error was: ", e);
            String mes = "Error performing getSuggestions";
            retXML = getErrorXML(SuggestResponse.NAME, mes, e);
        }

        log.debug("getSuggestion('" + prefix + "', " + maxSuggestions
                  + ") finished in " + (System.currentTimeMillis() - startTime)
                  + "ms");
        return retXML;
    }

    /**
     * Returns any suggestions added or updated within the last
     * {@code ageSeconds} returning a maximum of {@code maxSuggestions}
     * results.
     * @param ageSeconds number of seconds to look back
     * @param maxSuggestions The maximum number of queries to be returned.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String getRecentSuggestions(int ageSeconds, int maxSuggestions) {
        log.trace("getRecentSuggestions(" + ageSeconds + "s, "
                  + maxSuggestions + ")");
        long startTime = System.currentTimeMillis();
        String retXML;
        ResponseCollection res;

        Request req = new Request();
        req.put(SuggestKeys.SEARCH_RECENT, ageSeconds);
        req.put(SuggestKeys.SEARCH_MAX_RESULTS, maxSuggestions);

        try {
            res = getSuggestClient().search(req);
            Document dom = DOM.stringToDOM(res.toXML());
            Node subDom = DOM.selectNode(dom,
                    "/responsecollection/response[@name='SuggestResponse']/QueryResponse/suggestions");
            retXML = DOM.domToString(subDom);
        } catch (IOException e) {
            log.warn("Error executing getRecentSuggestions: "
                     + ageSeconds + "s, " + maxSuggestions + ". Error was: ", e);
            String mes = "Error performing getRecentSuggestions";
            retXML = getErrorXML(SuggestResponse.NAME, mes, e);
        } catch (TransformerException e) {
            log.warn("Error executing getRecentSuggestions: "
                     + ageSeconds + "s, " + maxSuggestions
                     + ". Error was: ", e);
            String mes = "Error performing getRecentSuggestions";
            retXML = getErrorXML(SuggestResponse.NAME, mes, e);
        }

        log.debug("getRecentSuggestions(" + ageSeconds + "s, " + maxSuggestions
                  + ") finished in " + (System.currentTimeMillis() - startTime)
                  + "ms");
        return retXML;
    }

    /**
     * Commits a query to the Suggestion database. This enables this query to be
     * returned in the result from getSuggestions. It is recommended that only
     * query that the user actually enters are committed - ie. it might not be
     * a good idea to commit queries that come from the user clicking facets,
     * etc.
     * @param query the query to commit to the database.
     * @param hitCount the number of hits that resulted from the query. If this
     * is 0 then the query is removed from the Suggestion database.
     */
    @WebMethod
    public void commitQuery(String query, long hitCount) {
        log.debug("commitQuery('" + query + "', " + hitCount + ")");

        Request req = new Request();
        req.put(SuggestKeys.SEARCH_UPDATE_QUERY, cleanQuery(query));
        req.put(SuggestKeys.SEARCH_UPDATE_HITCOUNT, hitCount);

        try {
            getSuggestClient().search(req);
        } catch (IOException e) {
            log.warn("Error committing query '" + cleanQuery(query)
                     + "' with hitCount '" + hitCount + "'");
        }
    }


    /**
     * Returns a given field from the search index for a specific recordId. This
     * could for instance be used to get the shortformat for a specific record.
     * @param id The recordId to look up.
     * @param fieldName The name of the field to return.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String getField(String id, String fieldName) {
        log.trace("getField('" + id + "', '" + fieldName + "')");
        long startTime = System.currentTimeMillis();
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(DocumentKeys.SEARCH_QUERY, "recordID:\"" + id + "\"");
        req.put(DocumentKeys.SEARCH_MAX_RECORDS, 1);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);

        try {
            res = getSearchClient().search(req);
            Document dom = DOM.stringToDOM(res.toXML());
            Node subDom = DOM.selectNode(dom,
                    "/responsecollection/response/documentresult/record/"
                    + "field[@name='" + fieldName + "']");
            retXML = DOM.domToString(subDom);
            if (retXML == null || "".equals(retXML) && log.isDebugEnabled()) {
                log.debug("getField(" + id + ", " + fieldName + ") did not give"
                          + " any return-XML. The reply from the core search "
                          + "was\n" + res.toXML());
            }
        } catch (IOException e) {
            log.warn("Error querying for id: '" + id + "'." +
                    "Error was: ", e);
            String mes = "Error performing query";
            retXML = getErrorXML(DocumentResponse.NAME, mes, e);
        } catch (TransformerException e) {
            log.warn("Error querying for id: '" + id + "'." +
                    "Error was: ", e);
            String mes = "Error performing query";
            retXML = getErrorXML(DocumentResponse.NAME, mes, e);
        }

        log.trace("getField('" + id + "', '" + fieldName
                  + "') finished in " + (System.currentTimeMillis() - startTime)
                  + "ms");
        return retXML;
    }


    /**
     * Performs a lookup in the given field for the given term and returns a
     * list starting at the term position + delta with the given length.
     * </p><p>
     * Example: {@code indexLookup("myField", "d", -2, 5)} on a field that has
     *          the values a, b, c, d, e, f, g, h will give the result
     *          b, c, d, e, f.
     * </p><p>
     * If the term cannot be located, the nearest matching position will be
     * used instead.
     * @param field  the field to perform a lookup on. Currently this must be
     *               the name of a facet.
     * @param term   the term to search for. This can be multiple words.
     * @param delta  the offset relative to the term position.
     * @param length the maximum number of terms to return.
     * @return an XML string containing the result or an error description.
     */
    @WebMethod
    public String indexLookup(String field, String term,
                              int delta, int length) {
        //noinspection DuplicateStringLiteralInspection
        String call = "indexLookup(" + field + ":" + term + ", " + delta + ", "
                      + length + ")";
        log.trace(call);
        long startTime = System.currentTimeMillis();
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(IndexKeys.SEARCH_INDEX_FIELD, field);
        req.put(IndexKeys.SEARCH_INDEX_TERM, term);
        req.put(IndexKeys.SEARCH_INDEX_DELTA, delta);
        req.put(IndexKeys.SEARCH_INDEX_LENGTH, length);

        try {
            res = getSearchClient().search(req);
            retXML = res.toXML();
        } catch (IOException e) {
            log.warn("Error executing " + call + ": ", e);
            String mes = "Error performing " + call + ": " + e.getMessage();
            retXML = getErrorXML(IndexResponse.NAME, mes, e);
        }
        //noinspection DuplicateStringLiteralInspection
        log.trace(call + " finished in "
                  + (System.currentTimeMillis() - startTime + "ms"));
        return retXML;
    }

    /**
     * Gives a search result of records that "are similar to" a given record. 
     * @param id The recordID of the record that should be used as base for the
     * MoreLikeThis query.
     * @param numberOfRecords The maximum number of records to return.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String getMoreLikeThis(String id, int numberOfRecords) {
        log.trace("getMoreLikeThis('" + id + "', " + numberOfRecords + ")");
        long startTime = System.currentTimeMillis();
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(LuceneKeys.SEARCH_MORELIKETHIS_RECORDID, id);
        req.put(DocumentKeys.SEARCH_MAX_RECORDS, numberOfRecords);
        req.put(DocumentKeys.SEARCH_SORTKEY, DocumentKeys.SORT_ON_SCORE);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);

        try {
            res = getSearchClient().search(req);
            retXML = res.toXML();
        } catch (IOException e) {
            log.warn("Error executing morelikethis: '" + id + "', " +
                    numberOfRecords +
                    ". Error was: ", e);
            String mes = "Error performing morelikethis";
            retXML = getErrorXML(DocumentResponse.NAME, mes, e);
        }

        log.debug("getMoreLikeThis('" + id + "', " + numberOfRecords
                  + ") finished in " + (System.currentTimeMillis() - startTime)
                  + "ms");
        return retXML;
    }

    /**
     * A simple way to query the index returning results sorted by relevance.
     * The same as calling simpleSearchSorted while specifying a normal sort on
     * relevancy.
     * @param query The query to perform.
     * @param numberOfRecords The maximum number of records to return.
     * @param startIndex Where to start returning records from (used to
     * implement paging).
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String simpleSearch(String query, int numberOfRecords,
                               int startIndex) {
        return simpleSearchSorted(query, numberOfRecords, startIndex,
                                             DocumentKeys.SORT_ON_SCORE, false);
    }

    public static final Pattern PROCESSING_OPTIONS =
            Pattern.compile("\\<\\:(.*)\\:\\>(.*)");
    /**
     * A simple way to query the index wile being able to specify which field to
     * sort by and whether the sorting should be reversed.
     * </p><p>
     * Processing-options can be specified at the start of the query prepended
     * by '<:' and appended by ':>', divided by spaces. As of now, the only
     * possible option is 'explain', which turns on explanation of the result.
     * This increases processing time, so do not turn it on as default.
     * example: "<:explain:>foo" will explain why the documents matching foo
     * was selected.
     * @param query The query to perform.
     * @param numberOfRecords The maximum number of records to return.
     * @param startIndex Where to start returning records from (used to
     * implement paging).
     * @param sortKey The field to sort by.
     * @param reverse Whether or not the sort should be reversed.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String simpleSearchSorted(String query, int numberOfRecords,
                                     int startIndex, String sortKey,
                                     boolean reverse) {
        if (log.isTraceEnabled()) {
            log.debug(String.format(
                    "simpleSearchSorted(query='%s', numberOfRecords=%d, "
                    + "startIndex=%d, sortKey='%s', reverse=%b) entered",
                    query, numberOfRecords, startIndex, sortKey, reverse));
        }
        return filterSearchSorted(null, query, numberOfRecords, startIndex,
                                                              sortKey, reverse);
    }

    /**
     * A simple way to query the index wile being able to specify which field to
     * sort by and whether the sorting should be reversed.
     * </p><p>
     * Processing-options can be specified at the start of the query prepended
     * by '<:' and appended by ':>', divided by spaces. As of now, the only
     * possible option is 'explain', which turns on explanation of the result.
     * This increases processing time, so do not turn it on as default.
     * example: "<:explain:>foo" will explain why the documents matching foo
     * was selected.
     * @param filter the filter to use before querying.
     * @param query The query to perform.
     * @param numberOfRecords The maximum number of records to return.
     * @param startIndex Where to start returning records from (used to
     * implement paging).
     * @param sortKey The field to sort by.
     * @param reverse Whether or not the sort should be reversed.
     * @return An XML string containing the result or an error description.
     */
    @WebMethod
    public String filterSearchSorted(
            String filter, String query, int numberOfRecords, int startIndex,
            String sortKey, boolean reverse) {
        final String PARAMS = "filter='%s', query='%s', numberOfRecords=%d, "
                              + "startIndex=%d, sortKey='%s', reverse=%b";
        if (log.isDebugEnabled()) {
            //noinspection DuplicateStringLiteralInspection
            log.debug(String.format("filterSearchSorted(" + PARAMS + ") called",
                                    filter, query, numberOfRecords, startIndex,
                                    sortKey, reverse));
        }
        long startTime = System.currentTimeMillis();

        String retXML;
        ResponseCollection res;
        Request req = new Request();
        // Handle processing options
        log.trace("Extracting options (aka explain)");
        try {
            String[] options = extractOptions(query);
            if (options != null) {
                log.debug("simpleSearchSorted received "
                          + options.length + " options");
                for (String option: options) {
                    if ("explain".equals(option)) {
                        log.debug("Turning on explain for query '"
                                  + query + "'");
                        req.put(DocumentKeys.SEARCH_EXPLAIN, true);
                        continue;
                    }
                    log.debug(String.format(
                            "Got unknown processing option '%s' in query '%s'",
                            option, query));
                }
            } else {
                log.trace(String.format("No processing options for query '%s'",
                                        query));
            }
        } catch (Exception e) {
            log.warn(String.format(
                    "Exception while extracting processing options from query "
                    + "'%s'. Options are skipped and the query left unchanged",
                    query));
        }
        log.trace("Cleaning query");
        query = cleanQuery(query);

        if (filter != null) {
            log.trace("Assigning filter '" + filter + "'");
            req.put(DocumentKeys.SEARCH_FILTER , filter);
        }
        if (query != null) {
            log.trace("Assigning query '" + query + "'");
            req.put(DocumentKeys.SEARCH_QUERY, query);
        }
        req.put(DocumentKeys.SEARCH_MAX_RECORDS, numberOfRecords);
        req.put(DocumentKeys.SEARCH_START_INDEX, startIndex);
        if (sortKey != null) {
            req.put(DocumentKeys.SEARCH_SORTKEY, sortKey);
        }
        req.put(DocumentKeys.SEARCH_REVERSE, reverse);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, false);
        log.trace("Produced search request, calling search");
        try {
            res = getSearchClient().search(req);
            log.trace("Got result, converting to XML");
            retXML = res.toXML();
        } catch (IOException e) {
            log.warn(String.format(
                    "Error executing query '" + PARAMS + "'. Error was: %s",
                    filter, query, numberOfRecords, startIndex,
                    sortKey, reverse, e.getMessage()), e);
            String mes = String.format("Error performing query: %s", 
                                                                e.getMessage());
            retXML = getErrorXML(DocumentResponse.NAME, mes, e);
        }

        log.debug(String.format(
                "simpleSearchSorted(" + PARAMS + ") finished in %s ms",
                filter, query, numberOfRecords, startIndex, sortKey, reverse,
                System.currentTimeMillis() - startTime));
        return retXML;
    }

    private String cleanQuery(String queryString) {
        if (queryString == null) {
            return null;
        }
        Matcher optionsMatcher = PROCESSING_OPTIONS.matcher(queryString);
        if (optionsMatcher.matches()) {
            return optionsMatcher.group(2);
        }
        return queryString;
    }

    private String[] extractOptions(String query) {
        if (query == null) {
            return null;
        }
        Matcher optionsMatcher = PROCESSING_OPTIONS.matcher(query);
        if (optionsMatcher.matches()) {
            return optionsMatcher.group(1).split(" +");
        }
        return null;
    }

    /**
     * A simple way to query the facet browser.
     * @param query The query to perform.
     * @return An XML string containing the facet result or an error
     * description.
     */
    @WebMethod
    public String simpleFacet(String query) {
        log.trace("simpleFacet('" + query + "')");
        long startTime = System.currentTimeMillis();

        String retXML = advancedFacet(query, null);

        log.debug("simpleFacet('" + query + "') finished in "
                  + (System.currentTimeMillis() - startTime) + "ms");
        return retXML;
    }

    /**
     * A more advanced way to query the facet browser giving the caller control
     * over the individual facets and tags.
     * @param query The query to perform.
     * @param facetKeys A comma-separeted list with the names of the wanted
     * Facets.
     * Optionally, the maximum Tag-count for a given Facet can be specified in
     * parenthesis after the name.
     *
     * Example: "Title, Author (5), City (10), Year".
     *
     * If no maximum Tag-count is specified, the number is taken from the
     * defaults.
     * Optionally, the sort-type for a given Facet can be specified in the same
     * parenthesis. Valid values are POPULARITY and ALPHA. If no sort-type is
     * specified, the number is taken from the defaults.
     *
     * Example: "Title (ALPHA), Author (5 POPULARITY), City"
     *
     * This is all optional. If no facets are specified, the default facets are
     * requested.
     * @return An XML string containing the facet result or an error
     * description.
     */
    @WebMethod
    public String advancedFacet(String query, String facetKeys) {
        log.trace("advancedFacet('" + query + "', '" + facetKeys + "')");
        long startTime = System.currentTimeMillis();

        query = cleanQuery(query);
        String retXML;

        ResponseCollection res;

        Request req = new Request();
        req.put(DocumentKeys.SEARCH_QUERY, query);
        req.put(DocumentKeys.SEARCH_COLLECT_DOCIDS, true);
        if (facetKeys != null && !"".equals(facetKeys)) {
            req.put(FacetKeys.SEARCH_FACET_FACETS, facetKeys);
        }

        try {
            res = getSearchClient().search(req);

            // parse string into dom
            Document dom = DOM.stringToDOM(res.toXML());

            // remove any response not related to FacetResult
            NodeList nl =
                        DOM.selectNodeList(dom, "/responsecollection/response");
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
            retXML = DOM.domToString(dom);
        } catch (IOException e) {
            log.warn("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            String mes = "Error performing query";
            retXML = getErrorXML(FacetResultExternal.NAME, mes, e);
        } catch (TransformerException e) {
            log.warn("Error faceting query: '" + query + "'" +
                    ". Error was: ", e);
            String mes = "Error performing query";
            retXML = getErrorXML(FacetResultExternal.NAME, mes, e);
        }

        log.debug("advancedFacet('" + query + "', '" + facetKeys
                + "') finished in " + (System.currentTimeMillis() - startTime)
                + "ms");
        return retXML;
    }

    private String getErrorXML(String responseName, String message,
                                                          Exception exception) {
        StringWriter sw = new StringWriter(2000);
        XMLStreamWriter writer;
        try {
            writer = xmlOutputFactory.createXMLStreamWriter(sw);
        } catch (XMLStreamException e) {
            throw new RuntimeException(
                    "Unable to create XMLStreamWriter from factory", e);
        }
        // Write XML document.
        try {
            writer.setDefaultNamespace(NAMESPACE);
            writer.writeStartElement(SEARCH_ERROR_RESPONSE);
            writer.writeDefaultNamespace(NAMESPACE);
            writer.writeAttribute(NAME_TAG, responseName);
            writer.writeAttribute(VERSION_TAG, VERSION);
            // message
            writer.writeStartElement(MESSAGE_TAG);
            writer.writeCharacters(message);
            writer.writeEndElement();
            writer.writeCharacters("\n");
            // stack trace
            writer.writeStartElement(ERROR_TAG);
            writer.writeCharacters(exception.toString());
            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeEndDocument();

            writer.flush(); // Just to make sure
        } catch (XMLStreamException e) {
            throw new RuntimeException(
                    "Got XMLStreamException while constructing XML from "
                    + "search response", e);
        }
        return sw.toString();
    }
}




