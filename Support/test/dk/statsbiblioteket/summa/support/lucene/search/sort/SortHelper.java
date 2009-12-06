/**
 * Created: te 06-12-2009 11:00:06
 * CVS:     $Id$
 */
package dk.statsbiblioteket.summa.support.lucene.search.sort;

import dk.statsbiblioteket.util.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.queryParser.QueryParser;

/**
 * Creates test-indexes usable for sort-testing.
 */
public class SortHelper {

    public static final String[] BASIC_TERMS = {
            "b", "a", "c", "d", "e"};
    public static final String[] TRICKY_TERMS = {
            "z", null, "abe", "a be", "a aa", "ægir", "Ægir", "Ødis",
            "foo", null, "bar", "moo moo", null};

    public static final String SORT_FIELD = "sortfield";

    private static List<File> indexes = new ArrayList<File>(10);

    /**
     * Deletes created indexes.
     */
    public static void tearDown() {
        for (File index: indexes) {
            try {
                Files.delete(index);
            } catch (IOException e) {
                System.err.println("Could not delete '" + index + "'\n");
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates an index of length terms.length, containing dummy-documents with
     * the fields sortfield:term, parity:even|odd, number:docID and all:all.
     * @param terms the terms to add to the field sortfield. Note that null is
     *              a valid value.
     * @return the root of an index with the given terms.
     * @throws java.io.IOException if the index could not be created.
     */
    public static File createIndex(String[] terms) throws IOException {
        File root = new File(System.getProperty("java.io.tmpdir"));
        Directory dir = FSDirectory.getDirectory(root);
        IndexWriter writer = new IndexWriter(
                dir, new StandardAnalyzer(), true,
                new IndexWriter.MaxFieldLength(10000));
        int counter = 0;
        for (String term: terms) {
            Document doc = new Document();
            doc.add(new Field("all", "all",
                              Field.Store.NO, Field.Index.NOT_ANALYZED));
            doc.add(new Field("number", Integer.toString(counter),
                              Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field("parity", counter % 2 == 0 ? "even" : "odd",
                              Field.Store.YES, Field.Index.ANALYZED));
            if (term != null) {
                doc.add(new Field(SORT_FIELD, term,
                                  Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
            writer.addDocument(doc);
            counter++;
        }
        writer.close();
        indexes.add(root);
        return root;
    }

    /**
     * Creates an index with the given terms with {@link #createIndex} and
     * performs a search for the given query, returning the contents of the
     * sortfields.
     * @param query the search query.
     * @param sortTerms the terms to add to the field sortfield. Note that null
     *                  is a valid value.
     * @param sortFactory constructs the Sort to use.
     * @return a list of terms from sortfields. If not defined for a given
     *         Document, null is inserted into the list.
     * @throws Exception if an error occured.
     */
    public static List<String> getSortResult(String query,
            String[] sortTerms, SortFactory sortFactory) throws Exception {
        File root = createIndex(sortTerms);
        IndexSearcher searcher = new IndexSearcher(root.toString());
        QueryParser qp = new QueryParser("all", new StandardAnalyzer());
        TopDocs result = searcher.search(
                qp.parse("all:all"), null, 10000,
                sortFactory.getSort(searcher.getIndexReader()));
        List<String> sortfields =
                new ArrayList<String>(result.scoreDocs.length);
        for (ScoreDoc sd: result.scoreDocs) {
            Field sortField = searcher.doc(sd.doc).getField(SORT_FIELD);
            sortfields.add(sortField == null ? null : sortField.stringValue());
        }
        searcher.close();
        return sortfields;
    }

    public abstract static class SortFactory {
        abstract Sort getSort(IndexReader reader) throws IOException;
    }

    /**
     * Sorts the given terms in natural order, where null is allowed. null is
     * first.
     * @param terms the terms to sort.
     */
    public static void nullSort(String[] terms) {
        Arrays.sort(terms, new Comparator<String>() {
            public int compare(String o1, String o2) {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 != null && o2 != null) {
                    return o1.compareTo(o2);
                }
                if (o1 != null) {
                    return 1;
                }
                return -1;
            }
        });
    }
}
