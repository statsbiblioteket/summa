package dk.statsbiblioteket.summa.storage.api;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.storage.database.DatabaseStorage;
import dk.statsbiblioteket.summa.storage.database.derby.DerbyStorage;
import dk.statsbiblioteket.summa.storage.database.postgres.PostgresStorage;
import dk.statsbiblioteket.summa.storage.database.h2.H2Storage;
import dk.statsbiblioteket.util.Files;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 */
public class StorageTest extends TestCase {
    private static Log log = LogFactory.getLog(StorageTest.class);

    Storage storage;

    static String testDBRoot = "test_db";
    static String dbPrefix = "db";
    static String testBase1 = "foobar";
    static String testBase2 = "frobnibar";
    static String testId1 = "testId1";
    static String testId2 = "testId2";
    static String testId3 = "testId3";
    static String testId4 = "testId4";
    static int storageCounter = 0;
    static byte[] testContent1 = new byte[] {'s', 'u', 'm', 'm', 'a'};
    long testStartTime;

    public static Configuration createConf () throws Exception {

        // H2 Config
        Configuration conf = Configuration.newMemoryBased(
                Storage.CONF_CLASS,
                H2Storage.class,
                DatabaseStorage.CONF_LOCATION,
                testDBRoot + File.separator + dbPrefix + (storageCounter++)
        );

        // Postgres Config
        /*Configuration conf = Configuration.newMemoryBased(
                Storage.CONF_CLASS,
                PostgresStorage.class,
                DatabaseStorage.CONF_LOCATION,
                testDBRoot + File.separator + dbPrefix + (storageCounter++),
                DatabaseStorage.CONF_FORCENEW,
                true,
                DatabaseStorage.CONF_DATABASE,
                "summa",
                DatabaseStorage.CONF_USERNAME,
                "${user.name}",
                DatabaseStorage.CONF_PASSWORD,
                "",
                DatabaseStorage.CONF_HOST,
                ""
        );*/

        return conf;
    }

    public void setUp () throws Exception {
        File dbRoot = new File(testDBRoot);

        if (dbRoot.exists()) {
            Files.delete (dbRoot);
        }

        storage = StorageFactory.createStorage(createConf());

        /* We get spurious errors where the connection to the db isn't ready
         * when running the unit tests in batch mode */
        Thread.sleep(200);

        testStartTime = System.currentTimeMillis();
    }

    public void tearDown () throws Exception {
        log.info("Test case tear down commencing");
        
        if (storage instanceof DatabaseStorage) {
            ((DatabaseStorage)storage).destroyDatabase();
        }

        storage.close();
        /* We get spurious errors where the connection to the db isn't ready
         * when running the unit tests in batch mode */
        Thread.sleep(200);
    }

    public void testGetEmpty () throws Exception {
        List<Record> recs = storage.getRecords(new ArrayList<String>(), null);
        assertEquals(0, recs.size());
    }

