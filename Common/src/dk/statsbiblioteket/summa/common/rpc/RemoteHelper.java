package dk.statsbiblioteket.summa.common.rpc;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.lang.management.ManagementFactory;
import java.io.IOException;

/**
 * Utility class to help export remote interfaces
 */
public class RemoteHelper {

    static private final Log log = LogFactory.getLog (RemoteHelper.class);

    /**
     * Expose the an object as a remote service. Currently this implementation
     * only works for RMI and {@link UnicastRemoteObject}s, but that might be
     * extended in the future.
     *
     * @param obj Object to bind
     * @param registryPort the port on which the registry should run. If no
     *                     registry is found here, one will be created
     * @param serviceName the name of the service to export
     * @throws IOException if there is an error exporting the interface
     */
    public static void exportRemoteInterfaces(Object obj,
                                              int registryPort,
                                              String serviceName)
                                                            throws IOException {
        UnicastRemoteObject remote = (UnicastRemoteObject) obj;
        Registry reg = null;

        try {
            reg = LocateRegistry.createRegistry(registryPort);
            log.debug("Created registry on port " + registryPort);
        } catch (RemoteException e) {
            reg = LocateRegistry.getRegistry("localhost", registryPort);
            log.debug ("Found registry localhost:" + registryPort);
        }


        if (reg == null) {
            throw new RemoteException ("Failed to locate or create registry on "
                                        + "localhost:" + registryPort);
        }

        reg.rebind(serviceName, remote);        

        log.info(remote.getClass().getSimpleName()
                + " bound in registry on //localhost:" + registryPort + "/"
                 + serviceName);


    }

    /**
     * Export an object as a JMX MBean
     * @param obj
     * @throws IOException
     */
    public static void exportMBean (Object obj) throws IOException {
        ObjectName name = null;

        try {
            log.debug ("Registering " + obj.getClass().getName()
                       + " at mbean server");

            MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
            name = new ObjectName(obj.getClass().getName()+ ":type=" + obj.getClass().getSimpleName());
            mbserver.registerMBean(obj, name);

            log.info ("Registered " + obj.getClass().getName()
                      + " at mbean server as " + name);
        } catch (Exception e) {
            throw new IOException("Failed to bind MBean '" + obj + "' "
                                  + "with '" + name + "'", e);
        }
    }

}
