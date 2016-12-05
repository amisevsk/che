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

import java.util.Map;

import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;

import com.google.inject.assistedinject.Assisted;

public interface ServerEvaluationStrategyProvider {

    public ServerEvaluationStrategy getStrategy(@Assisted ContainerInfo info,
                                                  @Assisted Map<String, ServerConfImpl> serverConf);
}
