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

import java.util.Map;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.plugin.docker.machine.local.LocalDockerModule;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Provides {@code ServerEvaluationStrategies}. Intended to be injected into {@link DockerInstanceRuntimeInfo}
 * instances to aid in {@link DockerInstanceRuntimeInfo#getServers()}
 *
 * <p>Strategy to be used is selected by property {@code che.docker.server_evaluation_strategy}, which should
 * refer to a strategy bound in {@link LocalDockerModule}. If the strategy cannot be found, the default will
 * be used.
 *
 * @author Angel Misevski <amisevsk@redhat.com>
 * @see ServerEvaluationStrategy
 */
public class ServerEvaluationStrategyProvider implements Provider<ServerEvaluationStrategy> {

    private ServerEvaluationStrategy strategy;

    @Inject
    public ServerEvaluationStrategyProvider(Map<String, ServerEvaluationStrategy> strategies,
                                            @Named("che.docker.server_evaluation_strategy") String chosenStrategy)
                                            throws ServerException {
        if (strategies.containsKey(chosenStrategy)) {
            this.strategy = strategies.get(chosenStrategy);
        } else {
            throw new ServerException(String.format("Property che.docker.server_evaluation_strategy=%s "
                                                  + "does not match provided strategies", chosenStrategy));
        }
    }

    @Override
    public ServerEvaluationStrategy get() {
        return strategy;
    }
}
