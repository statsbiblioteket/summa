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
package dk.statsbiblioteket.summa.control.bundle;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.storage.FileStorage;
import dk.statsbiblioteket.summa.control.api.ClientConnection;
import dk.statsbiblioteket.summa.control.api.bundle.BundleRepository;
import dk.statsbiblioteket.util.Strings;

import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Test {@link BundleRepository}.
 */
public class BundleRepositoryTest extends TestCase {
    /** Private logger. */
    private static Log log = LogFactory.getLog(BundleRepositoryTest.class);
    /** Bundle repository. */
    private BundleRepository repo;
    /** Configuration. */
    private Configuration conf;

    @Override
    public final void setUp() throws Exception {
        conf = new Configuration(new FileStorage("configuration.xml"));

        Class<? extends BundleRepository> repositoryClass =
                                    conf.getClass(
                                         ClientConnection.CONF_REPOSITORY_CLASS,
                                         BundleRepository.class,
                                         URLRepository.class);
        repo = Configuration.create(repositoryClass, conf);
    }

    @Override
    public final void tearDown() {
    }

    /**
     * Test list.
     * @throws Exception If error.
     */
    public final void testList() throws Exception {
        Configuration localConf = Configuration.newMemoryBased(
                            BundleRepository.CONF_REPO_ADDRESS,
                            "file:///${user.dir}/test/data/dummy-repo");
        BundleRepository remoteRepo = new RemoteURLRepositoryServer(localConf);

        String filter = ".*";

        List<String> list = repo.list(filter);
        log.info("Repo list for '" + filter + "':\n\t"
                           + Strings.join(list, "\n\t"));

        assertEquals(2, list.size());
        assertTrue(list.contains("foo"));
        assertTrue(list.contains("bar"));

        filter = "foo";
        list = repo.list(filter);
        log.info("Repo list for '" + filter + "':\n\t"
                 + Strings.join(list, "\n\t"));
        assertEquals(1, list.size());
        assertTrue(list.contains("foo"));
    }
}