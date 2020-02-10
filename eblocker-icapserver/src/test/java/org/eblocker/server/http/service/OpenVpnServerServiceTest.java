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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.fourthline.cling.support.model.PortMapping.Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.http.utils.NormalizationUtils;
import org.eblocker.server.upnp.UpnpManagementService;
import org.eblocker.server.upnp.UpnpPortForwarding;
import org.eblocker.server.upnp.UpnpPortForwardingResult;
import org.eblocker.server.common.system.ScriptRunner;

import org.junit.Assert;

public class OpenVpnServerServiceTest {
    OpenVpnServerService service;
    ScriptRunner scriptRunner;
    DataSource dataSource;
    DeviceRegistrationProperties deviceRegistrationProperties;
    NetworkInterfaceWrapper networkInterfaceWrapper;
    UpnpManagementService upnpService;
    EblockerDnsServer dnsServer;
    DnsService dnsService;
    ScheduledExecutorService executorService;
    EventLogger eventLogger;
    String openVpnServerCommand = "openvpn-server-control";
    int port = 1194;
    int tempDuration = 60;
    int duration = 0;
    String portForwardingDescription = "eBlocker Mobile";
    String registrationEmailAddress = "test@test.test";
    String registrationEblockerName = "my eBlocker";
    String registrationEblockerDeviceId = "15ae0432a0be0d0143401ee4d7c7a9a5114b6c13ba37ea6f0642451a75c1fcd2";
    String registrationEblockerDeviceIdSubstring = registrationEblockerDeviceId.substring(0, 8);

    @Before
    public void setup() {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        dataSource = Mockito.mock(DataSource.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        Mockito.when(deviceRegistrationProperties.getDeviceRegisteredBy()).thenReturn(registrationEmailAddress);
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn(registrationEblockerName);
        Mockito.when(deviceRegistrationProperties.getDeviceId()).thenReturn(registrationEblockerDeviceId);
        upnpService = Mockito.mock(UpnpManagementService.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        dnsService = Mockito.mock(DnsService.class);
        executorService = Mockito.mock(ScheduledExecutorService.class);
        eventLogger = Mockito.mock(EventLogger.class);

    }

    @After
    public void teardown() {

    }

    @Test
    public void testDontStartServerIfDisabled() throws IOException, InterruptedException {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(false);

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(openVpnServerCommand, "start");
    }

    @Test
    public void testStartServerIfEnabled() throws IOException, InterruptedException {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(true);

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Mockito.verify(executorService).execute(Mockito.any(Runnable.class));
    }

    @Test
    public void testSetGetExternalAddressType() {
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        service.setOpenVpnExternalAddressType(ExternalAddressType.DYN_DNS);
        Mockito.verify(dataSource).setOpenVpnExternalAddressType(ExternalAddressType.DYN_DNS);

        service.setOpenVpnExternalAddressType(ExternalAddressType.EBLOCKER_DYN_DNS);
        Mockito.verify(dataSource).setOpenVpnExternalAddressType(ExternalAddressType.EBLOCKER_DYN_DNS);

        service.setOpenVpnExternalAddressType(ExternalAddressType.FIXED_IP);
        Mockito.verify(dataSource).setOpenVpnExternalAddressType(ExternalAddressType.FIXED_IP);

        Mockito.when(dataSource.getOpenVpnExternalAddressType()).thenReturn(ExternalAddressType.DYN_DNS,
                ExternalAddressType.EBLOCKER_DYN_DNS, ExternalAddressType.FIXED_IP);
        Assert.assertEquals(ExternalAddressType.DYN_DNS, service.getOpenVpnExternalAddressType());
        Assert.assertEquals(ExternalAddressType.EBLOCKER_DYN_DNS, service.getOpenVpnExternalAddressType());
        Assert.assertEquals(ExternalAddressType.FIXED_IP, service.getOpenVpnExternalAddressType());

        Mockito.verify(dataSource, Mockito.times(3)).getOpenVpnExternalAddressType();
    }

    @Test
    public void testSetGetPortForwardingMode() {
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        service.setOpenVpnPortForwardingMode(PortForwardingMode.AUTO);
        Mockito.verify(dataSource).setOpenVpnPortForwardingMode(PortForwardingMode.AUTO);

        service.setOpenVpnPortForwardingMode(PortForwardingMode.MANUAL);
        Mockito.verify(dataSource).setOpenVpnPortForwardingMode(PortForwardingMode.MANUAL);

        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO,
                PortForwardingMode.MANUAL);
        Assert.assertEquals(PortForwardingMode.AUTO, service.getOpenVpnPortForwardingMode());
        Assert.assertEquals(PortForwardingMode.MANUAL, service.getOpenVpnPortForwardingMode());

        Mockito.verify(dataSource, Mockito.times(2)).getOpenVpnPortForwardingMode();
    }

    @Test
    public void testSetAndMapManualMode() {
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.MANUAL);
        int portNum = 1337;
        try {
            service.setAndMapExternalPortTemporarily(portNum);
        } catch (UpnpPortForwardingException e) {
            Assert.assertTrue(false);
        }

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();
        Mockito.verify(upnpService, Mockito.never()).addPortForwarding(Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyInt(), Mockito.anyString(), Mockito.eq(false));
        Assert.assertEquals(portNum, service.getOpenVpnTempMappedPort());
    }

