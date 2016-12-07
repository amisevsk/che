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

import com.google.common.collect.ImmutableMap;
import com.openshift.internal.restclient.ResourceFactory;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.Pod;
import com.openshift.internal.restclient.model.Port;
import com.openshift.internal.restclient.model.Service;
import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.IResourceFactory;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.images.DockerImageURI;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IPort;
import com.openshift.restclient.model.IProject;
import com.openshift.restclient.model.IReplicationController;
import com.openshift.restclient.model.IService;
import com.openshift.restclient.model.IServicePort;
import com.openshift.restclient.model.deploy.DeploymentTriggerType;
import org.eclipse.che.plugin.docker.client.json.ContainerCreated;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;
import org.eclipse.che.plugin.docker.client.json.PortBinding;
import org.eclipse.che.plugin.docker.client.params.CreateContainerParams;
import org.eclipse.che.plugin.docker.client.params.RemoveContainerParams;
import org.jboss.dmr.ModelNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;

/**
 * Client for OpenShift API.
 *
 * @author ML
 */
@Singleton
public class OpenShiftConnector {
    private static final Logger LOG = LoggerFactory.getLogger(OpenShiftConnector.class);
    private static final String OPENSHIFT_API_VERSION = "v1";
    private static final String CHE_WORKSPACE_ID_ENV_VAR = "CHE_WORKSPACE_ID";
    private static final String CHE_OPENSHIFT_RESOURCES_PREFIX = "che-ws-";

    /** Prefix used for che server labels */
    private static final String CHE_SERVER_LABEL_PREFIX = "che:server";
    /** Padding to use when converting server label to DNS name */
    private static final String CHE_SERVER_LABEL_PADDING = "0%s0";
    /** Regex to use when matching converted labels -- should match {@link CHE_SERVER_LABEL_PADDING} */
    private static final Pattern CHE_SERVER_LABEL_KEY    = Pattern.compile("^0(.*)0$");

    private static final String OPENSHIFT_SERVICE_TYPE_NODE_PORT = "NodePort";
    public static final String DOCKER_PROTOCOL_PORT_DELIMITER = "/";
    public static final String IMAGE_PULL_POLICY_IFNOTPRESENT = "IfNotPresent";
    public static final String CHE_DEFAULT_EXTERNAL_ADDRESS = "172.17.0.1";
    public static final String CHE_SERVER_INTERNAL_IP_ADB = "172.17.0.5";
    public static final String CHE_CONTAINER_IDENTIFIER_LABEL_KEY = "cheContainerIdentifier";

    private final IClient            openShiftClient;
    private final IResourceFactory   openShiftFactory;
    private final String             cheOpenShiftProjectName;
    private final String             cheOpenShiftServiceAccount;

    public final Map<Integer, String> servicePortNames = ImmutableMap.<Integer, String>builder().
            put(22, "sshd").
            put(4401, "wsagent").
            put(4403, "wsagent-jpda").
            put(4411, "terminal").
            put(8080, "tomcat").
            put(8000, "tomcat-jpda").
            put(9876, "codeserver").build();

    @Inject
    public OpenShiftConnector(@Named("che.openshift.endpoint") String openShiftApiEndpoint,
                              @Named("che.openshift.username") String openShiftUserName,
                              @Named("che.openshift.password") String openShiftUserPassword,
                              @Named("che.openshift.project") String cheOpenShiftProjectName,
                              @Named("che.openshift.serviceaccountname") String cheOpenShiftServiceAccount) {
        this.cheOpenShiftProjectName = cheOpenShiftProjectName;
        this.cheOpenShiftServiceAccount = cheOpenShiftServiceAccount;

        this.openShiftClient = new ClientBuilder(openShiftApiEndpoint)
                .withUserName(openShiftUserName)
                .withPassword(openShiftUserPassword)
                .build();
        this.openShiftFactory = new ResourceFactory(openShiftClient);
    }

