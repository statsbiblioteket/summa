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

import com.ibm.icu.text.Collator;
import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * Default analyzer for fields in Summa. No transliteration will
 * be performed, but chars will be removed or replaced by space.
 *
 * @see SummaAnalyzer
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class SummaStandardAnalyzer extends SummaAnalyzer {

    /**
     * @see SummaAnalyzer
     */
    public SummaStandardAnalyzer() {
        super(Rules.VOID_TRANSLITERATIONS + Rules.BLANK_TRANSLITERATIONS, false, "", true, true);
    }

    public SummaStandardAnalyzer(Collator collator) {
        super(Rules.VOID_TRANSLITERATIONS + Rules.BLANK_TRANSLITERATIONS, false, "", true, true, collator);
    }

}




