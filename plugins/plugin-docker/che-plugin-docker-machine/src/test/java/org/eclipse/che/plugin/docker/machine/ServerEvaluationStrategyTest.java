package org.eclipse.che.plugin.docker.machine;

import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerPropertiesImpl;
import org.eclipse.che.plugin.docker.client.json.ContainerConfig;
import org.eclipse.che.plugin.docker.client.json.ContainerInfo;
import org.eclipse.che.plugin.docker.client.json.NetworkSettings;
import org.eclipse.che.plugin.docker.client.json.PortBinding;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

@Listeners(MockitoTestNGListener.class)
public class ServerEvaluationStrategyTest {

    private static final String CHE_DOCKER_IP            = "container-host.com";
    private static final String CHE_DOCKER_IP_EXTERNAL   = "container-host-ext.com";
    private static final String ALL_IP_ADDRESS           = "0.0.0.0";
    private static final String CONTAINERINFO_GATEWAY    = "172.17.0.1";
    private static final String CONTAINERINFO_IP_ADDRESS = "172.17.0.200";
    private static final String DEFAULT_HOSTNAME         = "localhost";

    @Mock
    private ContainerInfo   containerInfo;
    @Mock
    private MachineConfig   machineConfig;
    @Mock
    private ContainerConfig containerConfig;
    @Mock
    private NetworkSettings networkSettings;

    private ServerEvaluationStrategy strategy;

    private Map<String, ServerConfImpl> serverConfs;

    private Map<String, List<PortBinding>> ports;

    @BeforeMethod
    public void setUp() {

        serverConfs = new HashMap<>();
        serverConfs.put("4301/tcp", new ServerConfImpl("sysServer1-tcp", "4301/tcp", "http", "/some/path1"));
        serverConfs.put("4305/udp", new ServerConfImpl("devSysServer1-udp", "4305/udp", null, "some/path4"));

        ports = new HashMap<>();
        ports.put("4301/tcp", Collections.singletonList(new PortBinding().withHostIp(ALL_IP_ADDRESS )
                                                                .withHostPort("32100")));
        ports.put("4305/udp", Collections.singletonList(new PortBinding().withHostIp(ALL_IP_ADDRESS )
                                                                .withHostPort("32103")));

        when(containerInfo.getNetworkSettings()).thenReturn(networkSettings);
        when(networkSettings.getGateway()).thenReturn(CONTAINERINFO_GATEWAY);
        when(networkSettings.getIpAddress()).thenReturn(CONTAINERINFO_IP_ADDRESS);
        when(networkSettings.getPorts()).thenReturn(ports);
        when(containerInfo.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getLabels()).thenReturn(Collections.emptyMap());
    }

    @Test
    public void defaultStrategyShouldUseInternalIpPropertyToOverrideContainerInfo() throws Exception {
        // given
        strategy = new DefaultServerEvaluationStrategy(CHE_DOCKER_IP, null);

        final Map<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                       "http",
                                                       CONTAINERINFO_GATEWAY + ":32100",
                                                       "http://" + CONTAINERINFO_GATEWAY + ":32100/some/path1",
                                                       new ServerPropertiesImpl("/some/path1",
                                                                                CHE_DOCKER_IP + ":32100",
                                                                                "http://" + CHE_DOCKER_IP + ":32100/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                       null,
                                                       CONTAINERINFO_GATEWAY  + ":32103",
                                                       null,
                                                       new ServerPropertiesImpl("some/path4",
                                                                                CHE_DOCKER_IP + ":32103",
                                                                                null)));

