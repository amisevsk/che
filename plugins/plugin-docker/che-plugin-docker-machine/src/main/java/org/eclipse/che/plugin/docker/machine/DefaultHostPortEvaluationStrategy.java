package org.eclipse.che.plugin.docker.machine;


import java.util.Collections;
import java.util.List;
import java.util.Map;

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

public class DefaultHostPortEvaluationStrategy implements HostPortEvaluationStrategy {

    protected static final String SERVER_CONF_LABEL_REF_KEY      = "che:server:%s:ref";
    protected static final String SERVER_CONF_LABEL_PROTOCOL_KEY = "che:server:%s:protocol";
    protected static final String SERVER_CONF_LABEL_PATH_KEY     = "che:server:%s:path";


    private final ContainerInfo containerInfo;

    private Map<String, ServerConfImpl> serverConf;

    private final Map<String, List<PortBinding>> ports;

    private final Map<String, String> labels;

    private String internalHostname;

    private String externalHostname;

    private boolean useEphemeralPorts;

    @Inject
    public DefaultHostPortEvaluationStrategy(@Assisted ContainerInfo containerInfo,
                                             @Assisted Map<String, ServerConfImpl> serverConf,
                                             @Nullable @Named("che.docker.ip") String internalHostname,
                                             @Nullable @Named("che.docker.ip.external") String externalHostname,
                                             @Nullable @Named("che.docker.ip.use_internal_address") boolean useInternal) {
        this.containerInfo    = containerInfo;
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

        String containerAddress = containerInfo.getNetworkSettings().getIpAddress();
        String dockerAddress    = containerInfo.getNetworkSettings().getGateway();
        this.useEphemeralPorts = true;

        if (internalHostname != null) {
            this.internalHostname = internalHostname;
        } else {
            if (useInternal && containerAddress != null && !containerAddress.isEmpty()) {
                this.internalHostname = containerAddress;
                this.useEphemeralPorts = false;
            } else {
                this.internalHostname = dockerAddress;
            }
        }

        this.externalHostname = externalHostname != null ?
                                externalHostname :
                                dockerAddress;
    }

    @Override
    public ServerImpl getServer(String portProtocol) {

        PortBinding portBinding;
        if (ports.get(portProtocol) != null) {
            portBinding = ports.get(portProtocol).get(0);
        } else {
            return null;
        }

        ServerConfImpl serverConf = getServerConfImpl(portProtocol);

        String ephemeralPort = portBinding.getHostPort();
        String exposedPort   = portProtocol.split("/")[0];

        String internalAddressAndPort;
        if (useEphemeralPorts) {
            internalAddressAndPort = internalHostname + ":" + ephemeralPort;
        } else {
            internalAddressAndPort = internalHostname + ":" + exposedPort;
        }

        String externalAddressAndPort = externalHostname + ":" + ephemeralPort;

        String internalUrl = null, externalUrl = null;
        if (serverConf.getProtocol() != null) {
            String pathSuffix = serverConf.getPath();
            if (pathSuffix != null && !pathSuffix.isEmpty()) {
                if (pathSuffix.charAt(0) != '/') {
                    pathSuffix = "/" + pathSuffix;
                }
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

    private ServerConfImpl getServerConfImpl(String portProtocol) {

        // provided serverConf map takes precedence
        if (serverConf.get(portProtocol) != null) {
            return serverConf.get(portProtocol);
        }

        String ref, protocol, path;
        String port = portProtocol.substring(0, portProtocol.length() - 4);

        ref = labels.get(String.format(SERVER_CONF_LABEL_REF_KEY, portProtocol));
        if (ref == null) {
            ref = labels.getOrDefault(String.format(SERVER_CONF_LABEL_REF_KEY, port),
                                      "Server-" + portProtocol.replace("/", "-"));
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
