package dk.statsbiblioteket.summa.storage.tools;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.util.LoggingExceptionHandler;
import dk.statsbiblioteket.summa.common.util.MachineStats;
import dk.statsbiblioteket.summa.storage.api.Storage;
import dk.statsbiblioteket.summa.storage.api.StorageFactory;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A small utility to launch a Storage instance
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.QA_NEEDED,
        author = "mke")
public class StorageRunner {

    private static MachineStats stats;
    
    /**
     * Create a new storage instance as defined by the configuration
     * obtained via {@link Configuration#getSystemConfiguration(boolean true)}.
     * <p/>
     * Any paramters to this method are ignored.
     *
     * @param args ignored
     */
    public static void main (String[] args) {
        Log log = LogFactory.getLog(StorageRunner.class);

        Thread.setDefaultUncaughtExceptionHandler(
                                              new LoggingExceptionHandler(log));

        Configuration conf = Configuration.getSystemConfiguration(true);        

        log.info("Creating storage instance");
        try {
            Storage storage = StorageFactory.createStorage(conf);


            log.info("Storage is running");

            try {
                stats = new MachineStats(conf, "Storage");
            } catch (Exception e) {
                log.warn("Failed to create machine stats. Not critical, but "
                         + "memory stats will not be logged", e);
            }

            // Block indefinitely (non-busy)
            while(true) {
                synchronized (conf) {
                    conf.wait();
                }
            }
        } catch (Throwable t) {
            log.fatal("Caught toplevel exception: " + t.getMessage(), t);
            t.printStackTrace();
        }
    }

}


