/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common.openvpn;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.openvpn.KeepAliveMode;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.TestPubSubService;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class OpenVpnClientTest {
    private final Logger logger = LoggerFactory.getLogger(OpenVpnClientTest.class);

    private final static String START_INSTANCE_SCRIPT = "START_INSTANCE_SCRIPT";
    private final static String KILL_PROCESS_SCRIPT = "KILL_PROCESS_SCRIPT";

    private final static int ROUTE = 3;

    private int nextDeviceId = 1;

    private ScriptRunner scriptRunner;
    private LoggingProcess loggingProcess;
    private NetworkStateMachine networkStateMachine;
    private DataSource dataSource;
    private SquidConfigController squidConfigController;
    private RoutingController routingController;
    private OpenVpnProfileFiles profileFiles;
    private OpenVpnChannelFactory openVpnChannelSubscriberFactory;
    private EblockerDnsServer eblockerDnsServer;
    private DeviceService deviceService;
    private Executor executor;
    private VpnKeepAliveFactory vpnKeepAliveFactory;
    private VpnKeepAlive vpnKeepAlive;

    private PubSubService pubSubService;
    private Semaphore processExitSemaphore;
    private Integer exitStatus;

    private OpenVpnProfile vpnProfile;
    private OpenVpnClient client;
    private OpenVpnClientState clientState;

    @Before
    public void setup() throws IOException, InterruptedException {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        // in case script-runner is used to start the openvpn start script it will return a logging process which will block until
        // being signaled to return an exit code
        processExitSemaphore = new Semaphore(1);
        processExitSemaphore.acquire();

        loggingProcess = Mockito.mock(LoggingProcess.class);
        Mockito.when(loggingProcess.waitFor()).then(invocationOnMock -> {
            try {
                processExitSemaphore.acquire();
            } catch (InterruptedException e) {
                logger.warn("unexpected interruption", e);
            }
            return exitStatus;
        });
        Mockito.when(loggingProcess.getPid()).thenReturn(51723);
        Mockito.when(loggingProcess.isAlive()).thenReturn(true);
        Mockito.when(scriptRunner.startScript(Mockito.eq(START_INSTANCE_SCRIPT), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(loggingProcess);

        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        dataSource = Mockito.mock(DataSource.class);

        squidConfigController = Mockito.mock(SquidConfigController.class);
        routingController = Mockito.mock(RoutingController.class);
        Mockito.when(routingController.createRoute()).thenReturn(ROUTE);

        profileFiles = Mockito.mock(OpenVpnProfileFiles.class);
        Mockito.when(profileFiles.getDirectory(Mockito.anyInt())).then(i -> "/test/" + i.getArguments()[0]);

        executor = Executors.newCachedThreadPool();
        pubSubService = new TestPubSubService();
        openVpnChannelSubscriberFactory = (id, listener) -> new OpenVpnChannel(executor, pubSubService, id, listener);

        vpnProfile = new OpenVpnProfile(1, "unit-test-profile-0");
        Mockito.when(dataSource.get(OpenVpnProfile.class, 1)).thenReturn(vpnProfile);

        Mockito.when(dataSource.save(Mockito.any(OpenVpnClientState.class), Mockito.anyInt())).thenAnswer(invocationOnMock -> {
            clientState = invocationOnMock.getArgument(0);
            return clientState;
        });

        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);
        deviceService = Mockito.mock(DeviceService.class);

        vpnKeepAlive = Mockito.mock(VpnKeepAlive.class);
        vpnKeepAliveFactory = Mockito.mock(VpnKeepAliveFactory.class);

        client = new OpenVpnClient(START_INSTANCE_SCRIPT, KILL_PROCESS_SCRIPT, profileFiles, scriptRunner, networkStateMachine, dataSource, squidConfigController, routingController, openVpnChannelSubscriberFactory, executor,
                eblockerDnsServer, deviceService, vpnKeepAliveFactory, vpnProfile);
    }

    @Test
    public void addAndRemoveClient() throws InterruptedException, IOException {
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));

        // allow some time for "openvpn-process" to start
        Thread.sleep(250);

        // client isn't connected to vpn yet
        Assert.assertFalse(client.isUp());

        // ensure used vpn profile has been updated and squid prepared with acls
        Mockito.verify(dataSource, Mockito.times(2)).save(Mockito.any(OpenVpnClientState.class), Mockito.eq(vpnProfile.getId()));
        Assert.assertEquals(vpnProfile.getId(), clientState.getId());

        // verify acls and squid have been prepared
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(vpnProfile.getId(), Collections.singleton(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();
        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195 8.8.8.8,8.8.4.4");
        // wait some time to ensure messages have been processed
        Thread.sleep(2500);

        // client should be connected now
        Assert.assertTrue(client.isUp());

        // check if configuration has been setup
        Mockito.verify(routingController).setClientRoute(ROUTE, "tun0", "10.10.10.10", "10.0.51.1", "5.79.71.195");
        Mockito.verify(squidConfigController).updateSquidConfig();
        Mockito.verify(networkStateMachine, Mockito.times(2)).deviceStateChanged();
        Mockito.verify(eblockerDnsServer).addVpnResolver(vpnProfile.getId(), Arrays.asList("8.8.8.8", "8.8.4.4"), "10.0.51.42");
        Mockito.verify(eblockerDnsServer).useVpnResolver(device, vpnProfile.getId());

        // remove client
        client.stopDevices(Collections.singleton(device));
        Thread.sleep(250);

        // it's the single client so we expect the vpn processed to be killed
        Mockito.verify(scriptRunner).runScript(KILL_PROCESS_SCRIPT, "51723");

        // on regular exit openvpn runs the down script publishing one last status update
        pubSubService.publish(channel, "down sigterm");
        Thread.sleep(250);

        // vpn should be marked as inactive
        Assert.assertFalse(client.isUp());

        // signal "process" to exit
        exitStatus = 0;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // ensure acls and squid have been reconfigured
        Mockito.verify(squidConfigController, Mockito.times(3)).updateVpnDevicesAcl(Mockito.anyInt(), Mockito.anySet());

        // check normal routing has been restored for device
        Mockito.verify(routingController).clearClientRoute(Mockito.eq(ROUTE), Mockito.anyString());
        Mockito.verify(networkStateMachine, Mockito.times(5)).deviceStateChanged();
        Mockito.verify(squidConfigController, Mockito.times(2)).updateSquidConfig();

        // check dns settings have been reverted
        Mockito.verify(eblockerDnsServer).removeVpnResolver(vpnProfile.getId());
        Mockito.verify(eblockerDnsServer).useDefaultResolver(device);

        // check client state is correct
        Mockito.verify(dataSource, Mockito.times(5)).save(Mockito.any(OpenVpnClientState.class), Mockito.eq(vpnProfile.getId()));
        Assert.assertEquals(Collections.emptySet(), clientState.getDevices());
    }

    @Test
    public void addClientIp6() throws InterruptedException {
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));
        Thread.sleep(250);

        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "up6 2abc:cafe:3:4711::3,2abc:cafe:3:4711::2,2abc:cafe:3:4711::1, fc00::/7,::/3,2000::/3,");
        Thread.sleep(250);

    }

    @Test
    public void reloadProfileBeforeConfiguringDns() throws InterruptedException {
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));

        // wait for executor thread
        Thread.sleep(250);

        InOrder inOrder = Mockito.inOrder(dataSource, eblockerDnsServer);
        inOrder.verify(dataSource).get(Mockito.eq(OpenVpnProfile.class), Mockito.eq(vpnProfile.getId()));
        inOrder.verify(eblockerDnsServer).useVpnResolver(device, vpnProfile.getId());
    }

    @Test
    public void addClientNameServersDisabled() throws InterruptedException {
        vpnProfile.setNameServersEnabled(false);

        Device device = createDevice();
        client.addDevices(Collections.singleton(device));

        // allow some time for "openvpn-process" to start
        Thread.sleep(250);

        // client isn't connected to vpn yet
        Assert.assertFalse(client.isUp());

        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 5.79.71.195 8.8.8.8,8.8.4.4");
        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // client should be connected now
        Assert.assertTrue(client.isUp());

        // check name server has not been set
        Mockito.verify(eblockerDnsServer, Mockito.never()).useVpnResolver(device, vpnProfile.getId());
    }

    @Test
    public void addRemoveMultipleClients() throws InterruptedException {
        // setup vpn
        Device[] devices = { createDevice(), createDevice() };
        addSingleDevice(devices[0]);

        resetMocks(); // to clear invocation counts from setup
        Mockito.when(dataSource.getDevices()).thenReturn(new HashSet<>(Arrays.asList(devices)));
        Mockito.when(dataSource.get(OpenVpnProfile.class, 1)).thenReturn(vpnProfile);

        // add second client
        client.addDevices(Collections.singleton(devices[1]));
        Thread.sleep(250);

        // check if second client has been configured
        Assert.assertTrue(clientState.getDevices().contains(devices[1].getId()));
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(vpnProfile.getId(), new HashSet<>(Arrays.asList(devices)));
        Mockito.verify(networkStateMachine).deviceStateChanged();
        Mockito.verify(eblockerDnsServer).useVpnResolver(devices[1], vpnProfile.getId());

        // remove first client
        client.stopDevices(Collections.singleton(devices[0]));
        Thread.sleep(250);

        // check if first client has been removed from routing and vpn is still up
        Assert.assertFalse(clientState.getDevices().contains(devices[0].getId()));
        Assert.assertTrue(clientState.getDevices().contains(devices[1].getId()));
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(vpnProfile.getId(), Collections.singleton(devices[1]));
        Mockito.verify(networkStateMachine, Mockito.times(2)).deviceStateChanged();
        Mockito.verify(eblockerDnsServer).useDefaultResolver(devices[0]);
        Mockito.verify(eblockerDnsServer, Mockito.never()).removeVpnResolver(vpnProfile.getId());

        Assert.assertTrue(client.isUp());
    }

    @Test
    public void unstableVpn() throws InterruptedException {
        // setup vpn
        Device device = createDevice();
        addSingleDevice(device);
        resetMocks(); // reset invocation counters from setup

        // signal vpn down
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "down some-random-trouble");
        Thread.sleep(250);

        // check client and device is marked as inactive
        Assert.assertFalse(client.isUp());

        // signal vpn up again
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195");
        Thread.sleep(250);

        // check client and device is marked as active
        Assert.assertTrue(client.isUp());
    }

    @Test
    public void processDeath() throws InterruptedException, IOException {
        // setup vpn
        Device device = createDevice();
        addSingleDevice(device);
        resetMocks(); // reset invocation counters from setup

        // signal "process" to exit
        exitStatus = 130;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // ensure acls and squid have been reconfigured
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(vpnProfile.getId(), Collections.emptySet());

        // ensure pbr and firewall has been reconfigured
        Mockito.verify(networkStateMachine, Mockito.atLeastOnce()).deviceStateChanged();
        Mockito.verify(routingController).clearClientRoute(Mockito.eq(ROUTE), Mockito.any());

        // ensure client is marked dead and
        Assert.assertFalse(client.isUp());
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(OpenVpnClientState.class), Mockito.eq(vpnProfile.getId()));
        Assert.assertFalse(clientState.getDevices().contains(device.getId()));
    }

    @Test
    public void testStartStop() throws InterruptedException, IOException {
        Assert.assertNull(client.getExitStatus());

        client.start();

        // give client some time to start
        Thread.sleep(250);

        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195");
        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // client should be connected now
        Assert.assertTrue(client.isUp());

        // check if configuration has been setup
        Mockito.verify(routingController).setClientRoute(ROUTE, "tun0", "10.10.10.10", "10.0.51.1", "5.79.71.195");
        Mockito.verify(squidConfigController).updateSquidConfig();
        Mockito.verify(networkStateMachine, Mockito.times(1)).deviceStateChanged();

        // stop client
        client.stop();
        Thread.sleep(250);

        // it's the single client so we expect the vpn processed to be killed
        Mockito.verify(scriptRunner).runScript(KILL_PROCESS_SCRIPT, "51723");

        // on regular exit openvpn runs the down script publishing one last status update
        pubSubService.publish(channel, "down sigterm");
        Thread.sleep(250);

        // vpn should be marked as inactive
        Assert.assertFalse(client.isUp());

        // signal "process" to exit
        exitStatus = 99;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // ensure exit status is correctly set
        Assert.assertEquals(new Integer(99), client.getExitStatus());

        // ensure acls and squid have been reconfigured
        Mockito.verify(squidConfigController, Mockito.times(1)).updateVpnDevicesAcl(Mockito.anyInt(), Mockito.anySet());

        // check normal routing has been restored for device
        Mockito.verify(routingController).clearClientRoute(Mockito.eq(ROUTE), Mockito.anyString());
        Mockito.verify(squidConfigController, Mockito.times(2)).updateSquidConfig();
    }

    /**
     * EB1-315: ensure profile is reloaded on start
     */
    @Test
    public void testProfileReload() throws InterruptedException {

        VpnProfile initialProfile = vpnProfile;

        InOrder dataSourceInOrder = Mockito.inOrder(dataSource);
        InOrder profileFilesInOrder = Mockito.inOrder(profileFiles);

        client.start();
        Thread.sleep(250);

        // report running process
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        Thread.sleep(250);

        dataSourceInOrder.verify(dataSource).get(OpenVpnProfile.class, 1);
        profileFilesInOrder.verify(profileFiles).writeTransientCredentialsFile(initialProfile);
        client.stop();
        Thread.sleep(250);
        // signal "process" to exit
        exitStatus = 99;
        processExitSemaphore.release();
        Thread.sleep(250);

        // replace profile with new version
        vpnProfile = new OpenVpnProfile(1, "updated-unit-test-profile-1");

        client.start();
        Thread.sleep(250);
        dataSourceInOrder.verify(dataSource).get(OpenVpnProfile.class, 1);
        profileFilesInOrder.verify(profileFiles).writeTransientCredentialsFile(vpnProfile);
    }

    @Test
    public void testKeepAlivePing() throws InterruptedException, IOException {
        // set up keep alive properties and mocks
        vpnProfile.setKeepAliveMode(KeepAliveMode.CUSTOM);
        vpnProfile.setKeepAlivePingTarget("www.eblocker.com");
        Mockito.when(vpnKeepAliveFactory.create(Mockito.anyString(), Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(vpnKeepAlive);

        // create device and give vpn some time to start-up
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));
        Thread.sleep(250);

        // verify process has been started
        Mockito.verify(loggingProcess).waitFor();

        // client isn't connected to vpn yet
        Assert.assertFalse(client.isUp());

        // ensure used vpn profile has been updated and squid prepared with acls
        Mockito.verify(dataSource, Mockito.times(2)).save(Mockito.any(OpenVpnClientState.class), Mockito.eq(vpnProfile.getId()));
        Assert.assertEquals(vpnProfile.getId(), clientState.getId());

        // verify acls and squid have been prepared
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(vpnProfile.getId(), Collections.singleton(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();
        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195 8.8.8.8,8.8.4.4");
        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // verify keep-alive ping has been started
        ArgumentCaptor<Runnable> connectionDeadCallbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(vpnKeepAliveFactory).create(Mockito.eq("tun0"), Mockito.eq("www.eblocker.com"), connectionDeadCallbackCaptor.capture());
        Mockito.verify(vpnKeepAlive).start();

        // client should be connected now
        Assert.assertTrue(client.isUp());

        // check if configuration has been setup
        Mockito.verify(routingController).setClientRoute(ROUTE, "tun0", "10.10.10.10", "10.0.51.1", "5.79.71.195");
        Mockito.verify(squidConfigController).updateSquidConfig();
        Mockito.verify(networkStateMachine, Mockito.times(2)).deviceStateChanged();
        Mockito.verify(eblockerDnsServer).addVpnResolver(vpnProfile.getId(), Arrays.asList("8.8.8.8", "8.8.4.4"), "10.0.51.42");
        Mockito.verify(eblockerDnsServer).useVpnResolver(device, vpnProfile.getId());

        // call "connection is dead"-callback and check vpn is being restarted
        connectionDeadCallbackCaptor.getValue().run();
        Thread.sleep(250);

        Mockito.verify(vpnKeepAlive).stop();
        Mockito.verify(scriptRunner).runScript(KILL_PROCESS_SCRIPT, "51723");

        // on regular exit openvpn runs the down script publishing one last status update
        pubSubService.publish(channel, "down sigterm");
        Thread.sleep(250);

        // vpn should be marked as inactive
        Assert.assertFalse(client.isUp());

        // signal "process" to exit
        exitStatus = 0;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // check normal routing has been restored for device
        Mockito.verify(routingController).clearClientRoute(Mockito.eq(ROUTE), Mockito.anyString());
        Mockito.verify(networkStateMachine, Mockito.times(3)).deviceStateChanged();
        Mockito.verify(squidConfigController, Mockito.times(2)).updateSquidConfig();

        // check client state is correct
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(Mockito.any(OpenVpnClientState.class), Mockito.eq(vpnProfile.getId()));
        Assert.assertEquals(Collections.singleton(device.getId()), clientState.getDevices());
        Assert.assertEquals(OpenVpnClientState.State.PENDING_RESTART, clientState.getState());

        // check process is started again
        Mockito.verify(loggingProcess, Mockito.times(2)).waitFor();

        // send up message
        pubSubService.publish(channel, "pid 51724");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195 8.8.8.8,8.8.4.4");
        Mockito.when(loggingProcess.getPid()).thenReturn(51724);
        Thread.sleep(250);

        // check routing is set-up again
        Mockito.verify(routingController, Mockito.times(2)).setClientRoute(ROUTE, "tun0", "10.10.10.10", "10.0.51.1", "5.79.71.195");
        Mockito.verify(squidConfigController, Mockito.times(3)).updateSquidConfig();
        Mockito.verify(networkStateMachine, Mockito.times(4)).deviceStateChanged();
        Mockito.verify(eblockerDnsServer, Mockito.times(2)).addVpnResolver(vpnProfile.getId(), Arrays.asList("8.8.8.8", "8.8.4.4"), "10.0.51.42");
        Mockito.verify(eblockerDnsServer, Mockito.times(1)).useVpnResolver(device, vpnProfile.getId()); // device has not been configured to return to normal dns

        // request shutdown and check vpn is not restarted again
        client.stop();
        Thread.sleep(250);

        Mockito.verify(vpnKeepAlive, Mockito.times(2)).stop();

        // it's the single client so we expect the vpn processed to be killed
        Mockito.verify(scriptRunner).runScript(KILL_PROCESS_SCRIPT, "51724");

        // on regular exit openvpn runs the down script publishing one last status update
        pubSubService.publish(channel, "down sigterm");
        Thread.sleep(250);

        // vpn should be marked as inactive
        Assert.assertFalse(client.isUp());

        // signal "process" to exit
        exitStatus = 0;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // check process is not started again
        Mockito.verify(loggingProcess, Mockito.times(2)).waitFor();
        Assert.assertEquals(OpenVpnClientState.State.INACTIVE, clientState.getState());
        Assert.assertEquals(Collections.emptySet(), clientState.getDevices());
    }

    @Test
    public void testKeepAliveStoppedAfterProcessDied() throws InterruptedException {
        // set up keep alive properties and mocks
        vpnProfile.setKeepAliveMode(KeepAliveMode.CUSTOM);
        vpnProfile.setKeepAlivePingTarget("www.eblocker.com");
        Mockito.when(vpnKeepAliveFactory.create(Mockito.anyString(), Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(vpnKeepAlive);

        // create device and give vpn some time to start-up
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));
        Thread.sleep(250);

        // verify process has been started
        Mockito.verify(loggingProcess).waitFor();

        // client isn't connected to vpn yet
        Assert.assertFalse(client.isUp());

        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 5.79.71.195 8.8.8.8,8.8.4.4");
        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // verify keep-alive ping has been started
        ArgumentCaptor<Runnable> connectionDeadCallbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(vpnKeepAliveFactory).create(Mockito.eq("tun0"), Mockito.eq("www.eblocker.com"), connectionDeadCallbackCaptor.capture());
        Mockito.verify(vpnKeepAlive).start();

        // signal "process" to exit
        exitStatus = 130;
        processExitSemaphore.release();

        // ensure all post processing is finished
        Thread.sleep(250);

        // ensure vpn keep alive has been stopped
        Mockito.verify(vpnKeepAlive).stop();
    }

    @Test
    public void testKeepAliveIsRestartedAfterVpnDownUp() throws InterruptedException {
        // set up keep alive properties and mocks
        vpnProfile.setKeepAliveMode(KeepAliveMode.CUSTOM);
        vpnProfile.setKeepAlivePingTarget("www.eblocker.com");
        Mockito.when(vpnKeepAliveFactory.create(Mockito.anyString(), Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(vpnKeepAlive);

        // create device and give vpn some time to start-up
        Device device = createDevice();
        client.addDevices(Collections.singleton(device));
        Thread.sleep(250);

        // verify process has been started
        Mockito.verify(loggingProcess).waitFor();

        // client isn't connected to vpn yet
        Assert.assertFalse(client.isUp());

        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 5.79.71.195 8.8.8.8,8.8.4.4");
        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // verify keep-alive ping has been started
        ArgumentCaptor<Runnable> connectionDeadCallbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(vpnKeepAliveFactory).create(Mockito.eq("tun0"), Mockito.eq("www.eblocker.com"), connectionDeadCallbackCaptor.capture());
        Mockito.verify(vpnKeepAlive).start();

        // signal vpn down
        pubSubService.publish(channel, "down some-random-trouble");
        Thread.sleep(250);

        // check client and device is marked as inactive and vpn keep alive has not been stopped
        Assert.assertFalse(client.isUp());
        Mockito.verify(vpnKeepAlive, Mockito.times(0)).stop();

        // signal vpn up again
        pubSubService.publish(channel, "up tun1 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195");
        Thread.sleep(250);

        // check client and device is marked as active and a new vpn keep alive has been created and the previous one has been stopped
        Assert.assertTrue(client.isUp());
        Mockito.verify(vpnKeepAliveFactory).create(Mockito.eq("tun1"), Mockito.eq("www.eblocker.com"), connectionDeadCallbackCaptor.capture());
        Mockito.verify(vpnKeepAlive).stop();
        Mockito.verify(vpnKeepAlive, Mockito.times(2)).start();
    }

    private Device createDevice() {
        int i = nextDeviceId++;
        String id = String.format("%s%012x", Device.ID_PREFIX, i);
        IpAddress ip = IpAddress.parse("192.168.1." + i);

        Device device = new Device();
        device.setId(id);
        device.setIpAddresses(Collections.singletonList(ip));

        Mockito.when(deviceService.getDeviceByIp(ip)).thenReturn(device);
        Mockito.when(deviceService.getDeviceById(id)).thenReturn(device);

        return device;
    }

    /**
     * This method add a single device to the vpn and starts it. It doesn't test if start sequence was
     * correct because the point of this method is to setup <></>he client for tests beyond starting. For
     * testing startup sequence @see OpenVpnClientTest#addAndRemoveClient()
     *
     * @param device device to add to vpn
     */
    private void addSingleDevice(Device device) throws InterruptedException {
        client.addDevices(Collections.singleton(device));

        // allow some time for "openvpn-process" to start
        Thread.sleep(250);

        // report vpn usable
        String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, vpnProfile.getId());
        pubSubService.publish(channel, "pid 51723");
        pubSubService.publish(channel, "up tun0 10.10.10.10 10.0.51.1 5.79.71.195 8.8.8.8,8.8.4.4");

        // wait some time to ensure messages have been processed
        Thread.sleep(250);

        // client should be connected now
        Assert.assertTrue(client.isUp());
    }

    /**
     * Resets all mocks
     */
    private void resetMocks() {
        Mockito.reset(this.networkStateMachine, this.dataSource, this.routingController, this.scriptRunner, this.squidConfigController);
    }
}
