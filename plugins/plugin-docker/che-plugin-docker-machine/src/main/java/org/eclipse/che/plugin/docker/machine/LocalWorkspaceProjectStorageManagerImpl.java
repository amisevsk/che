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
package org.eclipse.che.plugin.docker.machine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.workspace.server.WorkspaceProjectStorageManager;
import org.eclipse.che.plugin.docker.machine.local.node.provider.LocalWorkspaceFolderPathProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.String.format;

/**
 * todo java Doc!!!
 *
 * @author Alexander Andrienko
 */
@Singleton
public class LocalWorkspaceProjectStorageManagerImpl implements WorkspaceProjectStorageManager {

    private static final Logger LOG = LoggerFactory.getLogger(LocalWorkspaceProjectStorageManagerImpl.class);

    private final LocalWorkspaceFolderPathProvider localWorkspaceFolderPathProvider;

    @Inject
    public LocalWorkspaceProjectStorageManagerImpl(LocalWorkspaceFolderPathProvider localWorkspaceFolderPathProvider) {
        this.localWorkspaceFolderPathProvider = localWorkspaceFolderPathProvider;
    }

    @Override
    public void remove(Workspace workspace) {
        try {
            String workspaceName = workspace.getName();
            Path workspaceStoragePath = Paths.get(localWorkspaceFolderPathProvider.getPath(workspaceName));
            Files.delete(workspaceStoragePath);//maybe we should use here vfs cleaner
        } catch (IOException e) {
            LOG.error(format("Failed to delete workspace project storage with id '{}'. Cause: '{}' ",
                    workspace.getId()),
                    e.getMessage());
        }
    }
}
