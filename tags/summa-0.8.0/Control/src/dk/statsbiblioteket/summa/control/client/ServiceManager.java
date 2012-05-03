package dk.statsbiblioteket.summa.control.client;

import dk.statsbiblioteket.util.rpc.ConnectionManager;
import dk.statsbiblioteket.util.rpc.ConnectionFactory;
import dk.statsbiblioteket.util.rpc.ConnectionContext;
import dk.statsbiblioteket.summa.control.api.Service;
import dk.statsbiblioteket.summa.control.api.ClientConnection;
import dk.statsbiblioteket.summa.control.api.BadConfigurationException;
import dk.statsbiblioteket.summa.control.api.NoSuchServiceException;
import dk.statsbiblioteket.summa.control.bundle.BundleSpecBuilder;
import dk.statsbiblioteket.summa.common.configuration.Configurable;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.rpc.SummaRMIConnectionFactory;
import dk.statsbiblioteket.summa.common.rpc.GenericConnectionFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A helper class for the {@link Client} to manage a collection of
 * {@link Service}s.
 */
public class ServiceManager extends ConnectionManager<Service>
                            implements Configurable, Iterable<String> {

    private static Log log = LogFactory.getLog(ServiceManager.class);

    private List<String> services;

    /**
     * Configuration property defining the class of the
     * {@link ConnectionFactory} to use for creating service connections.
     */
    public static final String CONNECTION_FACTORY_PROP =
                                               GenericConnectionFactory.FACTORY;
    private String clientId;
    private String basePath;
    private String servicePath;
    private int registryPort;

    public ServiceManager (Configuration conf) {
        super (getConnectionFactory(conf));

        services = new ArrayList<String>();

        registryPort = conf.getInt(Client.REGISTRY_PORT_PROPERTY, 27000);
        clientId = System.getProperty(Client.CLIENT_ID);

        if (clientId == null) {
            throw new BadConfigurationException("System property '"
                                                + Client.CLIENT_ID + "' not set");
        }

        basePath = System.getProperty("user.home") + File.separator
                                     + conf.getString(
                                               Client.CLIENT_BASEPATH_PROPERTY,
                                               "summa-control")
                                     + File.separator + clientId;

        servicePath = basePath + File.separator + "services";

    }

    @Override
    public ConnectionContext<Service> get (String serviceId) {
        String address = getServiceAddress(serviceId);
        log.trace ("Getting address for '"+ serviceId + "': " + address + "");
        return super.get (address);
    }

    @SuppressWarnings("unchecked")
    private static ConnectionFactory<? extends Service>
                                     getConnectionFactory (Configuration conf) {
        Class<? extends ConnectionFactory> connFactClass =
                conf.getClass(CONNECTION_FACTORY_PROP, ConnectionFactory.class,
                              SummaRMIConnectionFactory.class);

        ConnectionFactory connFact = Configuration.create(connFactClass, conf);
        connFact.setGraceTime(1);
        connFact.setNumRetries(2);

        return (ConnectionFactory<Service>) connFact;
    }

    public void register (String instanceId) {
        if (instanceId == null) {
            throw new NullPointerException ("Trying to register service with id"
                                            + " 'null'");
        }

        services.add (instanceId);
    }

    public Iterator<String> iterator() {
        return services.iterator();
    }

    public boolean knows (String instanceId) {
        return getServiceFile(instanceId).exists();
    }

    public File getServiceDir (String serviceId) {
        return new File (servicePath, serviceId);
    }

    public String getServiceAddress (String serviceId) {
        if (serviceId == null) {
            throw new NullPointerException("Trying to retrieve service address "
                                           + "for service id 'null'");
        }

        return "//localhost:" + registryPort + "/" + serviceId;
    }

    public File getServiceFile (String serviceId) {
        return new File (getServiceDir(serviceId), "service.xml");
    }

    public String getBundleId (String serviceId) {
        return getBundle (serviceId).getBundleId();
    }

    private BundleSpecBuilder getBundle (String serviceId) {
        File bundleFile = getServiceFile(serviceId);
        try {
            BundleSpecBuilder builder = BundleSpecBuilder.open(bundleFile);
            return builder;
        } catch (IOException e) {
            log.warn ("Failed to read bundle file for " + serviceId, e);
            return null;
        }

    }

    public List<String> getServices () {
        String[] serviceFiles = new File (servicePath).list();
        List<String> serviceList =
                            new ArrayList<String>(Arrays.asList(serviceFiles));

        return serviceList;
    }
}