    /**
     * @param createContainerParams
     * @return
     * @throws IOException
     */
    public ContainerCreated createContainer(DockerConnector docker, CreateContainerParams createContainerParams) throws IOException {

        String containerName = getNormalizedContainerName(createContainerParams);
        String workspaceID = getCheWorkspaceId(createContainerParams);
        String imageName = createContainerParams.getContainerConfig().getImage();//"mariolet/che-ws-agent";//"172.30.166.244:5000/eclipse-che/che-ws-agent:latest";//
        Set<String> containerExposedPorts = createContainerParams.getContainerConfig().getExposedPorts().keySet();
        Set<String> imageExposedPorts = docker.inspectImage(imageName).getConfig().getExposedPorts().keySet();
        Set<String> exposedPorts = new HashSet<>();
        exposedPorts.addAll(containerExposedPorts);
        exposedPorts.addAll(imageExposedPorts);

        String[] envVariables = createContainerParams.getContainerConfig().getEnv();
        String[] volumes = createContainerParams.getContainerConfig().getHostConfig().getBinds();

        IProject cheProject = getCheProject();

        Map<String, String> additionalLabels = createContainerParams.getContainerConfig().getLabels();
        createOpenShiftService(cheProject, workspaceID, exposedPorts, additionalLabels);
        String deploymentConfigName = createOpenShiftDeploymentConfig(cheProject,
                                                                      workspaceID,
                                                                      imageName,
                                                                      containerName,
                                                                      exposedPorts,
                                                                      envVariables,
                                                                      volumes);

        String containerID = waitAndRetrieveContainerID(cheProject, deploymentConfigName);
        if (containerID == null) {
            throw new RuntimeException("Failed to get the ID of the container running in the OpenShift pod");
        }

        return new ContainerCreated(containerID, null);
    }

    /**
     * @param docker
     * @param container
     * @return
     * @throws IOException
     */
    public ContainerInfo inspectContainer(DockerConnector docker, String container) throws IOException {
        // Proxy to DockerConnector
        ContainerInfo info = docker.inspectContainer(container);
        if (info == null) {
            return null;
        }

        IPod pod = getChePodByContainerId(info.getId());
        String deploymentConfig = pod.getLabels().get("deploymentConfig");
        IService svc = getCheServiceBySelector("deploymentConfig", deploymentConfig);
        Map<String, String> annotations = convertDnsNamesToLabels(svc.getAnnotations());
        Map<String, String> containerLabels = info.getConfig().getLabels();

        Map<String, String> labels = Stream.concat(annotations.entrySet().stream(), containerLabels.entrySet().stream())
                                           .filter(e -> e.getKey().startsWith(CHE_SERVER_LABEL_PREFIX))
                                           .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

        info.getConfig().setLabels(labels);

        LOG.info("Container labels:");
        for (String key: info.getConfig().getLabels().keySet()) {
            String value = info.getConfig().getLabels().get(key);

            LOG.info("- " + key + "=" + value);
        }
        // Ignore portMapping for now: info.getNetworkSettings().setPortMapping();
        // replacePortMapping(info)
        replaceNetworkSettings(info);

        return info;
    }

    public void removeContainer(final RemoveContainerParams params) throws IOException {
        String containerId = params.getContainer();
        boolean useForce = params.isForce();
        boolean removeVolumes = params.isRemoveVolumes();

        IPod pod = getChePodByContainerId(containerId);

        String deploymentConfig = pod.getLabels().get("deploymentConfig");
        String replicationController = pod.getLabels().get("deployment");

        IDeploymentConfig dc = getCheDeploymentDescriptor(deploymentConfig);
        IReplicationController rc = getCheReplicationController(replicationController);
        IService svc = getCheServiceBySelector("deploymentConfig", deploymentConfig);

        LOG.info("Removing OpenShift Service " + svc.getName());
        openShiftClient.delete(svc);

        LOG.info("Removing OpenShift DeploymentConfiguration " + dc.getName());
        openShiftClient.delete(dc);

        LOG.info("Removing OpenShift ReplicationController " + rc.getName());
        openShiftClient.delete(rc);

        LOG.info("Removing OpenShift Pod " + pod.getName());
        openShiftClient.delete(pod);
    }

    private IService getCheServiceBySelector(String selectorKey, String selectorValue) {
        List<IService> svcs = openShiftClient.list(ResourceKind.SERVICE, cheOpenShiftProjectName, Collections.emptyMap());
        IService svc = svcs.stream().filter(s -> s.getSelector().get(selectorKey).equals(selectorValue)).findAny().orElse(null);

        if (svc == null) {
            LOG.warn("No Service with selector " + selectorKey + "=" + selectorValue + " could be found.");
        }

        return svc;
    }

