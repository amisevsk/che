package org.eclipse.che.plugin.openshift.client;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.workspace.server.WorkspaceFilesCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

@Singleton
public class OpenShiftWorkspaceFilesCleaner implements WorkspaceFilesCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(OpenShiftConnector.class);

    private final String projectNamespace;
    private final String workspacesPvcName;

    @Inject
    public OpenShiftWorkspaceFilesCleaner(@Named("che.openshift.project") String projectNamespace,
                                          @Named("che.openshift.workspaces.pvc.name") String workspacesPvcName) {
        this.projectNamespace = projectNamespace;
        this.workspacesPvcName = workspacesPvcName;
    }

    @Override
    public void clear(Workspace workspace) throws IOException, ServerException {
        String workspaceName = workspace.getConfig().getName();
        if (isNullOrEmpty(workspaceName)) {
            LOG.error("Could not get workspace name for files removal.");
            return;
        }

        LOG.info("Deleting workspace files for workspace {} on PVC {}", workspaceName, workspacesPvcName);
        OpenShiftPvcHelper.createJobPod(workspacesPvcName,
                                        projectNamespace,
                                        workspaceName,
                                        "delete-",
                                        OpenShiftPvcHelper.Command.REMOVE);
    }

}
