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
package dk.statsbiblioteket.summa.search.api;

import dk.statsbiblioteket.summa.common.configuration.Configurable;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.rpc.ConnectionConsumer;
import dk.statsbiblioteket.summa.common.rpc.GenericConnectionFactory;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

/**
 * A helper class utilizing a stateless connection to a search engine exposing
 * a {@link SummaSearcher} interface. Unless your needs are very advanced
 * or you must do manual connection management, this is by far the easiest way
 * to use a remote {@link SummaSearcher}.
 * <p></p>
 * It is modeled as a {@link ConnectionConsumer} meaning that you can tweak
 * its behavior by changing the configuration parameters
 * {@link GenericConnectionFactory#CONF_RETRIES},
 * {@link GenericConnectionFactory#CONF_GRACE_TIME},
 * {@link GenericConnectionFactory#CONF_FACTORY}, and
 * {@link ConnectionConsumer#CONF_RPC_TARGET}.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_OK,
        author = "mke")
public class SearchClient extends ConnectionConsumer<SummaSearcher>
                          implements Configurable, SummaSearcher {
    private static Log log = LogFactory.getLog(SearchClient.class);

    private String target;

    public SearchClient(Configuration conf) {
        super(conf);
        target = conf.getString(ConnectionConsumer.CONF_RPC_TARGET);
        log.debug(String.format(
                "Created SearchClient with %s=%s",
                ConnectionConsumer.CONF_RPC_TARGET, target));
    }

    /**
     * Perform a search on the remote {@link SummaSearcher}. Connection handling
     * is done transparently underneath.
     * 
     * @param request The request to pass.
     * @return What ever response the search engine returns.
     * @throws IOException on communication errors with the search engine.
     */
    @Override
    public ResponseCollection search(Request request) throws IOException {
        SummaSearcher searcher = getConnection();

        if (searcher == null) {
            final String msg =
                           "The searcher retrieved from getConnection was null";
            log.warn(msg);
            throw new IOException("Unable to connect to '" + target + "'");
        }
        try {
            return searcher.search(request);
        } catch (Throwable t) {
            connectionError(t);
            throw new IOException("Search failed: " + t.getMessage(), t);
        } finally {
            releaseConnection();
        }
    }

    @Override
    public void close() throws IOException {
        releaseConnection();
    }
}
