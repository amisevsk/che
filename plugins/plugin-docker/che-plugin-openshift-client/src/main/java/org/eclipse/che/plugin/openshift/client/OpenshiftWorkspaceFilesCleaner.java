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
public class OpenshiftWorkspaceFilesCleaner implements WorkspaceFilesCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(OpenShiftConnector.class);

    private static final String POD_PHASE_SUCCEEDED        = "Succeeded";
    private static final String POD_PHASE_FAILED           = "Failed";
    private static final String NAME_PREFIX                = "delete-";
    private static final String PVC_PROJECT_DIR            = "/projects";
    private static final String[] RM_WORKSPACE_DIR_COMMAND = new String[] {"rm", "-rf"};

    private OpenShiftClient openShiftClient;
    private final String projectNamespace;
    private final String workspacesPvcName;

    @Inject
    public OpenshiftWorkspaceFilesCleaner(@Named("che.openshift.project") String projectNamespace,
                                          @Named("che.openshift.workspaces.pvc.name") String workspacesPvcName) {
        this.projectNamespace = projectNamespace;
        this.workspacesPvcName = workspacesPvcName;
        openShiftClient = new DefaultOpenShiftClient();
    }

    @Override
    public void clear(Workspace workspace) throws IOException, ServerException {
        String workspaceName = workspace.getConfig().getName();
        if (isNullOrEmpty(workspaceName)) {
            LOG.error("Could not get workspace name for files removal.");
            return;
        }

        LOG.info("Deleting workspace files for workspace {}", workspaceName);
        VolumeMount vm = new VolumeMountBuilder().withMountPath("/projects")
                                                 .withName(workspacesPvcName)
                                                 .build();

        PersistentVolumeClaimVolumeSource pvcs = new PersistentVolumeClaimVolumeSourceBuilder()
                                           .withClaimName(workspacesPvcName)
                                           .build();

        Volume volume = new VolumeBuilder().withPersistentVolumeClaim(pvcs)
                                           .withName(workspacesPvcName)
                                           .build();

        String workspacePath = PVC_PROJECT_DIR + workspaceName;

        Container container = new ContainerBuilder().withName(NAME_PREFIX + workspaceName)
                                                    .withImage("busybox")
                                                    .withImagePullPolicy("IfNotPresent")
                                                    .withNewSecurityContext()
                                                        .withPrivileged(false)
                                                    .endSecurityContext()
                                                    .withCommand("rm", "-rf", "/projects/" + workspaceName)
                                                    .withVolumeMounts(vm)
                                                    .build();

        String podName = NAME_PREFIX + workspaceName;
        Pod deleteJob = openShiftClient.pods().inNamespace(projectNamespace)
                                              .createNew()
                                              .withNewMetadata()
                                                  .withName(podName)
                                              .endMetadata()
                                              .withNewSpec()
                                                  .withContainers(container)
                                                  .withVolumes(volume)
                                                  .withRestartPolicy("Never")
                                              .endSpec()
                                              .done();

        boolean completed = true;
        while(!completed) {
            Pod pod = openShiftClient.pods().inNamespace(projectNamespace).withName(podName).get();
            String phase = pod.getStatus().getPhase();
            if (phase.equals("Succeeded")) {

            }
        }
    }

}
