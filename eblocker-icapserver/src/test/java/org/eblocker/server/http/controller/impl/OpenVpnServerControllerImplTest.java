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

package org.eblocker.server.http.controller.impl;

import io.netty.buffer.ByteBuf;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.openvpn.server.OpenVpnClientConfigurationService;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.DnsService;
import org.eblocker.server.http.service.DynDnsService;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenVpnServerControllerImplTest {
    private static final String registrationEblockerName = "my eBlocker (the white cube)";

    private OpenVpnServerService openVpnServerService;
    private OpenVpnServerControllerImpl controller;
    private DeviceService deviceService;
    private DnsService dnsService;
    private DynDnsService dynDnsService;
    private Response response;
    private OpenVpnClientConfigurationService openVpnClientConfigurationService;
    private SquidConfigController squidConfigController;
    private String deviceId = "device:001122334455";
    private OperatingSystemType osType = OperatingSystemType.OTHER;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private NetworkStateMachine networkStateMachine;

    @Before
    public void setup() throws URISyntaxException, IOException {
        deviceService = Mockito.mock(DeviceService.class);
        dnsService = Mockito.mock(DnsService.class);
        dynDnsService = Mockito.mock(DynDnsService.class);
        openVpnClientConfigurationService = Mockito.mock(OpenVpnClientConfigurationService.class);
        openVpnServerService = Mockito.mock(OpenVpnServerService.class);

        Mockito.when(openVpnServerService.isOpenVpnServerEnabled()).thenReturn(true);
        Mockito.when(openVpnServerService.getDeviceIdsWithCertificates()).thenReturn(Collections.singleton(deviceId));
        squidConfigController = Mockito.mock(SquidConfigController.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn(registrationEblockerName);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);

        controller = new OpenVpnServerControllerImpl(
                openVpnServerService,
                openVpnClientConfigurationService,
                deviceService,
                dynDnsService,
                squidConfigController,
                deviceRegistrationProperties,
                networkStateMachine);
        response = new Response();
    }

    @Test
    public void getOpenVpnServerStatus() {
        Mockito.when(openVpnServerService.isOpenVpnServerfirstRun()).thenReturn(true);
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("eblocker.com");
        Mockito.when(openVpnServerService.getOpenVpnServerStatus()).thenReturn(true);

        Request request = Mockito.mock(Request.class);
        VpnServerStatus status = controller.getOpenVpnServerStatus(request, response);

        assertTrue(status.isFirstStart());
        assertEquals("eblocker.com", status.getHost());
        assertTrue(status.isRunning());
    }

    @Test
    public void startOpenVpnServerFirstStartFailed() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(VpnServerStatus.class)).thenReturn(startServerRequest());
        Mockito.when(openVpnServerService.isOpenVpnServerfirstRun()).thenReturn(true);

        VpnServerStatus status = controller.setOpenVpnServerStatus(request, response);
        assertFalse(status.isRunning());
    }

    @Test
    public void startOpenVpnServerSuccessfully() throws UpnpPortForwardingException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(VpnServerStatus.class)).thenReturn(startServerRequest());
        Mockito.when(openVpnServerService.isOpenVpnServerfirstRun()).thenReturn(true);
        Mockito.when(openVpnServerService.startOpenVpnServer()).thenReturn(true);
        Mockito.when(openVpnServerService.getOpenVpnServerStatus()).thenReturn(false);

        VpnServerStatus status = controller.setOpenVpnServerStatus(request, response);
        assertTrue(status.isRunning());

        // Simulate that server is running to prevent another "start"
        Mockito.when(openVpnServerService.getOpenVpnServerStatus()).thenReturn(true);

        controller.setOpenVpnServerStatus(request, response);

        Mockito.verify(squidConfigController, Mockito.times(1)).tellSquidToReloadConfig();
    }

    @Test
    public void startOpenVpnServerAndEnableDns() throws UpnpPortForwardingException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(VpnServerStatus.class)).thenReturn(startServerRequest());
        Mockito.when(dnsService.setStatus(true)).thenReturn(true);
        Mockito.when(openVpnServerService.startOpenVpnServer()).thenReturn(true);

        VpnServerStatus status = controller.setOpenVpnServerStatus(request, response);

        assertTrue(status.isRunning());
        Mockito.verify(squidConfigController, Mockito.times(1)).tellSquidToReloadConfig();
    }

    @Test
    public void stopOpenVpnServer() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(VpnServerStatus.class)).thenReturn(stopServerRequest());

        Collection<Device> devices = new LinkedList<>();
        Device device = Mockito.mock(Device.class);
        devices.add(device);
        Mockito.when(deviceService.getDevices(false)).thenReturn(devices);
        Mockito.when(openVpnServerService.stopOpenVpnServer()).thenReturn(true);
        Mockito.when(openVpnServerService.getOpenVpnServerStatus()).thenReturn(true);// <-?

        controller.setOpenVpnServerStatus(request, response);
        Mockito.verify(device, Mockito.times(1)).setIsVpnClient(false);
        Mockito.verify(squidConfigController, Mockito.times(1)).tellSquidToReloadConfig();
    }

    @Test
    public void downloadClientConf() throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);

        Device device = Mockito.mock(Device.class);
        Mockito.when(device.getId()).thenReturn(deviceId);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getUserFriendlyName()).thenReturn("device%//%%-from-Äggard");
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(openVpnClientConfigurationService.getOvpnProfile(device.getId(), OperatingSystemType.WINDOWS)).thenReturn("test".getBytes());
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("vpn.hh.eblocker.com");
        Mockito.when(request.getHeader("deviceType")).thenReturn(OperatingSystemType.WINDOWS.toString());

        ByteBuf buffer = (ByteBuf) controller.downloadClientConf(request, response);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);

        assertArrayEquals("test".getBytes(), bytes);
        Mockito.verify(openVpnClientConfigurationService, Mockito.times(1)).getOvpnProfile(device.getId(), OperatingSystemType.WINDOWS);
        assertEquals("attachment; filename=\"eBlockerMobile-my_eBlocker_-device:001122334455-Windows.ovpn\"", response.getHeader("Content-Disposition"));

        // Client is not allowed to use eBlocker mobile
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(false);
        assertNull(controller.downloadClientConf(request, response));
    }

    @Test
    public void downloadClientConfWithoutCertifcatePresent() throws IOException {
        String newDeviceId = "device:001122334456";
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn(newDeviceId);

        Device device = Mockito.mock(Device.class);
        Mockito.when(device.getId()).thenReturn(newDeviceId);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getUserFriendlyName()).thenReturn("device%//%%-from-Äggard");
        Mockito.when(deviceService.getDeviceById(newDeviceId)).thenReturn(device);
        Mockito.when(openVpnClientConfigurationService.getOvpnProfile(device.getId(), OperatingSystemType.WINDOWS))
                .thenReturn("test-xyz".getBytes());
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("vpn.hh.eblocker.com");
        Mockito.when(openVpnServerService.createClientCertificate(newDeviceId)).thenReturn(true);
        Mockito.when(request.getHeader("deviceType")).thenReturn(OperatingSystemType.WINDOWS.toString());

        ByteBuf buffer = (ByteBuf) controller.downloadClientConf(request, response);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        assertArrayEquals("test-xyz".getBytes(), bytes);
        Mockito.verify(openVpnServerService).createClientCertificate(newDeviceId);

        // Certificate creation failed
        Mockito.when(openVpnServerService.createClientCertificate(newDeviceId)).thenReturn(false);
        assertNull(controller.downloadClientConf(request, response));
    }

    @Test
    public void downloadUnixClientConf() throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(request.getHeader("deviceType")).thenReturn(OperatingSystemType.ANDROID.toString());

        Device device = new Device();
        device.setId(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(openVpnClientConfigurationService.getOvpnProfile(device.getId(), OperatingSystemType.ANDROID)).thenReturn("test".getBytes());

        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("vpn.hh.eblocker.com");

        ByteBuf buffer = (ByteBuf) controller.downloadClientConf(request, response);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);

        assertArrayEquals("test".getBytes(), bytes);
        Mockito.verify(openVpnClientConfigurationService, Mockito.times(1)).getOvpnProfile(device.getId(), OperatingSystemType.ANDROID);
    }

    @Test
    public void downloadInvalidClientConfFails() throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn("--invalid--");

        assertNull(controller.downloadClientConf(request, response));

        assertEquals(400, response.getResponseStatus().code());
    }

    @Test
    public void resetOpenVpnServer() {
        Request request = Mockito.mock(Request.class);

        // Stop and reset server
        Mockito.when(openVpnServerService.stopOpenVpnServer()).thenReturn(true);
        Mockito.when(openVpnServerService.purgeOpenVpnServer()).thenReturn(true);
        assertTrue(controller.resetOpenVpnServerStatus(request, response));
        Mockito.verify(openVpnServerService, Mockito.times(1)).setOpenVpnServerfirstRun(true);
    }

    @Test
    /*
     *  Server couldn't be stopped, so don't purge
     */
    public void resetOpenVpnServerFailed() {
        Request request = Mockito.mock(Request.class);

        assertFalse(controller.resetOpenVpnServerStatus(request, response));
    }

    @Test
    public void enableADisabledDevice() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);

        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(false);

        controller.enableDevice(request, response);

        Mockito.verify(device, Mockito.times(1)).setMobileState(true);
        Mockito.verify(deviceService, Mockito.times(1)).updateDevice(device);
    }

    @Test
    public void enableAnEnabledDevice() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);

        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);

        assertTrue(controller.enableDevice(request, response));

        Mockito.verify(device, Mockito.never()).setMobileState(true);
        Mockito.verify(deviceService, Mockito.never()).updateDevice(device);
    }

    @Test
    public void disableEnabledDevice() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);

        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getId()).thenReturn(deviceId);
        Mockito.when(openVpnServerService.revokeClientCertificate(deviceId))
                .thenReturn(true);

        assertTrue(controller.disableDevice(request, response));

        Mockito.verify(openVpnServerService, Mockito.times(1)).revokeClientCertificate(deviceId);
        Mockito.verify(device, Mockito.times(1)).setMobileState(false);
        Mockito.verify(deviceService, Mockito.times(1)).updateDevice(device);

        // Test revoking failure
        Mockito.when(openVpnServerService.revokeClientCertificate(deviceId)).thenReturn(false);

        assertFalse(controller.disableDevice(request, response));
    }

    @Test
    public void disableEnabledDeviceWithoutCertificates() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);
        String enabledDeviceId = "device:001122334456";

        Mockito.when(request.getHeader("deviceId")).thenReturn(enabledDeviceId);
        Mockito.when(deviceService.getDeviceById(enabledDeviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getId()).thenReturn(enabledDeviceId);

        assertTrue(controller.disableDevice(request, response));

        Mockito.verify(openVpnServerService, Mockito.never()).revokeClientCertificate(enabledDeviceId);
        Mockito.verify(device, Mockito.times(1)).setMobileState(false);
        Mockito.verify(deviceService, Mockito.times(1)).updateDevice(device);
    }

    @Test
    public void disableDisableDeviceWithCertificates() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);

        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(false);
        Mockito.when(device.getId()).thenReturn(deviceId);
        Mockito.when(openVpnServerService.revokeClientCertificate(deviceId))
                .thenReturn(true);

        assertTrue(controller.disableDevice(request, response));

        Mockito.verify(openVpnServerService, Mockito.times(1)).revokeClientCertificate(deviceId);
        Mockito.verify(device, Mockito.times(1)).setMobileState(false);
        Mockito.verify(deviceService, Mockito.times(1)).updateDevice(device);
    }

    @Test
    public void disableDisableDeviceWithoutCertificates() throws IOException {
        Request request = Mockito.mock(Request.class);
        Device device = Mockito.mock(Device.class);
        String disabledDeviceId = "device:001122334456";

        Mockito.when(request.getHeader("deviceId")).thenReturn(disabledDeviceId);
        Mockito.when(deviceService.getDeviceById(disabledDeviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(false);
        Mockito.when(device.getId()).thenReturn(disabledDeviceId);

        assertTrue(controller.disableDevice(request, response));

        Mockito.verify(openVpnServerService, Mockito.never()).revokeClientCertificate(disabledDeviceId);
        Mockito.verify(device, Mockito.never()).setMobileState(false);
        Mockito.verify(deviceService, Mockito.never()).updateDevice(device);
    }

    @Test
    public void generateDownloadUrlTest() throws IOException {
        String authString = "Bearer+asdjaksldaasjdaskjdlsajda";
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);
        Mockito.when(request.getHeader("Authorization")).thenReturn(authString);
        Mockito.when(request.getPath()).thenReturn(String.format("/api/adminconsole/openvpn/certificates/generateDownloadUrl/%s/%s", deviceId, osType.toString()));
        Device device = Mockito.mock(Device.class);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getId()).thenReturn(deviceId);
        Mockito.when(request.getHeader("deviceType")).thenReturn(OperatingSystemType.WINDOWS.toString());

        String expected = String.format("/api/adminconsole/openvpn/certificates/downloadClientConf/%s?deviceType=WINDOWS&Authorization=%s", deviceId, authString);
        assertEquals(expected, controller.generateDownloadUrl(request, response));

        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(false);
        assertNull(controller.generateDownloadUrl(request, response));
    }

    @Test
    public void testInvalidDeviceParameter() throws IOException {
        Request request = Mockito.mock(Request.class);

        assertFalse(controller.enableDevice(request, response));
        assertFalse(controller.disableDevice(request, response));
        assertNull(controller.downloadClientConf(request, response));
        assertNull(controller.generateDownloadUrl(request, response));

        Mockito.when(request.getHeader("deviceId")).thenReturn("asasdjsök");
        Mockito.when(request.getHeader("deviceType")).thenReturn("no-os");
        assertFalse(controller.enableDevice(request, response));
        assertFalse(controller.disableDevice(request, response));
        assertNull(controller.downloadClientConf(request, response));
        assertNull(controller.generateDownloadUrl(request, response));
    }

    @Test
    public void testFilenameNormalizationSpecialCharacters() throws IOException {
        assertFilenameNormalization("attachment; filename=\"eBlockerMobile-my_eBlocker_-aou012345678-Windows.ovpn\"",
                "äöü¹²³¼½¬{[]}0123456789");
    }

    @Test
    public void testFilenameNormalizationNull() throws IOException {
        assertFilenameNormalization(
                "attachment; filename=\"eBlockerMobile-my_eBlocker_-device:001122334455-Windows.ovpn\"", null);
    }

    @Test
    public void testFilenameNormalizationEmpty() throws IOException {
        assertFilenameNormalization(
                "attachment; filename=\"eBlockerMobile-my_eBlocker_-device:001122334455-Windows.ovpn\"", "¹²³¼½¬{[]}");
    }

    private void assertFilenameNormalization(String expectedContentDisposition, String deviceName) throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);

        Device device = Mockito.mock(Device.class);
        Mockito.when(device.getId()).thenReturn(deviceId);
        Mockito.when(device.isEblockerMobileEnabled()).thenReturn(true);
        Mockito.when(device.getName()).thenReturn(deviceName);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);
        Mockito.when(openVpnClientConfigurationService.getOvpnProfile(device.getId(), OperatingSystemType.WINDOWS))
                .thenReturn("test".getBytes());
        Mockito.when(request.getHeader("deviceType")).thenReturn(OperatingSystemType.WINDOWS.toString());

        controller.downloadClientConf(request, response);

        assertEquals(expectedContentDisposition, response.getHeader("Content-Disposition"));
    }

    @Test
    public void testSettingPrivateNetworkAccessAllowed() throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(Boolean.class)).thenReturn(true);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);

        Device device = new Device();
        device.setId(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);

        controller.setPrivateNetworkAccess(request, response);

        Assert.assertTrue(device.isMobilePrivateNetworkAccess());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);
    }

    @Test
    public void testSettingPrivateNetworkAccessProhibited() throws IOException {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(Boolean.class)).thenReturn(false);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceId);

        Device device = new Device();
        device.setId(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);

        controller.setPrivateNetworkAccess(request, response);

        Assert.assertFalse(device.isMobilePrivateNetworkAccess());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);
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
