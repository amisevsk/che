/*******************************************************************************
 * Copyright (c) 2012-2016 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.plugin.docker.machine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.che.api.core.model.machine.ServerConf;
import org.eclipse.che.api.core.model.machine.ServerProperties;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerPropertiesImpl;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;
import org.eclipse.che.plugin.docker.client.json.PortBinding;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;

/**
 * Represents the default server evaluation strategy. By default, calling
 * {@link ServerEvaluationStrategy#getServer(String)} will return a {@link ServerImpl} with
 * internal and external address set to the address of the Docker daemon, using the
 * ephemeral ports provided by Docker.
 *
 * If the property {@code che.docker.ip.use_internal_address} is {@code true}, then
 * the internal address assigned to the server will be the container's address within
 * the internal docker network; this allows direct communication between containers.
 *
 * The addresses used for internal and external address can be overridden via the properties
 * {@code che.docker.ip} and {@code che.docker.ip.external}, respectively.
 *
 * @author Angel Misevski <amisevsk@redhat.com>
 * @see ServerEvaluationStrategy
 */
public class DefaultServerEvaluationStrategy implements ServerEvaluationStrategy {

    protected static final String SERVER_CONF_LABEL_REF_KEY      = "che:server:%s:ref";
    protected static final String SERVER_CONF_LABEL_PROTOCOL_KEY = "che:server:%s:protocol";
    protected static final String SERVER_CONF_LABEL_PATH_KEY     = "che:server:%s:path";

    /**
     * Map of additional server configurations. Overrides the data obtained from
     * {@link ContainerInfo}
     */
    private Map<String, ServerConfImpl> serverConf;

    /**
     * Port binding mappings obtained from {@link ContainerInfo}. Used to get ephemeral ports
     * for server.
     */
    private final Map<String, List<PortBinding>> ports;

    /**
     * Labels map obtained from {@link ContainerInfo}. Used to obtain server metadata
     * (e.g. ref, path, and protocol)
     */
    private final Map<String, String> labels;

    /**
     * The internal address of the server. In order of precedence, it is set according to
     * <li> If property {@code che.docker.ip} is not null, that value is used</li>
     * <li> If property {@code che.docker.ip.use_internal_address} is {@code true}, then it is set to the
     *      internal container address obtained from {@link ContainerInfo} e.g. via
     *      {@code ContainerInfo.getNetworkSettings().getIpAddress()}</li>
     * <li> If neither of the above properties are set, the address of the Docker daemon is used, as obtained
     *      from {@link ContainerInfo} via {@code ContainerInfo.getNetworkSettings().getGateway()</li>
     */
    private String internalAddress;

    /**
     * External address of the server. In order of precedence, it is set according to
     * <li> If property {@code che.docker.ip.external} is not null, that value is used.</li>
     * <li> If property {@code che.docker.ip} is not null, that value is used.</li>
     * <li> If neither of the above are true, then the value of the Docker daemon is used, as obtained
     *      from {@link ContainerInfo}</li>
     */
    private String externalAddress;

    /**
     * Switch that stores whether we are using an internal container address. It is required because
     * in the case where we are using direct communication between containers (i.e. within the docker0 network),
     * we need to communicated on the ports exposed by the container.
     */
    private boolean useEphemeralPorts;