    private IReplicationController getCheReplicationController(String replicationController) throws IOException {
        IReplicationController rc = openShiftClient.get(ResourceKind.REPLICATION_CONTROLLER, replicationController, cheOpenShiftProjectName);
        if (rc == null) {
            LOG.warn("No ReplicationController with name " + replicationController + " could be found.");
        }
        return rc;
    }

    private IDeploymentConfig getCheDeploymentDescriptor(String deploymentConfig) throws IOException {
        IDeploymentConfig dc = openShiftClient.get(ResourceKind.DEPLOYMENT_CONFIG, deploymentConfig, cheOpenShiftProjectName);
        if (dc == null) {
            LOG.warn("DeploymentConfig with name " + deploymentConfig + " could be found");
        }
        return dc;
    }

    private IPod getChePodByContainerId(String containerId) throws IOException {
        Map<String, String> podLabels = new HashMap<>();
        String labelKey = CHE_CONTAINER_IDENTIFIER_LABEL_KEY;
        podLabels.put(labelKey, containerId.substring(0,12));

        List<IPod> pods = openShiftClient.list(ResourceKind.POD, cheOpenShiftProjectName, podLabels);

        if (pods.size() == 0 ) {
            LOG.error("An OpenShift Pod with label " + labelKey + "=" + containerId+" could not be found");
            throw new IOException("An OpenShift Pod with label " + labelKey + "=" + containerId+" could not be found");
        }

        if (pods.size() > 1 ) {
            LOG.error("There are " + pods.size() + " pod with label " + labelKey + "=" + containerId+" (just one was expeced)");
            throw  new  IOException("There are " + pods.size() + " pod with label " + labelKey + "=" + containerId+" (just one was expeced)");
        }

        return pods.get(0);
    }

    private String getNormalizedContainerName(CreateContainerParams createContainerParams) {
        String containerName = createContainerParams.getContainerName();
        // The name of a container in Kubernetes should be a
        // valid hostname as specified by RFC 1123 (i.e. max length
        // of 63 chars and no underscores)
        return containerName.substring(9).replace('_', '-');
    }

    protected String getCheWorkspaceId(CreateContainerParams createContainerParams) {
        Stream<String> env = Arrays.stream(createContainerParams.getContainerConfig().getEnv());
        String workspaceID = env.filter(v -> v.startsWith(CHE_WORKSPACE_ID_ENV_VAR) && v.contains("=")).
                                 map(v -> v.split("=",2)[1]).
                                 findFirst().
                                 orElse("");
        return workspaceID.replaceFirst("workspace","");
    }

    private IProject getCheProject() throws IOException {
        List<IProject> list = openShiftClient.list(ResourceKind.PROJECT);
        IProject cheProject = list.stream()
                                   .filter(p -> p.getName().equals(cheOpenShiftProjectName))
                                   .findFirst().orElse(null);
        if (cheProject == null) {
            LOG.error("OpenShift project " + cheOpenShiftProjectName + " not found");
            throw new IOException("OpenShift project " + cheOpenShiftProjectName + " not found");
        }
        return cheProject;
    }

    private void createOpenShiftService(IProject cheProject,
                                        String workspaceID,
                                        Set<String> exposedPorts,
                                        Map<String, String> labels) {
        IService service = openShiftFactory.create(OPENSHIFT_API_VERSION, ResourceKind.SERVICE);
        ((Service) service).setNamespace(cheProject.getNamespace());
        ((Service) service).setName(CHE_OPENSHIFT_RESOURCES_PREFIX + workspaceID);
        service.setType(OPENSHIFT_SERVICE_TYPE_NODE_PORT);

        List<IServicePort> openShiftPorts = getServicePortsFrom(exposedPorts);
        service.setPorts(openShiftPorts);

        service.setSelector("deploymentConfig", (CHE_OPENSHIFT_RESOURCES_PREFIX + workspaceID));

        Map<String,String> sanitizedLabels = convertLabelsToDnsNames(labels);
        for (Map.Entry<String, String> entry : sanitizedLabels.entrySet()) {
            if (entry.getValue() != null) {
                service.setAnnotation(entry.getKey(), entry.getValue());
            }
        }
        openShiftClient.create(service);
        LOG.info(String.format("OpenShift service %s created", service.getName()));
    }

