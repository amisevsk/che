package org.eclipse.che.plugin.docker.machine;

import java.util.Map;

import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;

import com.google.inject.assistedinject.Assisted;

public interface HostPortEvaluationStrategyProvider {

    public HostPortEvaluationStrategy getStrategy(@Assisted ContainerInfo info,
                                                  @Assisted Map<String, ServerConfImpl> serverConf);
}