    @Test
    public void testSetAndMapAutoMode() throws UpnpPortForwardingException {
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int portNum = 1337;
        service.setAndMapExternalPortTemporarily(portNum);

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();
        Mockito.verify(upnpService).addPortForwarding(portNum, port, tempDuration,
                portForwardingDescription, false);
        Assert.assertEquals(portNum, service.getOpenVpnTempMappedPort());

    }

    @Test
    public void testStartOpenVpnServerDnsServerCouldNotBeStarted() throws UpnpPortForwardingException {

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        service.startOpenVpnServer();

        Mockito.verify(dnsServer).isEnabled();
        Mockito.verify(dnsService).setStatus(true);
        Mockito.verify(dataSource, Mockito.never()).getOpenVpnServerFirstRun();

    }

    @Test
    public void testStartOpenVpnServerCouldNotBeInitialized()
            throws UpnpPortForwardingException, IOException, InterruptedException {
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);

        List<String> list = new ArrayList<>(
                Arrays.asList("init", registrationEmailAddress, registrationEblockerDeviceIdSubstring,
                        NormalizationUtils.normalizeStringForShellScript(registrationEblockerName)));

        Mockito.when(scriptRunner.runScript("openvpn-server-control", list.toArray(new String[0]))).thenReturn(1);

        Mockito.when(upnpService.addPortForwarding(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.eq(false))).thenReturn(Collections.emptyList());

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration,
                duration, portForwardingDescription);
        service.init();

        service.startOpenVpnServer();

        Mockito.verify(dnsServer).isEnabled();
        Mockito.verify(dataSource).getOpenVpnServerFirstRun();

        Mockito.verify(scriptRunner, Mockito.never()).runScript("openvpn-server-control", "start");
    }

    @Test
    public void testStartOpenVpnServer() throws UpnpPortForwardingException, IOException, InterruptedException {
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int externalPort = 1337;
        Mockito.when(dataSource.getOpenVpnMappedPort()).thenReturn(externalPort);

        List<String> list = new ArrayList<>(Arrays.asList("init"));

        Mockito.when(scriptRunner.runScript("openvpn-server-control", list.toArray(new String[0]))).thenReturn(0);

        Mockito.when(upnpService.addPortForwarding(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.eq(false))).thenReturn(Collections.emptyList());

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Assert.assertTrue(service.startOpenVpnServer());

        Mockito.verify(dnsServer).isEnabled();
        Mockito.verify(dataSource).getOpenVpnServerFirstRun();

        Mockito.verify(scriptRunner).runScript("openvpn-server-control", "init", registrationEmailAddress,
                registrationEblockerDeviceIdSubstring,
                NormalizationUtils.normalizeStringForShellScript(registrationEblockerName));
        Mockito.verify(scriptRunner).runScript("openvpn-server-control", "start");
        Mockito.verify(dataSource).setOpenVpnServerFirstRun(false);
        Mockito.verify(dataSource).setOpenVpnServerState(true);
        Mockito.verify(upnpService).addPortForwarding(externalPort, port, duration, portForwardingDescription, true);
    }

    @Test
    public void testSetGetTempOrt() {
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        port = 2342;
        service.setOpenVpnTempMappedPort(port);
        Assert.assertEquals(port, service.getOpenVpnTempMappedPort());
    }
    @Test
    public void testSetGetHost(){
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        String host = "host.tld";
        Mockito.when(dataSource.getOpenVpnServerHost()).thenReturn(host);
        service.setOpenVpnServerHost(host);
        Assert.assertEquals(host, service.getOpenVpnServerHost());
        Mockito.verify(dataSource).setOpenVpnServerHost(host);
    }
    @Test
    public void testSetGetMappedPort(){
        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        int portTest = 31337;
        Mockito.when(dataSource.getOpenVpnMappedPort()).thenReturn(portTest, null);
        service.setOpenVpnMappedPort(portTest);
        Assert.assertEquals(Integer.valueOf(portTest), service.getOpenVpnMappedPort());
        Mockito.verify(dataSource).setOpenVpnMappedPort(portTest);
        Assert.assertEquals(Integer.valueOf(port), service.getOpenVpnMappedPort());
    }
    @Test
    public void testDisableServer() throws UpnpPortForwardingException, IOException, InterruptedException{
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnServerFirstRun()).thenReturn(true);
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);
        int externalPort = 1337;
        Mockito.when(dataSource.getOpenVpnMappedPort()).thenReturn(externalPort);

        List<String> list = new ArrayList<>(Arrays.asList("init"));

        Mockito.when(scriptRunner.runScript("openvpn-server-control", list.toArray(new String[0]))).thenReturn(0);

        UpnpPortForwarding forwarding = new UpnpPortForwarding(1, 2, "intHostIp", 3, "description", Protocol.TCP, true);
        UpnpPortForwardingResult forwardingResult = new UpnpPortForwardingResult(forwarding, true, null);
        List<UpnpPortForwardingResult> forwardingResults = new ArrayList<>(Arrays.asList(forwardingResult));
        Mockito.when(upnpService.addPortForwarding(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.eq(true))).thenReturn(forwardingResults);

        service = new OpenVpnServerService(scriptRunner, dataSource, upnpService, dnsServer, dnsService,
                executorService, eventLogger, deviceRegistrationProperties, openVpnServerCommand, port, tempDuration, duration,
                portForwardingDescription);
        service.init();

        Assert.assertTrue(service.startOpenVpnServer());
        
        Mockito.verify(dnsServer).isEnabled();
        Mockito.verify(dataSource).getOpenVpnServerFirstRun();

        Mockito.verify(scriptRunner).runScript("openvpn-server-control", "init", registrationEmailAddress,
                registrationEblockerDeviceIdSubstring,
                NormalizationUtils.normalizeStringForShellScript(registrationEblockerName));
        Mockito.verify(scriptRunner).runScript("openvpn-server-control", "start");
        Mockito.verify(dataSource).setOpenVpnServerFirstRun(false);
        Mockito.verify(dataSource).setOpenVpnServerState(true);
        Mockito.verify(upnpService).addPortForwarding(externalPort, port, duration, portForwardingDescription, true);

        // Now the service has a list of opened ports

        service.disableOpenVpnServer();

        Mockito.verify(dataSource).setOpenVpnServerState(false);
        Mockito.verify(upnpService).removePortForwardings(new ArrayList<>(Arrays.asList(forwarding)));
    }
}