    private String createOpenShiftDeploymentConfig(IProject cheProject,
                                                   String workspaceID,
                                                   String imageName,
                                                   String sanitizedContaninerName,
                                                   Set<String> exposedPorts,
                                                   String[] envVariables,
                                                   String[] volumes) {
        String dcName = CHE_OPENSHIFT_RESOURCES_PREFIX + workspaceID;
        LOG.info(String.format("Creating OpenShift deploymentConfig %s", dcName));
        IDeploymentConfig dc = openShiftFactory.create(OPENSHIFT_API_VERSION, ResourceKind.DEPLOYMENT_CONFIG);
        ((DeploymentConfig) dc).setName(dcName);
        ((DeploymentConfig) dc).setNamespace(cheProject.getName());
//        ((DeploymentConfig) dc).getNode().get("spec").get("template").get("spec").get("dnsPolicy").set("Default");
        ((DeploymentConfig)dc).getNode().get("spec").get("template").get("spec").get("securityContext").get("runAsUser").set("1000");

        dc.setReplicas(1);
        dc.setReplicaSelector("deploymentConfig", dcName);
        dc.setServiceAccountName(this.cheOpenShiftServiceAccount);

        LOG.info(String.format("Adding container %s to OpenShift deploymentConfig %s", sanitizedContaninerName, dcName));
        addContainer(dc, workspaceID, imageName, sanitizedContaninerName, exposedPorts, envVariables, volumes);

        dc.addTrigger(DeploymentTriggerType.CONFIG_CHANGE);
        openShiftClient.create(dc);
        LOG.info(String.format("OpenShift deploymentConfig %s created", dcName));
        return dc.getName();
    }

    private void addContainer(IDeploymentConfig dc, String workspaceID, String imageName, String containerName, Set<String> exposedPorts, String[] envVariables, String[] volumes) {
        Set<IPort> containerPorts = getContainerPortsFrom(exposedPorts);
        Map<String, String> containerEnv = getContainerEnvFrom(envVariables);
        dc.addContainer(containerName,
                new DockerImageURI(imageName),
                containerPorts,
                containerEnv,
                Collections.emptyList());
        dc.getContainer(containerName).setImagePullPolicy(IMAGE_PULL_POLICY_IFNOTPRESENT);

        ModelNode dcFirstContainer = ((DeploymentConfig)dc).getNode().get("spec").get("template").get("spec").get("containers").get(0);

        // Run as root user
        dcFirstContainer.get("securityContext").get("privileged").set(true);
        dcFirstContainer.get("securityContext").get("runAsUser").set("1000");

        // LivenessProbe
        dcFirstContainer.get("livenessProbe").get("tcpSocket").get("port").set(4401);
        dcFirstContainer.get("livenessProbe").get("initialDelaySeconds").set(120);
        dcFirstContainer.get("livenessProbe").get("timeoutSeconds").set(1);

        // Add volumes
        for (String volume : volumes) {
            String hostPath = volume.split(":",3)[0];
            String mountPath = volume.split(":",3)[1];
            String volumName = getVolumeName(volume);
            addVolume((DeploymentConfig)dc, "ws-" + workspaceID + "-" + volumName, hostPath, mountPath);
        }
    }

    private String getVolumeName(String volume) {
        if ( volume.contains("ws-agent") ) {
            return "wsagent-lib";
        }

        if ( volume.contains("terminal") ) {
            return "terminal";
        }

        if ( volume.contains("workspaces") ) {
            return "project";
        }

        return "unknown-volume";
    }

