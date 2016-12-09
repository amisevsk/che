/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.plugin.docker.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.che.api.machine.server.model.impl.ServerImpl;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;
import org.eclipse.che.plugin.docker.client.json.PortBinding;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Represents the default server evaluation strategy. By default, calling
 * {@link ServerEvaluationStrategy#getServers(ContainerInfo, String, Map)} will return a completed
 * {@link ServerImpl} with internal and external address set to the address of the Docker host.
 *
 * <p>The addresses used for internal and external address can be overridden via the properties
 * {@code che.docker.ip} and {@code che.docker.ip.external}, respectively.
 *
 * @author Angel Misevski <amisevsk@redhat.com>
 * @see ServerEvaluationStrategy
 */
public class DefaultServerEvaluationStrategy extends ServerEvaluationStrategy {

    @Inject
    public DefaultServerEvaluationStrategy (@Nullable @Named("che.docker.ip") String internalAddress,
                                            @Nullable @Named("che.docker.ip.external") String externalAddress) {
        super(internalAddress, externalAddress);
    }

    @Override
    protected Map<String, String> getInternalAddressesAndPorts(ContainerInfo containerInfo, String internalHost) {
        String internalAddressContainer = containerInfo.getNetworkSettings().getGateway();

        String internalAddress = internalAddressProperty != null ?
                                 internalAddressProperty :
                                 internalAddressContainer != null && !internalAddressContainer.isEmpty() ?
                                 internalAddressContainer :
                                 internalHost;

        Map<String, List<PortBinding>> portBindings = containerInfo.getNetworkSettings().getPorts();

        return getAddressesAndPorts(internalAddress, portBindings);
    }

    @Override
    protected Map<String, String> getExternalAddressesAndPorts(ContainerInfo containerInfo, String internalHost) {
        String externalAddressContainer = containerInfo.getNetworkSettings().getGateway();

        String externalAddress = externalAddressProperty != null ?
                                 externalAddressProperty :
                                 externalAddressContainer != null && !externalAddressContainer.isEmpty() ?
                                 externalAddressContainer :
                                 internalHost;

        Map<String, List<PortBinding>> portBindings = containerInfo.getNetworkSettings().getPorts();

        return getAddressesAndPorts(externalAddress, portBindings);
    }

    private Map<String, String> getAddressesAndPorts(String address, Map<String, List<PortBinding>> ports) {
        Map<String, String> addressesAndPorts = new HashMap<>();
        for (String portKey : ports.keySet()) {
            String port = ports.get(portKey).get(0).getHostPort();
            addressesAndPorts.put(portKey, address + ":" + port);
        }
        return addressesAndPorts;
    }
}