        // when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    null,
                                                                    serverConfs);

        // then
        assertEquals(servers, expectedServers);
    }

    @Test
    public void defaultStrategyShouldSetExternalAddressAsInternalAddressIfContainerExternalHostnameIsNull() throws Exception {
        // given

        strategy = new DefaultServerEvaluationStrategy(null, null);

        final Map<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                       "http",
                                                       CONTAINERINFO_GATEWAY  + ":32100",
                                                       "http://" + CONTAINERINFO_GATEWAY  + ":32100/some/path1",
                                                       new ServerPropertiesImpl("/some/path1",
                                                                                CONTAINERINFO_GATEWAY  + ":32100",
                                                                                "http://" + CONTAINERINFO_GATEWAY  + ":32100/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                       null,
                                                       CONTAINERINFO_GATEWAY  + ":32103",
                                                       null,
                                                       new ServerPropertiesImpl("some/path4",
                                                                                CONTAINERINFO_GATEWAY  + ":32103",
                                                                                null)));

        // when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    null,
                                                                    serverConfs);

        // then
        assertEquals(servers, expectedServers);
    }

    @Test
    public void defaultStrategyShouldSetExternalAddressDistinctFromInternalWhenExternalHostnameIsNotNull() throws Exception {
        // given
        strategy = new DefaultServerEvaluationStrategy(null, CHE_DOCKER_IP_EXTERNAL);

        final Map<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                              "http",
                                                              CHE_DOCKER_IP_EXTERNAL  + ":32100",
                                                              "http://" + CHE_DOCKER_IP_EXTERNAL  + ":32100/some/path1",
                                                              new ServerPropertiesImpl("/some/path1",
                                                                                              CONTAINERINFO_GATEWAY + ":32100",
                                                                                              "http://" + CONTAINERINFO_GATEWAY + ":32100/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                              null,
                                                              CHE_DOCKER_IP_EXTERNAL  + ":32103",
                                                              null,
                                                              new ServerPropertiesImpl("some/path4",
                                                                                              CONTAINERINFO_GATEWAY + ":32103",
                                                                                              null)));

        // when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    null,
                                                                    serverConfs);

        // then
        assertEquals(servers, expectedServers);
    }


    @Test
    public void localDockerStrategyShouldUseExposedPortsWhenPossible() throws Exception {
        //given
        strategy = new LocalDockerServerEvaluationStrategy(null, null);

        final HashMap<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                       "http",
                                                       CONTAINERINFO_GATEWAY + ":32100",
                                                       "http://" + CONTAINERINFO_GATEWAY + ":32100/some/path1",
                                                       new ServerPropertiesImpl("/some/path1",
                                                                                CONTAINERINFO_IP_ADDRESS + ":4301",
                                                                                "http://" + CONTAINERINFO_IP_ADDRESS + ":4301/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                       null,
                                                       CONTAINERINFO_GATEWAY + ":32103",
                                                       null,
                                                       new ServerPropertiesImpl("some/path4",
                                                                                CONTAINERINFO_IP_ADDRESS + ":4305",
                                                                                null)));

        //when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    null,
                                                                    serverConfs);

        //then
        assertEquals(servers, expectedServers, "Expected strategy to use internal container address and ports");
    }

    @Test
    public void localDockerStrategyShouldStillUseExternalAddressWithInternalAddress() throws Exception {
        //given
        strategy = new LocalDockerServerEvaluationStrategy(null, CHE_DOCKER_IP_EXTERNAL);

        final HashMap<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                       "http",
                                                       CHE_DOCKER_IP_EXTERNAL  + ":32100",
                                                       "http://" + CHE_DOCKER_IP_EXTERNAL  + ":32100/some/path1",
                                                       new ServerPropertiesImpl("/some/path1",
                                                                                CONTAINERINFO_IP_ADDRESS + ":4301",
                                                                                "http://" + CONTAINERINFO_IP_ADDRESS + ":4301/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                       null,
                                                       CHE_DOCKER_IP_EXTERNAL + ":32103",
                                                       null,
                                                       new ServerPropertiesImpl("some/path4",
                                                                                CONTAINERINFO_IP_ADDRESS + ":4305",
                                                                                null)));

        //when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    null,
                                                                    serverConfs);

        //then
        assertEquals(servers, expectedServers, "Expected strategy to use external address property to override");
    }

    @Test
    public void localDockerStrategyShouldUseInternalHostnameWhenContainerInfoIsUnavailable() throws Exception {
        //given
        strategy = new LocalDockerServerEvaluationStrategy(null, null);

        when(networkSettings.getIpAddress()).thenReturn(null);
        when(networkSettings.getGateway()).thenReturn(null);

        final HashMap<String, ServerImpl> expectedServers = new HashMap<>();
        expectedServers.put("4301/tcp", new ServerImpl("sysServer1-tcp",
                                                       "http",
                                                       DEFAULT_HOSTNAME + ":32100",
                                                       "http://" + DEFAULT_HOSTNAME + ":32100/some/path1",
                                                       new ServerPropertiesImpl("/some/path1",
                                                                                DEFAULT_HOSTNAME + ":32100",
                                                                                "http://" + DEFAULT_HOSTNAME + ":32100/some/path1")));
        expectedServers.put("4305/udp", new ServerImpl("devSysServer1-udp",
                                                       null,
                                                       DEFAULT_HOSTNAME + ":32103",
                                                       null,
                                                       new ServerPropertiesImpl("some/path4",
                                                                                DEFAULT_HOSTNAME + ":32103",
                                                                                null)));

        //when
        final Map<String, ServerImpl> servers = strategy.getServers(containerInfo,
                                                                    DEFAULT_HOSTNAME,
                                                                    serverConfs);

        //then
        assertEquals(servers, expectedServers, "Expected servers to fall back to provided hostname when ContainerInfo is not available");
    }
}
