/* $Id: ClientConnection.java,v 1.15 2007/10/11 12:56:25 te Exp $
 * $Revision: 1.15 $
 * $Date: 2007/10/11 12:56:25 $
 * $Author: te $
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.score.api;

import dk.statsbiblioteket.summa.score.client.Client;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.util.qa.QAInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.net.URL;

/**
 * A connection to a running {@link Client} used to deploy 
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public interface ClientConnection extends Remote {

    /** <p>Property defining the id under which the client should report itself
     * via {@link #getId}. </p>
     *
     * <p>The client will install itself under
     * <{@code summa.score.client.basepath}>/<{@code summa.score.client.id}></p>
     *
     * <p>The client's RMI service will also run under this name.</p> 
     * */
    public static final String CLIENT_ID = "summa.score.client.id";

    /** Property defining the relative path under which the client
     * should install itself. The path is relative to the system property
     * {@code user.home} of the client's jvm<br/>
     * <br/>
     * The client will install itself under
     * {@code <summa.score.client.basepath>/<summa.score.client.id>} */
    public static final String CLIENT_BASEPATH = "summa.score.client.basepath";

    /** Property defining the port on which the client's rmi service should run */
    public static final String SERVICE_PORT = "summa.score.client.service.port";

    /** Property defining the port on which the client should contact or create
     * an rmi registry, see {@link #REGISTRY_HOST} */
    public static final String REGISTRY_PORT = "summa.score.client.registry.port";

    /** Property defining the host on which the client can find the rmi registry.
     * If this is set to {@code localhost} the client will create the registry
     * if it is not already running */
    public static final String REGISTRY_HOST = "summa.score.client.registry.host";

    /** <p>Property containing the class of {@link dk.statsbiblioteket.summa.score.bundle.BundleRepository}
     * a {@link Client} should use for fetching bundles.</p> */
    public static final String REPOSITORY_CLASS =
                                          "summa.score.client.repository.class";

    /** Property from which a sub-{@link Configuration} for the
     * {@link dk.statsbiblioteket.summa.score.bundle.BundleRepository} used by 
     * by a {@link Client} */
    public static final String REPOSITORY_CONFIG =
                                         "summa.score.client.repository.config";

    /** Property from which a sub-{@link Configuration} for the
     * {@link dk.statsbiblioteket.summa.score.bundle.BundleLoader} used by
     * by a {@link Client} */
    public static final String LOADER_CONFIG =
                                             "summa.score.client.loader.config";

    /**
     * Property defining the path uner which persisten files for Clients as
     * well as Services should be stored.
     */
    public static final String CLIENT_PERSISTENT_DIR =
                                            "summa.score.client.persistent.dir";

    /**
     * Stop the client specified in the base configuration for the
     * ClientDeployer.
     *
     * This call should stop the JVM of the client. Ie, call {@link System#exit}
     * 
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public void stop() throws RemoteException;

    /**
     * Get the status for the client.
     * @return null if the client could not be contacted, else a client-specific
     * string.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public Status getStatus() throws RemoteException;

    /**
     * Fetches the service from packageLocation and deploys it according to the
     * configuration and with the given id.
     * @param id               the id for the service (should be unique).
     * @param configLocation    deploy-specific properties. This should not be
     *                         confused with the properties for
     *                         {@link #startService}, although it is probably
     *                         easiest just to merge the two configurations to
     *                         one.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public void deployService(String id, String configLocation)
                              throws RemoteException;

    /**
     * Start the given service with the given configuration. If the service
     * is not deployed an error is thrown.
     * @param id               the id for the service.
     * @param configLocation    service-specific properties.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public void startService(String id, String configLocation)
                             throws RemoteException;

    /**
     * Stop the given service. If the service is not deployed, an error is
     * thrown. If the service is already stopped, nothing happens.
     * @param id               the id for the service.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public void stopService(String id) throws RemoteException;

    /**
     * Get the status for a specific service. If the service is not deployed,
     * an error is thrown.
     * @param id the id for the service.
     * @return   the status for the service.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public Status getServiceStatus(String id) throws RemoteException;

    /**
     * Iterate through the deployed services and collect a list of the ids
     * for said services.
     * @return a list of all the deployed services.
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public List<String> getServices() throws RemoteException;

    /**
     * Return the id of the client as set through the configuration property
     * {@link #CLIENT_ID}
     * @return the id
     * @throws java.rmi.RemoteException in case of communication errors.
     */
    public String getId() throws RemoteException;

}
