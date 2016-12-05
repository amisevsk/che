/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *   Red Hat Inc.  - refactor getServers() method
 *******************************************************************************/
package org.eclipse.che.plugin.docker.machine;

import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.MachineRuntimeInfo;
import org.eclipse.che.api.core.model.machine.ServerConf;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerImpl;
import org.eclipse.che.plugin.docker.client.json.ContainerConfig;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;
import org.eclipse.che.plugin.docker.client.json.ContainerState;
import org.eclipse.che.plugin.docker.client.json.HostConfig;
import org.eclipse.che.plugin.docker.client.json.NetworkSettings;
import org.eclipse.che.plugin.docker.client.json.PortBinding;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Docker implementation of {@link MachineRuntimeInfo}
 *
 * @author andrew00x
 * @author Alexander Garagatyi
 * @author Roman Iuvshyn
 */
public class DockerInstanceRuntimeInfo implements MachineRuntimeInfo {
    /**
     * Env variable that points to root folder of projects in dev machine
     */
    public static final String PROJECTS_ROOT_VARIABLE = "CHE_PROJECTS_ROOT";

    /**
     * Env variable for jvm settings
     */
    public static final String JAVA_OPTS_VARIABLE = "JAVA_OPTS";

    /**
     * Env variable for dev machine that contains url of Che API
     */
    public static final String API_ENDPOINT_URL_VARIABLE = "CHE_API";

    /**
     * Environment variable that will be setup in developer machine will contain ID of a workspace for which this machine has been created
     */
    public static final String CHE_WORKSPACE_ID = "CHE_WORKSPACE_ID";

    /**
     * Default HOSTNAME that will be added in all docker containers that are started. This host will container the Docker host's ip
     * reachable inside the container.
     */
    public static final String CHE_HOST = "che-host";

    /**
     * Environment variable that will be setup in developer machine and contains user token.
     */
    public static final String USER_TOKEN = "USER_TOKEN";

    private final ContainerInfo               info;
    private final Map<String, ServerConfImpl> serversConf;
    private final ServerEvaluationStrategyProvider provider;

