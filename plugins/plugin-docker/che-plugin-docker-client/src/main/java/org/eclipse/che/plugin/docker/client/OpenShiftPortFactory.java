/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.docker.client;

import com.openshift.internal.restclient.model.ServicePort;
import org.jboss.dmr.ModelNode;

public class OpenShiftPortFactory {
    public static ServicePort createServicePort(String name, String proto, int port, int targetPort) {
        return createServicePort(name, proto, port, String.valueOf(targetPort));
    }

    public static ServicePort createServicePort(String name, String proto, int port, String targetPort) {
        ModelNode node = new ModelNode();
        node.get("name").set(name);
        node.get("protocol").set(proto);
        node.get("port").set(port);
        node.get("targetPort").set(targetPort);
        return new ServicePort(node);
    }

}