    @Inject
    public DefaultServerEvaluationStrategy(@Assisted ContainerInfo containerInfo,
                                           @Assisted Map<String, ServerConfImpl> serverConf,
                                           @Nullable @Named("che.docker.ip") String internalAddress,
                                           @Nullable @Named("che.docker.ip.external") String externalAddress,
                                           @Nullable @Named("che.docker.ip.use_internal_address") boolean useInternal) {
        this.serverConf       = serverConf;

        if (containerInfo.getNetworkSettings() != null && containerInfo.getNetworkSettings().getPorts() != null) {
            ports = containerInfo.getNetworkSettings().getPorts();
        } else {
            ports = Collections.emptyMap();
        }

        if (containerInfo.getConfig() != null && containerInfo.getConfig().getLabels() != null) {
            labels = containerInfo.getConfig().getLabels();
        } else {
            labels = Collections.emptyMap();
        }

        // If containerInfo did not contain ports, we will not be able to return servers. Instead,
        // return early to avoid having to constantly check for null.
        if (ports.isEmpty()) {
            return;
        }

        // Make sure we set internal/external address according to precedence (see doc)
        String containerAddress = containerInfo.getNetworkSettings().getIpAddress();
        String dockerAddress    = containerInfo.getNetworkSettings().getGateway();
        this.useEphemeralPorts = true;

        if (internalAddress != null) {
            this.internalAddress = internalAddress;
        } else {
            if (useInternal && containerAddress != null && !containerAddress.isEmpty()) {
                this.internalAddress = containerAddress;
                this.useEphemeralPorts = false;
            } else {
                this.internalAddress = dockerAddress;
            }
        }

        this.externalAddress = externalAddress != null ?
                               externalAddress :
                               internalAddress != null ?
                               internalAddress :
                               dockerAddress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerImpl getServer(String portProtocol) {

        PortBinding portBinding;
        if (ports.get(portProtocol) != null) {
            portBinding = ports.get(portProtocol).get(0);
        } else {
            return null;
        }

        // Get ServerConfImpl object that contains value for ref, protocol, and path
        ServerConfImpl serverConf = getServerConfImpl(portProtocol);
        if (serverConf.getRef() == null) {
            // Add default ref to server if it was not set above
            serverConf.setRef("Server-" + portProtocol.replace('/', '-'));
        }

        // Ephemeral port is the container port that is exposed to the world,
        // Exposed port is the port exposed on the container (and mapped to the
        // ephemeral port by Docker.
        String ephemeralPort = portBinding.getHostPort();
        String exposedPort   = portProtocol.split("/")[0];

        String internalAddressAndPort;
        if (useEphemeralPorts) {
            internalAddressAndPort = internalAddress + ":" + ephemeralPort;
        } else {
            internalAddressAndPort = internalAddress + ":" + exposedPort;
        }

        String externalAddressAndPort = externalAddress + ":" + ephemeralPort;

        // Add protocol and path to internal/external address, if applicable
        String internalUrl = null, externalUrl = null;
        if (serverConf.getProtocol() != null) {
            String pathSuffix = serverConf.getPath();
            if (pathSuffix != null && !pathSuffix.isEmpty()) {
                if (pathSuffix.charAt(0) != '/') {
                    pathSuffix = "/" + pathSuffix;
                }
            } else {
                pathSuffix = "";
            }
            internalUrl = serverConf.getProtocol() + "://" + internalAddressAndPort + pathSuffix;
            externalUrl = serverConf.getProtocol() + "://" + externalAddressAndPort + pathSuffix;
        }

        ServerProperties properties = new ServerPropertiesImpl(serverConf.getPath(),
                                                               internalAddressAndPort,
                                                               internalUrl);

        return new ServerImpl(serverConf.getRef(),
                              serverConf.getProtocol(),
                              externalAddressAndPort,
                              externalUrl,
                              properties);
    }

    /**
     * Gets the {@link ServerConfImpl} object associated with {@code portProtocol}.
     *
     * @param portProtocol the port binding associated with the server
     * @return {@code ServerConfImpl}, obtained from the local {@link DefaultServerEvaluationStrategy#serverConf}
     *         object if possible, or obtained from the {@link DefaultServerEvaluationStrategy#labels} map instead.
     */
    private ServerConfImpl getServerConfImpl(String portProtocol) {

        // provided serverConf map takes precedence
        if (serverConf.get(portProtocol) != null) {
            return serverConf.get(portProtocol);
        }

        String ref, protocol, path;
        // Label can be specified without protocol -- e.g. 4401 refers to 4401/tcp
        String port = portProtocol.substring(0, portProtocol.length() - 4);

        ref = labels.get(String.format(SERVER_CONF_LABEL_REF_KEY, portProtocol));
        if (ref == null) {
            ref = labels.get(String.format(SERVER_CONF_LABEL_REF_KEY, port));
        }

        protocol = labels.get(String.format(SERVER_CONF_LABEL_PROTOCOL_KEY, portProtocol));
        if (protocol == null) {
            protocol = labels.get(String.format(SERVER_CONF_LABEL_PROTOCOL_KEY, port));
        }

        path = labels.get(String.format(SERVER_CONF_LABEL_PATH_KEY, portProtocol));
        if (path == null) {
            path = labels.get(String.format(SERVER_CONF_LABEL_PATH_KEY, port));
        }

        return new ServerConfImpl(ref, portProtocol, protocol, path);
    }
}