    public void testGetNonExisting () throws Exception {
        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);
        assertEquals(0, recs.size());
    }

    public void testClearEmptyBase () throws Exception {
        storage.clearBase (testBase1);
        assertBaseEmpty (testBase1);
    }

    public void assertBaseEmpty (String base) throws Exception {
        long iterKey = storage.getRecordsModifiedAfter(0, base, null);
        Iterator<Record> iter = new StorageIterator(storage, iterKey);
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
        long iterKey = storage.getRecordsModifiedAfter(0, base, null);
        Iterator<Record> iter = new StorageIterator(storage, iterKey);
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

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);

        assertTrue(storage.getModificationTime (testBase1) > testStartTime);
        assertTrue(storage.getModificationTime (null) > testStartTime);
    }

    public void testClearOne() throws Exception {
        testAddOne();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    public void testAddOneWithOneChildId() throws Exception {
        Record rec = new Record (testId1, testBase1, testContent1);
        rec.setChildIds(Arrays.asList(testId2));
        storage.flush (rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(Arrays.asList(testId2), recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithTwoChildIds() throws Exception {
        Record rec = new Record (testId1, testBase1, testContent1);
        rec.setChildIds(Arrays.asList(testId2, testId3));
        storage.flush (rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(Arrays.asList(testId2, testId3), recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(null, recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithOneParentId() throws Exception {
        Record rec = new Record (testId1, testBase1, testContent1);
        rec.setParentIds(Arrays.asList(testId2));
        storage.flush (rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertNotNull(recs.get(0).getParentIds());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(Arrays.asList(testId2), recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddOneWithTwoParentIds() throws Exception {
        Record rec = new Record (testId1, testBase1, testContent1);
        rec.setParentIds(Arrays.asList(testId2, testId3));
        storage.flush (rec);

        List<Record> recs = storage.getRecords(Arrays.asList(testId1), null);

        assertEquals(1, recs.size());
        assertEquals(rec, recs.get(0));

        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getParents());
        assertEquals(Arrays.asList(testId2, testId3), recs.get(0).getParentIds());

        assertBaseCount(testBase1, 1);
    }

    public void testAddTwo () throws Exception {
        Record rec1 = new Record(testId1, testBase1, testContent1);
        Record rec2 = new Record(testId2, testBase1, testContent1);

        storage.flushAll (Arrays.asList(rec1, rec2));

        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               null);

        assertEquals(2, recs.size());

        assertEquals(rec1, recs.get(0));
        assertEquals(null, recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getChildren());
        assertEquals(null, recs.get(0).getParentIds());
        assertEquals(null, recs.get(0).getParents());

        assertEquals(rec2, recs.get(1));
        assertEquals(null, recs.get(1).getChildIds());
        assertEquals(null, recs.get(1).getChildren());
        assertEquals(null, recs.get(1).getParentIds());
        assertEquals(null, recs.get(1).getParents());

        assertBaseCount(testBase1, 2);

        assertTrue(storage.getModificationTime (testBase1) > testStartTime);
        assertTrue(storage.getModificationTime (null) > testStartTime);
    }

    public void testAddTwoWithTimestampSort () throws Exception {
        Record rec1 = new Record(testId1, testBase1, testContent1);
        Record rec2 = new Record(testId2, testBase1, testContent1);

        // Commit the records with id sorting reversed with 100ms delay
        storage.flush (rec2);
        Thread.sleep(100);
        storage.flush(rec1);

        long iterKey = storage.getRecordsModifiedAfter(0, testBase1, null);
        Iterator<Record> iter = new StorageIterator(storage, iterKey);

        Record r = iter.next();
        assertEquals(rec2, r); // The first record flushed should be first

        r = iter.next();
        assertEquals(rec1, r); // The last record flushed should be last

        assertFalse("Storage should contain exactly two records",
                    iter.hasNext());
    }

    public void testClearTwo () throws Exception {
        testAddTwo();
        storage.clearBase(testBase1);
        assertBaseEmpty(testBase1);
    }

    public void testExpandShallowRecord () throws Exception {
        testAddOne();

        QueryOptions options = new QueryOptions(null, null, 1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1),
                                               options);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testFullExpandShallowRecord () throws Exception {
        testAddOne();

        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1),
                                               options);
        assertEquals(1, recs.size());

        Record rec = recs.get(0);
        assertEquals(rec.getContentAsUTF8(), rec.getContentAsUTF8());
        assertEquals(rec.getId(), rec.getId());
    }

    public void testAddLinkedRecords () throws Exception {
        Record recP = new Record (testId1, testBase1, testContent1);
        Record recC1 = new Record (testId2, testBase1, testContent1);
        Record recC2 = new Record (testId3, testBase1, testContent1);

        recP.setChildIds(Arrays.asList(recC1.getId(), recC2.getId()));

        recC1.setChildIds(Arrays.asList(recC2.getId()));
        recC1.setParentIds(Arrays.asList(recP.getId()));

        recC2.setParentIds(Arrays.asList(recC1.getId()));

        storage.flushAll (Arrays.asList(recP, recC1, recC2));

        /* Fetch without expansion, we test that elewhere */
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               null);

        assertEquals(2, recs.size());

        System.out.println ("ORIG:\n" + recP.toString(true));
        System.out.println ("GOT :\n" + recs.get(0).toString(true));

        /* We can't compare the records directly because recP has the child
         * records nested, while the retrieved records only has the ids */
        assertEquals(recP.getId(), recs.get(0).getId());
        assertEquals(recP.getBase(), recs.get(0).getBase());
        assertEquals(recP.getContentAsUTF8(), recs.get(0).getContentAsUTF8());

        /* We should have the ids of the children, but they should not be
         * expanded */
        assertEquals(recP.getChildIds(), recs.get(0).getChildIds());
        assertEquals(null, recs.get(0).getChildren());


        assertEquals(recC1.getContentAsUTF8(), recs.get(1).getContentAsUTF8());
        assertEquals(recC1.getId(), recs.get(1).getId());
        assertEquals(recC1.getBase(), recs.get(1).getBase());
        assertEquals(recC1.getParentIds(), recs.get(1).getParentIds());

        assertBaseCount(testBase1, 3);
    }

    public void testExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        QueryOptions options = new QueryOptions(null, null, 1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2),
                                               options);

        assertEquals(2, recs.size());

        /* Check that the first record holds a child relation to the next */
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(testId2, recs.get(0).getChildren().get(0).getId());

        /* testId3 is a child of testId2 and should be expanded */
        assertEquals(1, recs.get(1).getChildren().size());
        assertEquals(1, recs.get(1).getChildIds().size());
        assertEquals(testId3, recs.get(1).getChildIds().get(0));
        assertEquals(testId3, recs.get(1).getChildren().get(0).getId());

    }

    public void testRecursiveExpandLinkedRecord () throws Exception {
        testAddLinkedRecords();

        /* Fetch records expanding immediate children only */
        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2, testId3),
                                               options);

        assertEquals(3, recs.size());

        /* Check that the first record holds child relations to recC1, and recC2 */
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(recs.get(1), recs.get(0).getChildren().get(0));
        assertEquals(recs.get(2), recs.get(0).getChildren().get(1));        

        /* Check that recC1 has child recC2 */
        assertEquals(recs.get(1).getChildren(),
                     Arrays.asList(recs.get(2)));
    }

    /**
     * Test that ingesting one record also ingests any child records on it
     */
    public void testAddNestedRecords () throws Exception {
        Record recP = new Record (testId1, testBase1, testContent1);
        Record recC1 = new Record (testId2, testBase1, testContent1);
        Record recC2 = new Record (testId3, testBase1, testContent1);
        Record recCC1 = new Record (testId4, testBase1, testContent1);

        /* We need to explicitely set all relations here to make the assertions
         * work*/

        recP.setChildren(Arrays.asList(recC1, recC2));

        recC1.setParents (Arrays.asList(recP));
        recC1.setChildren(Arrays.asList(recCC1));
        recC1.setIndexable(false);

        recC2.setParents(Arrays.asList(recP));
        recC2.setIndexable(false);

        recCC1.setParents(Arrays.asList(recC1));
        recCC1.setIndexable(false);

        /* The child records should be implicitly flushed as well */
        storage.flushAll (Arrays.asList(recP));

        QueryOptions options = new QueryOptions(null, null, -1, 0);
        List<Record> recs = storage.getRecords(Arrays.asList(testId1, testId2,
                                                             testId3, testId4),
                                               options);

        assertEquals(4, recs.size());

        assertEquals(recP, recs.get(0));
        assertEquals(recC1, recs.get(1));
        assertEquals(recC2, recs.get(2));
        assertEquals(recCC1, recs.get(3));

        assertNotNull(recs.get(0).getChildren());
        assertNotNull(recs.get(0).getChildIds());
        assertEquals(2, recs.get(0).getChildren().size());
        assertEquals(2, recs.get(0).getChildIds().size());

        assertEquals(recs.get(0).getChildren().get(0), recC1);
        assertEquals(recs.get(0).getChildren().get(1), recC2);

        assertNotNull(recs.get(1).getChildren());
        assertNotNull(recs.get(1).getChildIds());
        assertEquals(1, recs.get(1).getChildren().size());
        assertEquals(1, recs.get(1).getChildIds().size());
        assertEquals(recCC1, recs.get(1).getChildren().get(0));

        assertNull(recs.get(2).getChildren());
        assertNull(recs.get(2).getChildIds());

        assertNull(recs.get(3).getChildren());
        assertNull(recs.get(3).getChildIds());

        assertBaseCount(testBase1, 4);
    }

    /**
     * Test that ctime is preserved but mtime is updated when flushing the same
     * tecord two times
     */
    public void testTimestampUpdates () throws Exception {
        Record r1 = new Record (testId1, testBase1, testContent1);

        long beforeFlushTime = System.currentTimeMillis();
        storage.flush(r1);
        long afterFlushTime = System.currentTimeMillis();

        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);

        assertTrue(beforeFlushTime <= r2.getModificationTime());
        assertTrue(beforeFlushTime <= r2.getCreationTime());
        assertTrue(afterFlushTime >= r2.getModificationTime());
        assertTrue(afterFlushTime >= r2.getCreationTime());

        assertEquals(r1, r2);
        long ctime = r2.getCreationTime();
        long mtime = r2.getModificationTime();

        assertEquals("For new records mtime == ctime",
                     ctime, mtime);

        storage.flush(r1);
        r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);

        assertEquals(ctime, r2.getCreationTime());
        assertTrue(mtime < r2.getModificationTime());
    }

    public void testGetMorerecords() throws Exception {
        int WANTED_RECORDS = Math.max(DatabaseStorage.DEFAULT_PAGE_SIZE*3 + 5,
                                      StorageIterator.MAX_QUEUE_SIZE + 5);

        for (int i = 0 ; i < WANTED_RECORDS ; i++) {
            storage.flush(new Record("Foo" + i, testBase1, testContent1));
        }

        StorageIterator records = new StorageIterator(
                storage, storage.getRecordsModifiedAfter(0, testBase1, null));
        int count = 0;
        while (records.hasNext()) {
            records.next();
            count++;
        }
        assertEquals("The number of received Records should match (and be "
                     + WANTED_RECORDS + ")", WANTED_RECORDS, count);
    }

    public void testGetModifiedAfter() throws Exception {
        Record r1 = new Record (testId1, testBase1, testContent1);
        storage.flush(r1);
        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);
        assertEquals("The timestamp for the retireved record should match the"
                     + " ingested", r1, r2);
        long mtime1 = r2.getModificationTime();

        storage.flush(new Record(testId2, testBase1, testContent1));
        long mtime2 = storage.getRecords(Arrays.asList(testId2), null).get(0).
                getModificationTime();
        assertTrue("Record 1 mtime should be before record 2 mtime",
                   mtime1 < mtime2);

        log.debug("Received modification timestamps: " + mtime1 + " and "
                  + mtime2);
        StorageIterator records = new StorageIterator(
                storage, storage.getRecordsModifiedAfter(mtime1,
                                                         testBase1, null));
        assertTrue("The iterator should contain a record", records.hasNext());
        Record record = records.next();
        log.debug("Got record from getModifiedAfter(" + mtime1 + ", "
                  + testBase1 + ") with id " + record.getId() + ", mtime "
                  + record.getModificationTime() + ". Time diff: " 
                  + (record.getModificationTime() - mtime1));
        if (records.hasNext()) {
            fail("Record is singular - no more records should be returned. " 
                 + "Got record " + records.next());
        }
    }

    /*
     * Assert that touching a child updates its parents recursively upwards
     */
    public void testRecursiveParentUpdates () throws Exception {
        testAddNestedRecords();

        Record recCC1 = storage.getRecord(testId4, null);
        Record recP = storage.getRecord(testId1, null);

        long mtimeCC1 = recCC1.getModificationTime();
        long ctimeCC1 = recCC1.getCreationTime();
        long mtimeP = recP.getModificationTime();
        long ctimeP = recP.getCreationTime();

        /* Touch child recCC1 of recP (nested 2 levels) */
        storage.flush (recCC1);

        recCC1 = storage.getRecord(testId4, null);
        recP = storage.getRecord(testId1, null);

        /* Sanity check that recCC1 has been updated */
        assertTrue (mtimeCC1 < recCC1.getModificationTime());
        assertTrue (ctimeCC1 == recCC1.getCreationTime());

        /* Assert that the parent has been updated as well */
        assertTrue (mtimeP < recP.getModificationTime());
        assertTrue (ctimeP == recP.getCreationTime());
    }

    public void testRecordMetaTags () throws Exception {
        Record r1 = new Record (testId1, testBase1, testContent1);
        r1.addMeta("foo", "bar");
        r1.addMeta("bork", "boo");

        storage.flush(r1);
        Record r2 = storage.getRecords(Arrays.asList(testId1), null).get(0);
        
        assertEquals(2, r2.getMeta().size());
        assertEquals(r1.getMeta("foo"), r2.getMeta("foo"));
        assertEquals(r1.getMeta("bork"), r2.getMeta("bork"));
        assertEquals(r1, r2);

        assertBaseCount(testBase1, 1);
    }

    public void testFlushAllIdCollisions() throws Exception {
        List<Record> recs = new ArrayList<Record>();

        recs.add(new Record (testId1, testBase1, testContent1));
        recs.add(new Record (testId1, testBase2, testContent1));

        storage.flushAll(recs);

        assertBaseCount(testBase1, 0);
        assertBaseCount(testBase2, 1);
    }

    public void testFlushAllWithNestedChildren() throws Exception {
        List<Record> recs = new ArrayList<Record>();

        recs.add(new Record (testId1, testBase1, testContent1));
        recs.add(new Record (testId2, testBase1, testContent1));
        recs.get(1).setChildren(Arrays.asList(new Record(testId3, testBase1,
                                                         testContent1)));

        storage.flushAll(recs);

        assertBaseCount(testBase1, 3);
    }

    /*
     * Flush several batches of parent/children pairs. This, among other things,
     * will test for connection leaks.
     */
    public void testManyParentChild() throws Exception {
        int batchCount = 20;
        int batchSize = 30;
        List<List<Record>> batches =
                                  new ArrayList<List<Record>>(batchCount);
        List<String> parentIds = new ArrayList<String>(batchSize);

        for (int j = 0; j < batchCount; j++) {
            List<Record> batch = new ArrayList<Record>(batchSize);
            batches.add(batch);
        for (int i = 0; i < batchSize; i++) {
            batch.add(new Record ("parent_"+j+"_"+i, testBase1, testContent1));
            parentIds.add("parent_"+j+"_"+i);
            batch.get(i).setChildren(Arrays.asList(new Record ("child_"+j+"_"+i,
                                                              testBase1,
                                                              testContent1)));
        }
        }

        for(int k = 0; k < batches.size(); k++) {
            storage.flushAll(batches.get(k));
        }

        assertBaseCount(testBase1, batchSize*batchCount*2);

        List<Record> result = storage.getRecords(parentIds,
                                                 new QueryOptions(null, null,
                                                                  -1 , -1));
        assertEquals(batchCount*batchSize, result.size());
        for (int l = 0; l < result.size(); l++) {
            assertEquals(1, result.get(l).getChildren().size());
            assertEquals("child_",
                         result.get(l).getChildren().get(0).getId().substring(0,6));
        }
    }
}
