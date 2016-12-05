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

import org.eclipse.che.api.machine.server.model.impl.ServerImpl;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;

/**
 * Represents a strategy for resolving Servers associated with workspace containers.
 * Used to extract relevant information from e.g. {@link ContainerInfo} into a
 * {@link ServerImpl} object.
 *
 * @author Angel Misevski <amisevsk@redhat.com>
 * @see ServerEvaluationStrategyProvider
 */
public interface ServerEvaluationStrategy {

    /**
     * Gets the server on the container associated with an exposed port
     * @param portProtocol The port exposed by the container (e.g. "4401/tcp")
     * @return The server implementation associated with the provided port.
     */
    public ServerImpl getServer(String portProtocol);
}
