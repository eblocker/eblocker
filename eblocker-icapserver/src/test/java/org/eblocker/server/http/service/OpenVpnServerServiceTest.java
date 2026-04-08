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
package org.eblocker.server.http.service;

import org.eblocker.server.common.MockScheduledExecutorService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.openvpn.server.OpenVpnCa;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.upnp.UpnpManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class OpenVpnServerServiceTest {
    OpenVpnServerService service;
    ScriptRunner scriptRunner;
    DataSource dataSource;
    DeviceService deviceService;
    UpnpManagementService upnpService;
    EblockerDnsServer dnsServer;
    DnsService dnsService;
    DynDnsService dynDnsService;
    MockScheduledExecutorService executorService;
    EventLogger eventLogger;
    OpenVpnCa openVpnCa;
    String openVpnServerCommand = "openvpn-server-control";
    int port = 1194;
    int tempDuration = 60;
    int duration = 0;
    String portForwardingDescription = "eBlocker Mobile";

    @BeforeEach
    public void setup() {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        upnpService = Mockito.mock(UpnpManagementService.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        dnsService = Mockito.mock(DnsService.class);
        dynDnsService = Mockito.mock(DynDnsService.class);
        executorService = new MockScheduledExecutorService(Duration.ofSeconds(1)); // Method init() might take a while
        eventLogger = Mockito.mock(EventLogger.class);
        openVpnCa = Mockito.mock(OpenVpnCa.class);
    }

    private OpenVpnServerService createOpenVpnServerService() {
        return new OpenVpnServerService(scriptRunner, dataSource, deviceService, upnpService, dnsServer,
                dnsService, dynDnsService, executorService, eventLogger, openVpnServerCommand, port, tempDuration,
                duration, portForwardingDescription, openVpnCa);
    }

    private void createAndInitService() {
        service = createOpenVpnServerService();
        service.init();
        executorService.elapse(Duration.ofSeconds(2)); // so the initStartOpenVpnServer task has completed
    }

    @Test
    public void testDontStartServerIfDisabled() throws Exception {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(false);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);

        createAndInitService();

        Mockito.verify(scriptRunner, Mockito.never()).runScript(openVpnServerCommand, "start");
    }

    @Test
    public void testStartServerIfEnabled() throws Exception {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(true);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);

        createAndInitService();

        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "start");
    }

    @Test
    public void startOpenVpnServerFirstStartFailed() throws Exception {
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);

        createAndInitService();

        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // not running
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "start")).thenReturn(1); // start fails

        VpnServerStatus status = service.setOpenVpnServerStatus(startServerRequest());
        assertFalse(status.isRunning());
    }

    @Test
    public void startOpenVpnServerSuccessfully() throws Exception {
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // not running
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "start")).thenReturn(0); // start OK

        createAndInitService();

        VpnServerStatus status = service.setOpenVpnServerStatus(startServerRequest());
        assertTrue(status.isRunning());

        // Simulate that server is running to prevent another "start"
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(0); // running

        service.setOpenVpnServerStatus(startServerRequest());
    }

    @Test
    public void startOpenVpnServerAndEnableDns() throws Exception {
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // not running
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "start")).thenReturn(0); // start OK

        createAndInitService();

        VpnServerStatus status = service.setOpenVpnServerStatus(startServerRequest());

        assertTrue(status.isRunning());
    }

    @Test
    public void stopOpenVpnServer() throws Exception {
        Collection<Device> devices = new LinkedList<>();
        Device device = Mockito.mock(Device.class);
        devices.add(device);
        Mockito.when(deviceService.getDevices(false)).thenReturn(devices);
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(0); // running
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "stop")).thenReturn(0); // stop OK

        createAndInitService();

        VpnServerStatus status = service.setOpenVpnServerStatus(stopServerRequest());

        assertFalse(status.isRunning());
        Mockito.verify(device, Mockito.times(1)).setIsVpnClient(false);
    }

    @Test
    public void startWithEblockerDynDns() {
        VpnServerStatus statusIn = startServerRequest();
        final String dynDnsHost = "abcdefghijklmnop.home.eblocker.com";
        final Integer mappedPort = 1195;
        statusIn.setExternalAddressType(ExternalAddressType.EBLOCKER_DYN_DNS);
        statusIn.setPortForwardingMode(PortForwardingMode.AUTO);
        statusIn.setMappedPort(mappedPort);
        Mockito.when(dynDnsService.getHostname()).thenReturn(dynDnsHost);
        Mockito.when(dataSource.getOpenVpnServerHost()).thenReturn(dynDnsHost);

        createAndInitService();

        VpnServerStatus statusOut = service.setOpenVpnServerStatus(statusIn);

        assertEquals(dynDnsHost, statusOut.getHost());
        assertEquals(ExternalAddressType.EBLOCKER_DYN_DNS, statusOut.getExternalAddressType());
        assertEquals(PortForwardingMode.AUTO, statusOut.getPortForwardingMode());
        assertEquals(mappedPort, statusOut.getMappedPort());

        Mockito.verify(dataSource).setOpenVpnServerHost(dynDnsHost);
    }

    @Test
    public void testSetAndMapManualMode() {
        createAndInitService();

        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.MANUAL);
        int portNum = 1337;
        try {
            service.setAndMapExternalPortTemporarily(portNum);
        } catch (UpnpPortForwardingException e) {
            assertTrue(false);
        }

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();
        Mockito.verify(upnpService, Mockito.never()).addPortForwarding(Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void testSetAndMapAutoMode() throws Exception {
        createAndInitService();

        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int portNum = 1337;
        service.setAndMapExternalPortTemporarily(portNum);

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();
        Mockito.verify(upnpService).addPortForwarding(portNum, port, tempDuration,
                portForwardingDescription);
    }

    @Test
    public void testStartOpenVpnServerDnsServerCouldNotBeStarted() throws Exception {
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // server not running

        createAndInitService();
        service.setOpenVpnServerStatus(startServerRequest());

        Mockito.verify(dnsServer).isEnabled();
        Mockito.verify(dnsService).setStatus(true);
        Mockito.verify(scriptRunner, Mockito.never()).runScript(openVpnServerCommand, "start");

    }

    @Test
    public void testStartOpenVpnServerCouldNotBeInitialized() throws Exception {
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // server not running
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "init")).thenReturn(1); // initialization fails

        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);


        Mockito.when(upnpService.addPortForwarding(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyString())).thenReturn(Collections.emptyList());

        createAndInitService();
        service.setOpenVpnServerStatus(startServerRequest());

        Mockito.verify(dnsServer).isEnabled();

        Mockito.verify(scriptRunner, Mockito.never()).runScript(openVpnServerCommand, "start");
    }

    @Test
    public void testStartOpenVpnServer() throws Exception {
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // server not running
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int externalPort = 1337;
        Mockito.when(dataSource.getOpenVpnMappedPort()).thenReturn(externalPort);

        createAndInitService();
        service.setOpenVpnServerStatus(startServerRequest());

        Mockito.verify(dnsServer).isEnabled();

        Mockito.verify(openVpnCa).generateCa();
        Mockito.verify(openVpnCa).generateServerCertificate();
        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "init");
        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "start");
        Mockito.verify(dataSource).setOpenVpnServerFirstRun(false);
        Mockito.verify(dataSource).setOpenVpnServerState(true);
    }

    @Test
    public void testDisableServer() throws Exception {
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(1); // server not running
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int externalPort = 1337;
        Mockito.when(dataSource.getOpenVpnMappedPort()).thenReturn(externalPort);

        createAndInitService();
        service.setOpenVpnServerStatus(startServerRequest());

        Mockito.verify(dnsServer).isEnabled();

        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "init");
        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "start");
        Mockito.verify(dataSource).setOpenVpnServerFirstRun(false);
        Mockito.verify(dataSource).setOpenVpnServerState(true);

        // Now the service has a list of opened ports
        Mockito.when(scriptRunner.runScript(openVpnServerCommand, "status")).thenReturn(0); // server is running

        // Disable the server:
        service.setOpenVpnServerStatus(stopServerRequest());

        Mockito.verify(dataSource).setOpenVpnServerState(false);
    }

    @Test
    public void testResetServer() throws IOException, InterruptedException {
        createAndInitService();
        assertTrue(service.resetOpenVpnServer());
        Mockito.verify(dataSource).setOpenVpnServerFirstRun(true);
        Mockito.verify(dataSource).setOpenVpnServerState(false);
        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "stop");
        Mockito.verify(scriptRunner).runScript(openVpnServerCommand, "purge");
    }

    @Test
    public void testRevokeDeletedDevices() throws Exception {
        service = createOpenVpnServerService();
        ArgumentCaptor<DeviceService.DeviceChangeListener> captor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.when(openVpnCa.getActiveClientIds()).thenReturn(Set.of("device:a", "device:b"));
        service.init();

        // Simulate device deletion by DeviceService:
        Mockito.verify(deviceService).addListener(captor.capture());
        Device device = new Device();
        device.setId("device:b");
        captor.getValue().onDelete(device);

        // Only device b was revoked:
        Mockito.verify(openVpnCa, Mockito.never()).revokeClientCertificate("device:a");
        Mockito.verify(openVpnCa).revokeClientCertificate("device:b");

        // Another device without eBlocker Mobile access:
        device.setId("device:c");
        captor.getValue().onDelete(device);
        Mockito.verify(openVpnCa, Mockito.never()).revokeClientCertificate("device:c");
    }

    private VpnServerStatus startServerRequest() {
        VpnServerStatus status = new VpnServerStatus();
        status.setHost("eblocker.com");
        status.setRunning(true);
        return status;
    }

    private VpnServerStatus stopServerRequest() {
        VpnServerStatus status = new VpnServerStatus();
        status.setRunning(false);
        return status;
    }
}