    @Inject
    public DockerInstanceRuntimeInfo(@Assisted ContainerInfo containerInfo,
                                     @Assisted MachineConfig machineConfig,
                                     ServerEvaluationStrategyProvider provider,
                                     @Named("machine.docker.dev_machine.machine_servers") Set<ServerConf> devMachineSystemServers,
                                     @Named("machine.docker.machine_servers") Set<ServerConf> allMachinesSystemServers) {
        this.info = containerInfo;

        Stream<ServerConf> confStream = Stream.concat(machineConfig.getServers().stream(), allMachinesSystemServers.stream());
        if (machineConfig.isDev()) {
            confStream = Stream.concat(confStream, devMachineSystemServers.stream());
        }
        // convert list to map for quick search and normalize port - add /tcp if missing
        this.serversConf = confStream.collect(toMap(srvConf -> srvConf.getPort().contains("/") ?
                                                               srvConf.getPort() :
                                                               srvConf.getPort() + "/tcp",
                                                    ServerConfImpl::new));

        this.provider = provider;
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> md = new LinkedHashMap<>();
        md.put("id", info.getId());
        md.put("created", info.getCreated());
        md.put("image", info.getImage());
        md.put("path", info.getPath());
        md.put("appArmorProfile", info.getAppArmorProfile());
        md.put("driver", info.getDriver());
        md.put("execDriver", info.getExecDriver());
        md.put("hostnamePath", info.getHostnamePath());
        md.put("hostsPath", info.getHostsPath());
        md.put("mountLabel", info.getMountLabel());
        md.put("name", info.getName());
        md.put("processLabel", info.getProcessLabel());
        md.put("volumesRW", String.valueOf(info.getVolumesRW()));
        md.put("resolvConfPath", info.getResolvConfPath());
        md.put("args", Arrays.toString(info.getArgs()));
        md.put("volumes", String.valueOf(info.getVolumes()));
        md.put("restartCount", String.valueOf(info.getRestartCount()));
        md.put("logPath", String.valueOf(info.getLogPath()));
        ContainerConfig config = info.getConfig();
        if (config != null) {
            md.put("config.domainName", config.getDomainName());
            md.put("config.hostname", config.getHostname());
            md.put("config.image", config.getImage());
            md.put("config.user", config.getUser());
            md.put("config.workingDir", config.getWorkingDir());
            md.put("config.cmd", Arrays.toString(config.getCmd()));
            md.put("config.volumes", String.valueOf(config.getVolumes()));
            md.put("config.cpuset", config.getCpuset());
            md.put("config.entrypoint", Arrays.toString(config.getEntrypoint()));
            md.put("config.exposedPorts", String.valueOf(config.getExposedPorts()));
            md.put("config.macAddress", config.getMacAddress());
            md.put("config.securityOpts", Arrays.toString(config.getSecurityOpts()));
            md.put("config.cpuShares", Integer.toString(config.getCpuShares()));
            md.put("config.env", Arrays.toString(config.getEnv()));
            md.put("config.attachStderr", Boolean.toString(config.isAttachStderr()));
            md.put("config.attachStdin", Boolean.toString(config.isAttachStdin()));
            md.put("config.attachStdout", Boolean.toString(config.isAttachStdout()));
            md.put("config.networkDisabled", Boolean.toString(config.isNetworkDisabled()));
            md.put("config.openStdin", Boolean.toString(config.isOpenStdin()));
            md.put("config.stdinOnce", Boolean.toString(config.isStdinOnce()));
            md.put("config.tty", Boolean.toString(config.isTty()));
            md.put("config.labels", String.valueOf(config.getLabels()));
        }
        ContainerState state = info.getState();
        if (state != null) {
            md.put("state.startedAt", state.getStartedAt());
            md.put("state.exitCode", Integer.toString(state.getExitCode()));
            md.put("state.pid", Integer.toString(state.getPid()));
            md.put("state.running", Boolean.toString(state.isRunning()));
            md.put("state.finishedAt", state.getFinishedAt());
            md.put("state.paused", Boolean.toString(state.isPaused()));
            md.put("state.restarting", Boolean.toString(state.isRestarting()));
            md.put("state.dead", String.valueOf(state.isDead()));
            md.put("state.OOMKilled", String.valueOf(state.isOOMKilled()));
            md.put("state.error", state.getError());
        }
        NetworkSettings networkSettings = info.getNetworkSettings();
        if (networkSettings != null) {
            md.put("network.bridge", networkSettings.getBridge());
            md.put("network.gateway", networkSettings.getGateway());
            md.put("network.ipAddress", networkSettings.getIpAddress());
            md.put("network.ipPrefixLen", Integer.toString(networkSettings.getIpPrefixLen()));
            md.put("network.portMappings", Arrays.toString(networkSettings.getPortMapping()));
            md.put("network.macAddress", networkSettings.getMacAddress());
            md.put("network.ports", String.valueOf(networkSettings.getPorts()));
            md.put("network.linkLocalIPv6PrefixLen", String.valueOf(networkSettings.getLinkLocalIPv6PrefixLen()));
            md.put("network.globalIPv6Address", networkSettings.getGlobalIPv6Address());
            md.put("network.globalIPv6PrefixLen", String.valueOf(networkSettings.getGlobalIPv6PrefixLen()));
            md.put("network.iPv6Gateway", networkSettings.getIpV6Gateway());
            md.put("network.linkLocalIPv6Address", networkSettings.getLinkLocalIPv6Address());
        }
        HostConfig hostConfig = info.getHostConfig();
        if (hostConfig != null) {
            md.put("hostConfig.cgroupParent", hostConfig.getCgroupParent());
            md.put("hostConfig.containerIDFile", hostConfig.getContainerIDFile());
            md.put("hostConfig.cpusetCpus", hostConfig.getCpusetCpus());
            md.put("hostConfig.ipcMode", hostConfig.getIpcMode());
            md.put("hostConfig.memory", Long.toString(hostConfig.getMemory()));
            md.put("hostConfig.networkMode", hostConfig.getNetworkMode());
            md.put("hostConfig.pidMode", hostConfig.getPidMode());
            md.put("hostConfig.binds", Arrays.toString(hostConfig.getBinds()));
            md.put("hostConfig.capAdd", Arrays.toString(hostConfig.getCapAdd()));
            md.put("hostConfig.capDrop", Arrays.toString(hostConfig.getCapDrop()));
            md.put("hostConfig.cpuShares", String.valueOf(hostConfig.getCpuShares()));
            md.put("hostConfig.devices", Arrays.toString(hostConfig.getDevices()));
            md.put("hostConfig.dns", Arrays.toString(hostConfig.getDns()));
            md.put("hostConfig.dnsSearch", Arrays.toString(hostConfig.getDnsSearch()));
            md.put("hostConfig.extraHosts", Arrays.toString(hostConfig.getExtraHosts()));
            md.put("hostConfig.links", Arrays.toString(hostConfig.getLinks()));
            md.put("hostConfig.logConfig", String.valueOf(hostConfig.getLogConfig()));
            md.put("hostConfig.lxcConf", Arrays.toString(hostConfig.getLxcConf()));
            md.put("hostConfig.memorySwap", String.valueOf(hostConfig.getMemorySwap()));
            md.put("hostConfig.portBindings", String.valueOf(hostConfig.getPortBindings()));
            md.put("hostConfig.restartPolicy", String.valueOf(hostConfig.getRestartPolicy()));
            md.put("hostConfig.ulimits", Arrays.toString(hostConfig.getUlimits()));
            md.put("hostConfig.volumesFrom", Arrays.toString(hostConfig.getVolumesFrom()));
            md.put("hostConfig.pidsLimit", Long.toString(hostConfig.getPidsLimit()));
        }

        return md;
    }

    @Override
    public Map<String, String> getEnvVariables() {
        final Map<String, String> envVariables = new HashMap<>();
        if (info.getConfig() != null && info.getConfig().getEnv() != null) {
            for (String envVariable : info.getConfig().getEnv()) {
                final String[] variableNameValue = envVariable.split("=", 2);
                envVariables.put(variableNameValue[0], variableNameValue[1]);
            }
        }
        return envVariables;
    }

    @Override
    public String projectsRoot() {
        return getEnvVariables().get(PROJECTS_ROOT_VARIABLE);
    }

    @Override
    public Map<String, ServerImpl> getServers() {
        ServerEvaluationStrategy strategy = provider.getStrategy(info, serversConf);
        Map<String, List<PortBinding>> ports;
        if (info.getNetworkSettings() != null && info.getNetworkSettings().getPorts() != null) {
            ports = info.getNetworkSettings().getPorts();
        } else {
            return Collections.emptyMap();
        }

        Map<String, ServerImpl> servers = new LinkedHashMap<>();

        for (String portProtocol : ports.keySet()) {
            servers.put(portProtocol, strategy.getServer(portProtocol));
        }

        return servers;
    }
}
