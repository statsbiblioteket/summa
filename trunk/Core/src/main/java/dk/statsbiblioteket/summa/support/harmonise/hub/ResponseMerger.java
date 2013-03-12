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
package dk.statsbiblioteket.summa.support.harmonise.hub;

import dk.statsbiblioteket.summa.common.configuration.Configurable;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.support.harmonise.hub.core.HubAggregatorBase;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.SolrParams;

import java.util.List;

/**
 * Merges responses with the possibility of prioritizing certain sources over others.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public class ResponseMerger implements Configurable {
    private static Log log = LogFactory.getLog(ResponseMerger.class);

    public ResponseMerger(Configuration conf) {
        log.info("Created " + this);
    }

    public QueryResponse merge(SolrParams params, List<HubAggregatorBase.NamedResponse> responses) {
        if (responses.size() == 1) {
            return responses.get(0).getResponse();
        }
        throw new UnsupportedOperationException("Merging of more than 1 response is not implemented yet");
    }

    @Override
    public String toString() {
        return "ResponseMerger(not properly implemented)";
    }
}
