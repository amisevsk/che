package org.eclipse.che.plugin.docker.machine;

import org.eclipse.che.api.machine.server.model.impl.ServerImpl;

public interface HostPortEvaluationStrategy {

    public ServerImpl getServer(String portProtocol);
}
