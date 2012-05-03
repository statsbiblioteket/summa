package dk.statsbiblioteket.summa.storage.api;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.storage.database.derby.DerbyStorage;
import dk.statsbiblioteket.summa.storage.database.DatabaseStorage;
import dk.statsbiblioteket.util.Files;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;

import junit.framework.TestCase;

/**
 *
 */
public class StorageTest extends TestCase {

    Storage storage;

    static String testDBLocation = "test_db";
    static String testBase1 = "foobar";
    static String testId1 = "quiz1";
    static String testId2 = "quiz2";
    static String testId3 = "quiz3";
    static int storageCounter = 0;
    static byte[] testContent1 = new byte[] {'s', 'u', 'm', 'm', 'a'};

    public static Configuration createConf () throws Exception {

        Configuration conf = Configuration.newMemoryBased(
                StorageFactory.CONF_STORAGE,
                DerbyStorage.class,
                DatabaseStorage.CONF_LOCATION + (storageCounter++),
                testDBLocation,
                DatabaseStorage.CONF_FORCENEW,
                true
        );

        return conf;
    }

    public void setUp () throws Exception {
        if (new File(testDBLocation + storageCounter).exists()) {
            Files.delete (testDBLocation + storageCounter);
        }

        storage = StorageFactory.createStorage(createConf());
    }

    public void tearDown () throws Exception {
        storage.close();
    }

    public void testGetEmpty () throws Exception {
        List<Record> recs = storage.getRecords(new ArrayList<String>(), 0);
        assertEquals(0, recs.size());
    }

    public void testGetNonExisting () throws Exception {
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), 0);
        assertEquals(0, recs.size());
    }

    public void testClearEmptyBase () throws Exception {
        storage.clearBase (testBase1);
        assertBaseEmpty (testBase1);
    }

    public void assertBaseEmpty (String base) throws Exception {
        Iterator<Record> iter = storage.getRecordsFromBase(base);
        long counter = 0;
        while (iter.hasNext()) {
            Record r = iter.next();
            if (!r.isDeleted()) {
                counter++;
            }
        }

        if (counter != 0) {
            fail ("Base '" + base + "' should be empty, but found " + counter
                  + " records");
        }
    }

    public void testIteration() throws Exception {
        storage.clearBase (testBase1);
        storage.flush(new Record(testId1, testBase1, testContent1));
        storage.flush(new Record(testId2, testBase1, testContent1));
        assertBaseCount(testBase1, 2);
    }

    public void assertBaseCount (String base, long expected) throws Exception {
        Iterator<Record> iter = storage.getRecordsFromBase(base);
        long actual = 0;
        while (iter.hasNext()) {
            iter.next();
            actual++;
        }

        if (actual != expected) {
            fail("Base '" + base + "' should contain " + expected
                 + " records, but found " + actual);
        }
    }

    public void testAddOne () throws Exception {
        Record rec = new Record (testId1, testBase1, testContent1);
        storage.flush (rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), 0);

        assertEquals(1, recs.size());
        assertEquals(rec.getContentAsUTF8(), recs.get(0).getContentAsUTF8());
        assertEquals(rec.getId(), recs.get(0).getId());
    }

    public void testClearOne () throws Exception {
        testAddOne();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    public void testAddTwo () throws Exception {
        Record rec1 = new Record (testId1, testBase1, testContent1);
        Record rec2 = new Record (testId2, testBase1, testContent1);

        storage.flushAll (Arrays.asList(rec1, rec2));

        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               0);

        assertEquals(2, recs.size());
        assertEquals(rec1.getContentAsUTF8(), recs.get(0).getContentAsUTF8());
        assertEquals(rec1.getId(), recs.get(0).getId());

        assertEquals(rec2.getContentAsUTF8(), recs.get(1).getContentAsUTF8());
        assertEquals(rec2.getId(), recs.get(1).getId());
    }

    public void testClearTwo () throws Exception {
        testAddTwo();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    public void testExpandShallowRecord () throws Exception {
        testAddOne();

        List<Record> recs = storage.getRecords(Arrays.asList(testId1),
                                               1);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testFullExpandShallowRecord () throws Exception {
        testAddOne();

        List<Record> recs = storage.getRecords(Arrays.asList(testId1),
                                               -1);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testAddLinkedRecords () throws Exception {
        Record recP = new Record (testId1, testBase1, testContent1);
        Record recC1 = new Record (testId2, testBase1, testContent1);
        Record recC2 = new Record (testId3, testBase1, testContent1);
        recP.setChildren(Arrays.asList(recC1, recC2));
        recC1.setChildren(Arrays.asList(recC2));
        recC1.setParentId(recP.getId());
        recC2.setParentId(recC1.getId());

        storage.flushAll (Arrays.asList(recP, recC1, recC2));

        /* Fetch without expansion, we test that elewhere */
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               0);

        assertEquals(2, recs.size());

        assertEquals(recP.getContentAsUTF8(), recs.get(0).getContentAsUTF8());
        assertEquals(recP.getId(), recs.get(0).getId());
        assertEquals(recs.get(0).getChildren(), null);
        assertEquals(recs.get(0).getChildIds(), recP.getChildIds());

        assertEquals(recC1.getContentAsUTF8(), recs.get(1).getContentAsUTF8());
        assertEquals(recC1.getId(), recs.get(1).getId());
        assertEquals(recC1.getParentId(), recs.get(1).getParentId());
    }

    public void testExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               1);

        assertEquals(2, recs.size());

        /* Check that the first record holds a child relation to the next */
        assertEquals(recs.get(0).getChildren(), Arrays.asList(recs.get(1)));
    }

    public void testRecursiveExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2, testId3),
                                               -1);

        assertEquals(3, recs.size());

        /* Check that the first record holds child relations to recC1, and recC2 */
        assertEquals(recs.get(0).getChildren(),
                     Arrays.asList(recs.get(1), recs.get(2)));

        /* Check that recC1 has child recC2 */
        assertEquals(recs.get(1).getChildren(),
                     Arrays.asList(recs.get(2)));
    }

    /*
    TODO
     - test deep child expansion
     - test mtime/ctime and repeated adding of same rec
     */


}