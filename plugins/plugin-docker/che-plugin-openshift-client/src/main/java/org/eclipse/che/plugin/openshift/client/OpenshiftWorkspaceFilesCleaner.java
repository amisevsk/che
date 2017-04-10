package org.eclipse.che.plugin.openshift.client;

import java.io.File;
import java.io.IOException;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.workspace.server.WorkspaceFilesCleaner;
import static org.eclipse.che.commons.lang.IoUtil.deleteRecursive;

public class OpenshiftWorkspaceFilesCleaner implements WorkspaceFilesCleaner {

    @Override
    public void clear(Workspace workspace) throws IOException, ServerException {
        String workspaceName = workspace.getConfig().getName();
        deleteRecursive(new File("/projects/" + workspaceName));
    }

}
