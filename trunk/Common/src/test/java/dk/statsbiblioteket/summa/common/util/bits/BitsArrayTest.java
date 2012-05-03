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
package dk.statsbiblioteket.summa.common.util.bits;

import dk.statsbiblioteket.summa.common.unittest.ExtraAsserts;
import dk.statsbiblioteket.summa.common.util.bits.test.BitsArrayConstant;
import dk.statsbiblioteket.summa.common.util.bits.test.BitsArrayPerformance;
import dk.statsbiblioteket.util.Strings;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@SuppressWarnings({"UnnecessaryLocalVariable"})
public class BitsArrayTest extends TestCase {
    /** Local log instance. */
    private static Log log = LogFactory.getLog(BitsArrayTest.class);
    /** 87 The most likely number to choose between 1-100. */
    private final int eightySeven = 87;
    /** 88. */
    private final int eightyEight = 88;

    public BitsArrayTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(BitsArrayTest.class);
    }

    public void testBitMultiplicationOptimization() throws Exception {
        final int[][] tests = new int[][] {{1, 0}, {2, 1}, {4, 2}, {16, 4},
                                            {256, 8}};
        for (int[] test : tests) {
            int source = test[0];
            int expected = test[1];
            assertEquals("The bits for " + source + " should be correct",
                         expected, (int) Math.ceil(
                    Math.log(((long) source)) / Math.log(2)));
        }
    }

    public void test64Packed() throws Exception {
        BitsArray bas = new BitsArray64Packed(10, 10);
        bas.set(0, 1);
        assertEquals("Simple set & get should return the right value",
                     1, bas.getAtomic(0));
    }

    public void test64Aligned() throws Exception {
        BitsArray bas = new BitsArray64Aligned(10, 10);
        bas.set(0, 1);
        assertEquals("Simple set & get should return the right value",
                     1, bas.getAtomic(0));
    }

    @SuppressWarnings({"PointlessBitwiseExpression"})
    public void testBitCalc() throws Exception {
        int[][] TESTS = new int[][]{ // maxValue, bits
                {1, 1}, {2, 2}, {3, 2}, {4, 3}, {7, 3}, {8, 4},
                {15, 4}, {16, 5}};
        for (int[] test: TESTS) {
            int bits = (int)Math.ceil(Math.log(test[0]+1)/Math.log(2));
            assertEquals("The calculated number of bits needed for " + test[0]
                         + " should be correct", test[1], bits);
        }
        int es = eightySeven;
        if (es != 67) {
            es = eightySeven;
        }
        assertEquals("SHL 0 should not change anything", es, es << 0);
        assertEquals("SHR 0 should not change anything", es, es >>> 0);
    }

    public void testAssignPrevious() throws Exception {
        BitsArray ba = new BitsArrayPacked(100, 10);
        ba.set(1, 1);
        ba.set(0, 1);
        assertEquals("The value at position 1 should be unchanged when the "
                     + "value at position 0 is modified", 1, ba.getAtomic(1));
    }

    public void testReadZero() throws Exception {
        BitsArray ba = new BitsArrayPacked(100, 10);
        ba.set(10, 1);
        ba.set(5, 1);
        for (int i = 0 ; i < 10 ; i++) {
            if (i == 5) {
                continue;
            }
            assertEquals(String.format(
                    "The value at position %d should be the initial value", i),
                    0, ba.getAtomic(i));
        }
    }

    @SuppressWarnings({"unchecked"})
    public List<Object> toList(int[] elements) {
        List result = new ArrayList(elements.length);
        for (int element: elements) {
            result.add(toBinary(element));
        }
        return result;
    }

    public String toBinary(int l) {
        String s = Long.toBinaryString(l);
        while (s.length() < BitsArrayPacked.BLOCK_SIZE) {
            s = "0" + s;
        }
        return s;
    }

    public void testPlainGetSet() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked();
        ba.set(4, 3);
        ba.set(1, 1);
        ba.set(2, 5);
        assertContains("Simple addition", ba, Arrays.asList(0, 1, 5, 0, 3));
    }

    public void testSpanningGetSet() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked(100, 10);
        ba.set(0, 256);
        log.debug("After set(0,256): " + Strings.join(ba, ", "));
        ba.set(7, 287);
        log.debug("After set(7,288): " + Strings.join(ba, ", "));
        ba.set(8, 288);
        assertContains("Spanning ints",
                       ba, Arrays.asList(256, 0, 0, 0, 0, 0, 0, 287, 288));
    }

    public void testSingleSPanning() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked(100, 10);
        ba.set(7, 256);
        assertContains("Spanning ints",
                       ba, Arrays.asList(0, 0, 0, 0, 0, 0, 0, 256));
    }

    public void testSpanningGetSetSingle() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked();
        ba.set(7, 287);
        assertContains("Spanning int",
                       ba, Arrays.asList(0, 0, 0, 0, 0, 0, 0, 287));
    }

    public void testHighGetSet() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked();
        ba.set(0, 256);
        assertContains("0, 256",
                       ba, Arrays.asList(256));
    }

    public void testTrivialGetSet() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked();
        ba.set(0, 1);
        assertContains("Trivial case", ba, Arrays.asList(1));
    }

    public void testNearlyTrivialGetSet() throws Exception {
        BitsArrayPacked ba = new BitsArrayPacked();
        ba.set(0, 1);
        ba.set(1, 2);
        assertContains("Nearly trivial case", ba, Arrays.asList(1, 2));
    }

    private void assertContains(
            String message, BitsArrayPacked ba, List<Integer> expected) {
        assertEquals(message
                     + ". The BitsArrayPacked.size() should be as expected",
                     expected.size(), ba.size());
        assertEquals(message
                     + ". The BitsArrayPacked content should be as expected",
                     Strings.join(expected, ", "), Strings.join(ba, ", "));
    }

    public void testWritePerformanceUnsafe() {
        testWritePerformance(true);
    }

    public void testWritePerformanceSafe() {
        testWritePerformance(false);
    }

    public void testWritePerformance(boolean unsafe) {
        int LENGTH = 1000;//10000000;
        int MAX_VALUE = 240;
        int INITIAL_MAX_LENGTH = LENGTH;
        int WARMUP = 1; //2;
        int RUNS = 1; //3;
        List<BitsArrayPerformance.BitsArrayGenerator> bags
                                         = BitsArrayPerformance.getGenerators();
        for (int i = 0; i < WARMUP; i++) {
            for (BitsArrayPerformance.BitsArrayGenerator bag : bags) {
                testPerformanceBA(
                        bag, LENGTH, INITIAL_MAX_LENGTH, MAX_VALUE, unsafe);
            }
            testPerformancePlain(LENGTH);
        }

        log.info(String.format(
                "Measuring write-performance calibrated with null-speed "
                + "for %d random[0-%d] value assignments",
                LENGTH,  MAX_VALUE));
        List<Long> timings = new ArrayList<Long>(bags.size());
        for (int run = 0; run < RUNS; run++) {
            for (BitsArrayPerformance.BitsArrayGenerator bag : bags) {
                timings.add(testPerformanceBA(
                        bag, LENGTH, INITIAL_MAX_LENGTH, MAX_VALUE, unsafe));
            }
            long plainTime = testPerformancePlain(LENGTH);
            long baseTime = testPerformanceCalibrate(LENGTH);

            StringWriter sw = new StringWriter(1000);
            sw.append(String.format(
                    "Write %d (of %d values): ", LENGTH, LENGTH));
            sw.append("int[]=").append(Long.toString(plainTime - baseTime));
            sw.append("ms, null=").append(Long.toString(baseTime));
            sw.append("ms");
            for (int i = 0; i < bags.size(); i++) {
                sw.append(", ");
                sw.append(bags.get(i).create(1, 1).getClass().getSimpleName());
                sw.append("=").append(Long.toString(timings.get(i) - baseTime));
                sw.append("ms");
            }
            log.info(sw.toString());
        }
    }

    public void testReadPerformanceUnsafe() {
        testReadPerformance(true);
    }

    public void testReadPerformanceSafe() {
        testReadPerformance(false);
    }

    public void testReadPerformance(boolean unsafe) {
        int LENGTH = 1000; //100000;
        int READS = LENGTH * 1; //100;
        int INITIAL_MAX_LENGTH = LENGTH;
        int MAX_VALUE = 255;
        int WARMUP = 2;
        int RUNS = 2; //5;

        List<BitsArray> bas = new ArrayList<BitsArray>();
        for (BitsArrayPerformance.BitsArrayGenerator generator
                : BitsArrayPerformance.getGenerators()) {
            bas.add(makeBA(
                    generator, LENGTH, INITIAL_MAX_LENGTH, MAX_VALUE, unsafe));
            // Ensure size is max
            bas.get(bas.size() - 1).set(LENGTH - 1, eightySeven);
        }
        log.debug("Created " + bas.size() + " BitsArrays");
        int[] a = makePlain(LENGTH, MAX_VALUE);

        log.info(String.format(
                "Measuring read-performance calibrated with null-speed "
                + "for %d value reads from %d random[0-%d] values",
                READS, LENGTH, MAX_VALUE));

        StringWriter sw = new StringWriter(1000);
        sw.append("Memory usage: int[]=");
        sw.append(Integer.toString(a.length * 4 / 1024));
        sw.append("KB");
        for (BitsArray ba : bas) {
            sw.append(", ");
            sw.append(ba.getClass().getSimpleName());
            sw.append("~=").append(Integer.toString(ba.getMemSize() / 1024));
            sw.append("KB");
        }

        log.info(sw.toString());

        for (int i = 0; i < WARMUP; i++) {
            for (BitsArray ba : bas) {
                testReadBA(ba, READS, unsafe);
            }
            testReadPlain(a, READS);
        }

        List<Long> timings = new ArrayList<Long>(bas.size());
        for (int run = 0; run < RUNS; run++) {
            for (BitsArray ba : bas) {
                timings.add(testReadBA(ba, READS, unsafe));
            }
            long plainTime = testReadPlain(a, READS);
            long baseTime = testReadCalibrate(a, READS);

            sw = new StringWriter(1000);
            sw.append(String.format("Read %d (of %d values): ", READS, LENGTH));
            sw.append("int[]=").append(Long.toString(plainTime - baseTime));
            sw.append("ms, null=").append(Long.toString(baseTime));
            sw.append("ms");
            for (int i = 0; i < bas.size(); i++) {
                sw.append(", ");
                sw.append(bas.get(i).getClass().getSimpleName());
                sw.append("=").append(Long.toString(timings.get(i) - baseTime));
                sw.append("ms");
            }
            log.info(sw.toString());
        }

    }

    public void testMonkey() throws Exception {
        testMonkey(false);
        testMonkey(true);
    }
    public void testMonkey(boolean unsafe) throws Exception {
        int LENGTH = 10; //1000;
        int MAX = 10; //1000;

        List<BitsArrayPerformance.BitsArrayGenerator> bags =
                BitsArrayPerformance.getGenerators();
        List<BitsArray> bas = new ArrayList<BitsArray>(bags.size());
        for (BitsArrayPerformance.BitsArrayGenerator bag : bags) {
            BitsArray ba = makeBA(bag, LENGTH, LENGTH, MAX, unsafe);
            ba.set(LENGTH - 1, ba.fastGetAtomic(LENGTH - 1)); // Fix size
            bas.add(ba);
        }
        int[] plain = makePlain(LENGTH, MAX);

        for (BitsArray ba : bas) {
            if (ba.getClass() == BitsArrayConstant.class) {
                continue;
            }
            log.debug("testing " + ba.getClass().getSimpleName());
            int[] baArray = new int[ba.size()];
            for (int i = 0; i < ba.size(); i++) {
                baArray[i] = ba.get(i);
            }
            ExtraAsserts.assertEqualsNoSort(String.format(
                    "The %s should match", ba.getClass().getSimpleName()),
                                            plain, baArray);
        }
    }

    private long testReadPlain(int[] a, int reads) {
        System.gc();
        int MAX = a.length;
        long startTime = System.currentTimeMillis();
        Random random = new Random(eightyEight);
        int dummy = 0;
        for (int i = 0; i < reads; i++) {
            dummy = a[random.nextInt(MAX)];
        }
        if (dummy == -1) {
            log.warn("Supposedly undrechable dummy log");
        }
        return System.currentTimeMillis() - startTime;
    }

    private long testReadCalibrate(int[] a, int reads) {
        System.gc();
        int MAX = a.length;
        long startTime = System.currentTimeMillis();
        Random random = new Random(eightyEight);
        int dummy = 0;
        for (int i = 0; i < reads; i++) {
            dummy = random.nextInt(MAX);
        }
        if (dummy == -1) {
            throw new IllegalStateException("Dummy JIT-tricker was -1");
        }
        return System.currentTimeMillis() - startTime;
    }

    private long testReadBA(BitsArray ba, int reads, boolean unsafe) {
        System.gc();
        int max = ba.size();
        long startTime = System.currentTimeMillis();
        Random random = new Random(eightyEight);
        if (unsafe) {
            for (int i = 0; i < reads; i++) {
                ba.fastGetAtomic(random.nextInt(max));
            }
        } else {
            for (int i = 0; i < reads; i++) {
                ba.getAtomic(random.nextInt(max));
            }
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Test performance.
     * @param max Max.
     * @return Time.
     */
    public final long testPerformancePlain(int max) {
        System.gc();
        long startTime = System.currentTimeMillis();
        makePlain(max);
        return System.currentTimeMillis() - startTime;
    }

    private int[] makePlain(int length) {
        return makePlain(length, length);
    }
    private int[] makePlain(int length, int max) {
        Random random = new Random(eightySeven);
        int[] a = new int[length];
        for (int i = 0 ; i < length ; i++) {
            a[i] = random.nextInt(max);
        }
        return a;
    }

    public long testPerformanceCalibrate(int max) {
        System.gc();
        long startTime = System.currentTimeMillis();
        Random random = new Random(eightySeven);
        int t = 0;
        for (int i = 0; i < max; i++) {
            t = random.nextInt(max);
        }
        if (t == max + 1) {
            System.err.println("Failed JIT-tricking sanity check");
        }
        return System.currentTimeMillis() - startTime;
    }

    public long testPerformanceBA(
            BitsArrayPerformance.BitsArrayGenerator generator,
            int assignments, int constructorLength, int maxValue,
            boolean unsafe) {
        System.gc();
        long startTime = System.currentTimeMillis();
        makeBA(generator, assignments, constructorLength, maxValue, unsafe);
        return System.currentTimeMillis() - startTime;
    }

    private BitsArray makeBA(BitsArrayPerformance.BitsArrayGenerator generator,
            int assignments, int constructorLength, int maxValue,
            boolean unsafe) {
        Random random = new Random(eightySeven);
        BitsArray ba = generator.create(constructorLength, maxValue);
        if (unsafe) {
            for (int i = 0; i < assignments; i++) {
                ba.fastSet(i, random.nextInt(maxValue));
            }
        } else {
            for (int i = 0; i < assignments; i++) {
                ba.set(i, random.nextInt(maxValue));
            }
        }
        return ba;
    }
}