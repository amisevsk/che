package org.eclipse.che.plugin.openshift.client;

import static com.google.common.base.Strings.isNullOrEmpty;

import javax.inject.Named;

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

final class OpenShiftPvcHelper {

    private static final Logger LOG = LoggerFactory.getLogger(OpenShiftPvcHelper.class);

    private static final String POD_PHASE_SUCCEEDED        = "Succeeded";
    private static final String POD_PHASE_FAILED           = "Failed";
    private static final String JOB_IMAGE                  = "busybox";
    private static final String[] MKDIR_WORKSPACE_COMMAND  = new String[] {"mkdir", "-p"};
    private static final String[] RMDIR_WORKSPACE_COMMAND  = new String[] {"rm", "-rf"};

    protected enum Command {REMOVE, MAKE}

    protected static void createJobPod(String workspacesPvcName,
                              String projectNamespace,
                              String workspaceDir,
                              String jobNamePrefix,
                              Command command) {

        VolumeMount vm = new VolumeMountBuilder()
                .withMountPath("/projects")
                .withName(workspacesPvcName)
                .build();

        PersistentVolumeClaimVolumeSource pvcs = new PersistentVolumeClaimVolumeSourceBuilder()
                .withClaimName(workspacesPvcName)
                .build();

        Volume volume = new VolumeBuilder()
                .withPersistentVolumeClaim(pvcs)
                .withName(workspacesPvcName)
                .build();

        String[] jobCommand = getCommand(command, "/projects/" + workspaceDir);

        Container container = new ContainerBuilder().withName(jobNamePrefix + workspaceDir)
                                                    .withImage(JOB_IMAGE)
                                                    .withImagePullPolicy("IfNotPresent")
                                                    .withNewSecurityContext()
                                                        .withPrivileged(false)
                                                    .endSecurityContext()
                                                    .withCommand(jobCommand)
                                                    .withVolumeMounts(vm)
                                                    .build();

        try (OpenShiftClient openShiftClient = new DefaultOpenShiftClient()){
            String podName = jobNamePrefix + workspaceDir;
            openShiftClient.pods().inNamespace(projectNamespace)
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

            boolean completed = false;
            while(!completed) {
                Pod pod = openShiftClient.pods().inNamespace(projectNamespace).withName(podName).get();
                String phase = pod.getStatus().getPhase();
                if (phase.equals(POD_PHASE_SUCCEEDED)) {
                    LOG.info("Pod Succeeded!"); //TODO
                    openShiftClient.resource(pod).delete();
                    completed = true;
                } else if (phase.equals(POD_PHASE_FAILED)) {
                    LOG.error("Pod failed!"); //TODO
                    openShiftClient.resource(pod).delete();
                    completed = true;
                } else {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String[] getCommand(Command commandType, String... args) {
        String[] command = new String[0];
        switch (commandType) {
            case MAKE :
                command = MKDIR_WORKSPACE_COMMAND;
                break;
            case REMOVE :
                command = RMDIR_WORKSPACE_COMMAND;
                break;
        }

        String[] fullCommand = new String[command.length + args.length];

        System.arraycopy(command, 0, fullCommand, 0, command.length);
        System.arraycopy(args, 0, fullCommand, command.length, args.length);
        return fullCommand;
    }
}
