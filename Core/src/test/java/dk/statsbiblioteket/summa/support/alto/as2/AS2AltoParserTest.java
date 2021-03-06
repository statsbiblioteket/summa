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
package dk.statsbiblioteket.summa.support.alto.as2;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.filter.Payload;
import dk.statsbiblioteket.summa.common.filter.object.ObjectFilter;
import dk.statsbiblioteket.summa.common.unittest.PayloadFeederHelper;
import dk.statsbiblioteket.summa.ingest.split.StreamController;
import dk.statsbiblioteket.summa.support.alto.as.ASAltoAnalyzer;
import dk.statsbiblioteket.summa.support.alto.as.ASAltoAnalyzerTest;
import dk.statsbiblioteket.summa.support.alto.as.ASAltoParser;
import dk.statsbiblioteket.util.qa.QAInfo;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class AS2AltoParserTest extends TestCase {
//    private static Log log = LogFactory.getLog(HPAltoParserTest.class);

    public AS2AltoParserTest(String name) {
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
        return new TestSuite(AS2AltoParserTest.class);
    }

    public void testBasicParse() throws XMLStreamException, IOException {
        ObjectFilter feeder = new PayloadFeederHelper(AS2AltoAnalyzerTest.s1795_1, AS2AltoAnalyzerTest.s1846_1);
        ObjectFilter altoFilter = new StreamController(Configuration.newMemoryBased(
                StreamController.CONF_PARSER, AS2AltoParser.class
        ));
        altoFilter.setSource(feeder);
        while (altoFilter.hasNext()) {
            Payload altoPayload = altoFilter.next();
            System.out.println(altoPayload.getRecord().getContentAsUTF8());
        }
        altoFilter.close(true);
    }
}