    private void addVolume(DeploymentConfig dc, String name, String hostPath, String mountPath) {
        ModelNode dcVolume = dc.getNode().get("spec").get("template").get("spec").get("volumes").add();

        //ModelNode dcFirstVolume = dc.getNode().get("spec").get("template").get("spec").get("volumes").get(0);
        dcVolume.get("name").set(name);
        dcVolume.get("hostPath").get("path").set(hostPath);

        ModelNode dcFirstContainer = dc.getNode().get("spec").get("template").get("spec").get("containers").get(0);
        ModelNode volumeMount = dcFirstContainer.get("volumeMounts").add();
        //ModelNode firstVolumeMount = dcFirstContainer.get("volumeMounts").get(0);
        volumeMount.get("name").set(name);
        volumeMount.get("mountPath").set(mountPath);
    }

    private String waitAndRetrieveContainerID(IProject cheproject, String deploymentConfigName) {
        String deployerLabelKey = "openshift.io/deployer-pod-for.name";
        for (int i = 0; i < 120; i++) {
            try {
                Thread.sleep(1000);                 //1000 milliseconds is one second.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            List<IPod> pods = openShiftClient.list(ResourceKind.POD, cheproject.getNamespace(), Collections.emptyMap());
            long deployPodNum = pods.stream().filter(p -> p.getLabels().keySet().contains(deployerLabelKey)).count();

            if (deployPodNum == 0) {
                LOG.info("Pod has been deployed.");
                for (IPod pod : pods) {
                    if (pod.getLabels().get("deploymentConfig").equals(deploymentConfigName)) {
                        ModelNode containerID = ((Pod) pod).getNode().get("status").get("containerStatuses").get(0).get("containerID");
                        pod.addLabel(CHE_CONTAINER_IDENTIFIER_LABEL_KEY, containerID.toString().substring(10,22));
                        openShiftClient.update(pod);
                        return containerID.toString().substring(10, 74);
                    }
                }
            }
        }
        return null;
    }

    public List<IServicePort> getServicePortsFrom(Set<String> exposedPorts) {
        List<IServicePort> servicePorts = new ArrayList<>();
        for (String exposedPort: exposedPorts){
            String[] portAndProtocol = exposedPort.split(DOCKER_PROTOCOL_PORT_DELIMITER,2);

            String port = portAndProtocol[0];
            String protocol = portAndProtocol[1];

            int portNumber = Integer.parseInt(port);
            String portName = isNullOrEmpty(servicePortNames.get(portNumber)) ?
                    exposedPort.replace("/","-") : servicePortNames.get(portNumber);
            int targetPortNumber = portNumber;

            IServicePort servicePort = OpenShiftPortFactory.createServicePort(
                    portName,
                    protocol,
                    portNumber,
                    targetPortNumber);
            servicePorts.add(servicePort);
        }
        return servicePorts;
    }

    public Set<IPort> getContainerPortsFrom(Set<String> exposedPorts) {
        Set<IPort> containerPorts = new HashSet<>();
        for (String exposedPort: exposedPorts){
            String[] portAndProtocol = exposedPort.split(DOCKER_PROTOCOL_PORT_DELIMITER,2);

            String port = portAndProtocol[0];
            String protocol = portAndProtocol[1].toUpperCase();

            int portNumber = Integer.parseInt(port);
            String portName = isNullOrEmpty(servicePortNames.get(portNumber)) ?
                    exposedPort.replace("/","-") : servicePortNames.get(portNumber);

            Port containerPort = new Port(new ModelNode());
            containerPort.setName(portName);
            containerPort.setProtocol(protocol);
            containerPort.setContainerPort(portNumber);
            containerPorts.add(containerPort);
        }
        return containerPorts;
    }

    public Map<String, String> getContainerEnvFrom(String[] envVariables){
        LOG.info("Container environment variables:");
        Map<String, String> env = new HashMap<>();
        for (String envVariable : envVariables) {
            String[] nameAndValue = envVariable.split("=",2);
            String varName = nameAndValue[0];
            String varValue = nameAndValue[1];
            env.put(varName, varValue);
            LOG.info("- " + varName + "=" + varValue);
        }
        return env;
    }

    private void replaceNetworkSettings(ContainerInfo info) throws IOException {

        if (info.getNetworkSettings() == null) {
            return;
        }

        IProject cheproject = getCheProject();

        IService service = getCheWorkspaceService(cheproject);
        Map<String, List<PortBinding>> networkSettingsPorts = getCheServicePorts((Service) service);

        info.getNetworkSettings().setPorts(networkSettingsPorts);
    }

    private IService getCheWorkspaceService(IProject cheproject) throws IOException {
        List<IService> services = openShiftClient.list(ResourceKind.SERVICE, cheproject.getNamespace(), Collections.emptyMap());
        // TODO: improve how the service is found (e.g. using a label with the workspaceid)
        IService service = services.stream().filter(s -> s.getName().startsWith(CHE_OPENSHIFT_RESOURCES_PREFIX)).findFirst().orElse(null);
        if (service == null) {
            LOG.error("No service with prefix " + CHE_OPENSHIFT_RESOURCES_PREFIX +" found");
            throw new IOException("No service with prefix " + CHE_OPENSHIFT_RESOURCES_PREFIX +" found");
        }
        return service;
    }

    private Map<String, List<PortBinding>> getCheServicePorts(Service service) {
        Map<String, List<PortBinding>> networkSettingsPorts = new HashMap<>();
        List<ModelNode> servicePorts = service.getNode().get("spec").get("ports").asList();
        LOG.info("Retrieving " + servicePorts.size() + " ports exposed by service " + service.getName());
        for (ModelNode servicePort : servicePorts) {
            String protocol = servicePort.get("protocol").asString();
            String targetPort = servicePort.get("targetPort").asString();
            String nodePort = servicePort.get("nodePort").asString();
            String portName = servicePort.get("name").asString();

            LOG.info("Port: " + targetPort + DOCKER_PROTOCOL_PORT_DELIMITER + protocol + " (" + portName + ")");

            networkSettingsPorts.put(targetPort + DOCKER_PROTOCOL_PORT_DELIMITER + protocol.toLowerCase(),
                    Collections.singletonList(new PortBinding().withHostIp(CHE_DEFAULT_EXTERNAL_ADDRESS).withHostPort(nodePort)));
        }
        return networkSettingsPorts;
    }

    /**
     * Converts a map of labels to match RFC 1123 (valid DNS name). Names are limited
     * to alphanumeric characters, {@code '.'} and {@code '-'}, and must start with an
     * alphanumeric character.
     * @param labels Map of labels to convert
     * @return Map of labels converted to DNS Names
     * @see OpenShiftConnector#convertDnsNamesToLabels(Map)
     */
    private Map<String, String> convertLabelsToDnsNames(Map<String, String> labels) {
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null) {
                continue;
            }
            // TODO: Check for special characters in key & value
            // Convert keys: e.g. "che:server:4401/tcp:ref" -> "che.server.4401-tcp.ref"
            key = key.replaceAll(":", ".").replaceAll("/", "-");
            // Convert values: e.g. "/api" -> ".api" -- note values may include '-'
            value = value.replaceAll("/", ".");

            // Add padding since DNS names must start and end with alphanumeric characters
            names.put(String.format(CHE_SERVER_LABEL_PADDING, key),
                      String.format(CHE_SERVER_LABEL_PADDING, value));
        }
        return names;
    }

    /**
     * Undoes the label to DNS name conversion done by {@link OpenShiftConnector#convertLabelsToDnsNames(Map)}
     *
     * @param labels Map of DNS names
     * @return Map of unconverted labels
     * @see OpenShiftConnector#convertLabelsToDnsNames(Map)
     */
    private Map<String, String> convertDnsNamesToLabels(Map<String, String> names) {
        Map<String, String> labels = new HashMap<>();
        for (Map.Entry<String, String> entry: names.entrySet()){
            String key = entry.getKey();
            String value = entry.getValue();

            // Remove padding
            Matcher keyMatcher   = CHE_SERVER_LABEL_KEY.matcher(key);
            Matcher valueMatcher = CHE_SERVER_LABEL_KEY.matcher(value);
            if (!keyMatcher.matches() || !valueMatcher.matches()) {
                continue;
            }
            key = keyMatcher.group(1);
            value = valueMatcher.group(1);

            // Convert key: e.g. "che.server.4401-tcp.ref" -> "che:server:4401/tcp:ref"
            key = key.replaceAll("\\.", ":").replaceAll("-", "/");
            // Convert value: e.g. Convert values: e.g. ".api" -> "/api"
            value = value.replaceAll("\\.", "/");

            labels.put(key, value);
        }
        return labels;
    }
}