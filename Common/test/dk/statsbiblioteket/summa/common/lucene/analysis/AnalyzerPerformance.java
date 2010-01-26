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
package dk.statsbiblioteket.summa.common.lucene.analysis;

import static dk.statsbiblioteket.summa.common.lucene.analysis.SampleDataLoader.*;
import dk.statsbiblioteket.util.Strings;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.WhitespaceAnalyzer;

import java.io.Writer;
import java.io.CharArrayWriter;
import java.io.StringReader;

/**
 * Performance tests for the analyzers
 */
public class AnalyzerPerformance {

    final int NUM_TESTS = 2000;

    Analyzer a;
    TokenStream t;

    public AnalyzerPerformance() throws Exception {
        a = new SummaStandardAnalyzer();
        //a = new SummaAnalyzer("", true, "", true, true);
        //a = new WhitespaceAnalyzer();

        System.out.println("Running " + NUM_TESTS + " tests on "
                           + a.getClass().getSimpleName());

        CharArrayWriter out = new CharArrayWriter();
        long testTime = 0;
        long testStart;
        long dummyInspection = 0; // Prevent sneaky JIT compiling
        for (int i = 0; i < NUM_TESTS; i++) {
            testStart = System.nanoTime();
            t = a.reusableTokenStream("testField", getDataReader(0));
            dumpTokens(t, out);
            dummyInspection += out.size();
            out.reset();
            testTime += (System.nanoTime() - testStart);

            if (i % (NUM_TESTS/5) == 0 && i != 0) {
                System.out.println("At " + i + ", avg. test time (ms) : "
                           + ((testTime/1000000D)/i));
            }
        }

        System.out.println("Total avg. test time (ms) : "
                           + ((testTime/1000000D)/NUM_TESTS));

        // Prevent sneaky JIT compiling... Hopefully :-)
        System.out.println("Garbage data: " + dummyInspection);
    }

    public static void dumpTokens(TokenStream t, Appendable out) throws Exception {
        Token tok = new Token();

        while ((tok = t.next(tok)) != null) {
            out.append(tok.term());
            out.append('\n');
        }
    }

    public static void main(String[] args) {
        try {
            new AnalyzerPerformance();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}

