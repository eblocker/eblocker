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

import io.netty.handler.codec.http.HttpMethod;
import org.eblocker.server.common.PauseDeviceController;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.ShowWelcomeFlags;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.RemainingPause;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DevicePermissionsService;
import org.eblocker.server.http.service.DeviceScanningService;
import org.eblocker.server.http.service.DeviceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.ForbiddenException;
import org.restexpress.exception.NotFoundException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeviceControllerImplTest {
    private DeviceControllerImpl controller;
    private AnonymousService anonymousService;
    private DevicePermissionsService devicePermissionsService;
    private DeviceScanningService deviceScanningService;
    private DeviceService deviceService;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private DeviceFactory deviceFactory;
    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterfaceWrapper;
    private NetworkStateMachine networkStateMachine;
    private OpenVpnService openVpnService;
    private PauseDeviceController pauseDeviceController;

    private Response response;

    @Before
    public void setUp() throws Exception {
        anonymousService = Mockito.mock(AnonymousService.class);
        devicePermissionsService = Mockito.mock(DevicePermissionsService.class);
        deviceScanningService = Mockito.mock(DeviceScanningService.class);
        deviceService = Mockito.mock(DeviceService.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        deviceFactory = Mockito.mock(DeviceFactory.class);

        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);

        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);

        openVpnService = Mockito.mock(OpenVpnService.class);

        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterfaceWrapper.getHardwareAddressHex()).thenReturn("eb0000000000");
        Mockito.when(networkInterfaceWrapper.getAddresses()).thenReturn(Arrays.asList(IpAddress.parse("10.10.10.10"), IpAddress.parse("fe80::10:10:10:10")));

        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        pauseDeviceController = Mockito.mock(PauseDeviceController.class);
        response = new Response();

        TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("0123456789ab", "10.2.3.4", true);
        tdf.addDevice("caffee001122", "10.2.3.5", false);
        tdf.addDevice("1cc11afedcba", "10.2.3.6", false);
        tdf.commit();

        controller = new DeviceControllerImpl(
                anonymousService,
                devicePermissionsService,
                deviceScanningService,
                deviceService,
                deviceRegistrationProperties,
                featureToggleRouter,
                networkInterfaceWrapper,
                networkStateMachine,
                openVpnService,
                pauseDeviceController,
                deviceFactory);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getAllDevices() {
        List<Device> devices = controller.getAllDevices(null, null);

        assertEquals(4, devices.size()); // eBlocker itself is always added to the list
        Map<String, Device> devicesById = devices.stream().collect(Collectors.toMap(Device::getHardwareAddress, Function.identity()));
        Device a = devicesById.get("01:23:45:67:89:ab");
        Device b = devicesById.get("1c:c1:1a:fe:dc:ba");
        Device c = devicesById.get("ca:ff:ee:00:11:22");
        Device eblocker = devicesById.get("eb:00:00:00:00:00");
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(eblocker);
        assertEquals("caffee001122", c.getHardwareAddress(false));
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.4")), a.getIpAddresses());
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.6")), b.getIpAddresses());
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.5")), c.getIpAddresses());
        assertEquals(Arrays.asList(IpAddress.parse("10.10.10.10"), IpAddress.parse("fe80::10:10:10:10")), eblocker.getIpAddresses());
        assertTrue(a.isEnabled());
        assertFalse(b.isEnabled());
        assertFalse(c.isEnabled());
        assertNull(a.getVendor());
        assertNull(c.getVendor());
    }

    @Test
    public void saveDevice() throws IOException {
        Device device = TestDeviceFactory.createDevice("0123456789ab", "10.2.3.4", true);

        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT, "/some/path");
        request.addHeader("deviceId", "device:0123456789ab");

        Object returnValue = controller.updateDevice(request, response);

        verify(deviceService).updateDevice(device);
        verify(networkStateMachine, never()).deviceStateChanged(); // device is still enabled
        assertEquals(device, returnValue);
    }

    @Test
    public void firewallIsUpdatedForEnabledDevice() throws IOException {
        Device device = TestDeviceFactory.createDevice("caffee001122", "10.2.3.5", true);

        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT, "/some/path");
        request.addHeader("deviceId", "device:caffee001122");

        Object returnValue = controller.updateDevice(request, response);

        verify(deviceService).updateDevice(device);
        verify(networkStateMachine).deviceStateChanged(device);
        assertEquals(device, returnValue);
    }

    @Test
    public void firewallIsEnabledForWhitelistedDevice() throws IOException {
        Device device = TestDeviceFactory.createDevice("0123456789ab", "10.2.3.4", false);

        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT, "/some/path");
        request.addHeader("deviceId", "device:0123456789ab");

        Object returnValue = controller.updateDevice(request, response);

        verify(deviceService).updateDevice(device);
        verify(networkStateMachine).deviceStateChanged(device);
        assertEquals(device, returnValue);
    }

    @Test
    public void showWarningsSet() {
        Device device = deviceService.getDeviceById("device:0123456789ab");
        Request getRequest = ControllerTestUtils.mockRequestByDevice(device);
        IpAddress ip = device.getIpAddresses().get(0);

        when(deviceService.getDeviceByIp(ip)).thenReturn(device);

        // If nothing else is given, the settings are the default settings
        Object returnValue = controller.getShowWarnings(getRequest, response);
        assertTrue((boolean) returnValue);

        Map<String, Boolean> map = new TreeMap<>();
        map.put("showWarnings", false);
        Request postRequest = ControllerTestUtils.mockRequestByDevice(device);
        when(postRequest.getBodyAs(Map.class)).thenReturn(map);

        // Change settings
        returnValue = controller.postShowWarnings(postRequest, response);
        // See if settings are changed
        returnValue = controller.getShowWarnings(getRequest, response);
        assertFalse((boolean) returnValue);

        // Change settings again
        map.put("showWarnings", true);
        returnValue = controller.postShowWarnings(postRequest, response);
        // See if settings are changed
        returnValue = controller.getShowWarnings(getRequest, response);
        assertTrue((boolean) returnValue);
    }

    @Test
    public void testDeleteDevice() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn("device:112233445566");
        Response response = Mockito.mock(Response.class);
        Mockito.when(deviceService.delete(Mockito.any(Device.class))).thenReturn(new Device());

        controller.deleteDevice(request, response);

        Mockito.verify(deviceService).delete(Mockito.any(Device.class));
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testDeleteInvalidDevice() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("deviceId")).thenReturn("device:112233445566");
        Response response = Mockito.mock(Response.class);
        Mockito.when(deviceService.delete(Mockito.any(Device.class))).thenReturn(null);

        controller.deleteDevice(request, response);

        Mockito.verifyZeroInteractions(networkStateMachine);
    }

    @Test
    public void getPause() {
        Device device = deviceService.getDeviceById("device:0123456789ab");
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        when(pauseDeviceController.getRemainingPause(device)).thenReturn(new RemainingPause(42L));
        when(devicePermissionsService.operatingUserMayPause(device)).thenReturn(false);
        RemainingPause result = controller.getPauseCurrentDevice(request, response);
        assertEquals(Long.valueOf(42), result.getPausing());
    }

    @Test
    public void testUnpauseDevice() throws IOException {
        // The backend knows the device as paused
        Device backendDevice = Mockito.mock(Device.class);
        when(backendDevice.isEnabled()).thenReturn(false);
        when(backendDevice.isPaused()).thenReturn(true);
        when(backendDevice.getDefaultSystemUser()).thenReturn(-1);
        when(deviceService.getDeviceById(Mockito.eq("device:0123456789ab"))).thenReturn(backendDevice);
        RemainingPause remainingPause = new RemainingPause(0L);
        when(pauseDeviceController.pauseDevice(Mockito.any(Device.class), Mockito.eq(0L))).thenReturn(remainingPause);

        // Paused (and disabled) device has been enabled in the frontend...
        Device device = TestDeviceFactory.createDevice("0123456789ab", "10.2.3.4", true);
        device.setPaused(true);
        device.setEnabled(true);
        // and is now processed in the backend
        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT, "/some/path");
        request.addHeader("deviceId", "device:0123456789ab");
        Object returnValue = controller.updateDevice(request, response);

        // In real application such a call would be made, but by pauseDeviceController which in the tests is a Mock
        verify(deviceService, never()).updateDevice(device);
        verify(pauseDeviceController).pauseDevice(Mockito.any(Device.class), Mockito.eq(0L));
        assertEquals(device, returnValue);
        assertFalse(((Device) returnValue).isPaused());
    }

    @Test
    public void testResetDevice() {
        String deviceId = "device:0123456789ab";
        Request request = ControllerTestUtils.createRequest(deviceId, HttpMethod.PUT,
                "/api/adminconsole/devices/reset/device:0123456789ab");
        request.addHeader("deviceId", deviceId);
        Device resetDevice = new Device();
        resetDevice.setId(deviceId);
        Mockito.when(deviceService.resetDevice(deviceId)).thenReturn(resetDevice);
        Object returnValue = controller.resetDevice(request, response);

        verify(deviceService).resetDevice(deviceId);
        verify(networkStateMachine).deviceStateChanged();
        assertEquals(resetDevice, (Device) returnValue);
        assertNotNull(returnValue);
    }

    @Test
    public void testResetNonExistingDevice() {
        String deviceId = "device:0123456789ab";
        Request request = ControllerTestUtils.createRequest(deviceId, HttpMethod.PUT,
                "/api/adminconsole/devices/reset/device:0123456789ab");
        request.addHeader("deviceId", deviceId);
        Device resetDevice = new Device();
        resetDevice.setId(deviceId);
        Object returnValue = controller.resetDevice(request, response);

        verify(deviceService).resetDevice(deviceId);
        verify(networkStateMachine, Mockito.never()).deviceStateChanged();
        assertNull(returnValue);
    }

    @Test
    public void disablingDeviceStopsVpn() throws IOException {
        Device backendDevice = Mockito.mock(Device.class);
        when(backendDevice.isEnabled()).thenReturn(true);
        when(backendDevice.isUseAnonymizationService()).thenReturn(true);
        when(backendDevice.getDefaultSystemUser()).thenReturn(-1);
        when(deviceService.getDeviceById(Mockito.eq("device:0123456789ab"))).thenReturn(backendDevice);

        Device device = TestDeviceFactory.createDevice("0123456789ab", "10.2.3.4", false);

        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT,
                "/api/adminconsole/devices/device:0123456789ab");
        request.addHeader("deviceId", "device:0123456789ab");

        Object returnValue = controller.updateDevice(request, response);

        verify(anonymousService).disableVpn(device);
        verify(deviceService).updateDevice(device);
        verify(deviceService).updateDevice(device);
        assertEquals(device, returnValue);
    }

    @Test
    public void enablingDeviceStartsVpn() throws IOException {
        Device backendDevice = Mockito.mock(Device.class);
        when(backendDevice.isEnabled()).thenReturn(false);
        when(backendDevice.isUseAnonymizationService()).thenReturn(true);
        when(backendDevice.getDefaultSystemUser()).thenReturn(-1);
        when(deviceService.getDeviceById(Mockito.eq("device:0123456789ab"))).thenReturn(backendDevice);

        Device device = TestDeviceFactory.createDevice("0123456789ab", "10.2.3.4", true);
        device.setUseAnonymizationService(true);
        Integer vpnProfileId = 23;
        device.setUseVPNProfileID(vpnProfileId);
        VpnProfile profile = Mockito.mock(VpnProfile.class);
        Mockito.when(openVpnService.getVpnProfileById(vpnProfileId)).thenReturn(profile);

        Request request = ControllerTestUtils.createRequest(ControllerTestUtils.toJSON(device), HttpMethod.PUT,
                "/api/adminconsole/devices/device:0123456789ab");
        request.addHeader("deviceId", "device:0123456789ab");

        Object returnValue = controller.updateDevice(request, response);

        verify(networkStateMachine).deviceStateChanged(device);
        verify(anonymousService).enableVpn(device, profile);
        verify(deviceService).updateDevice(device);
        assertEquals(device, returnValue);
    }

    @Test
    public void testEblockerDeviceIp6Disabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);

        List<Device> devices = controller.getAllDevices(null, null);
        devices.stream().filter(d -> "eb00000000".equals(d.getHardwareAddress(false))).findAny().orElse(null);
        assertEquals(4, devices.size()); // eBlocker itself is always added to the list
        Map<String, Device> devicesById = devices.stream().collect(Collectors.toMap(Device::getHardwareAddress, Function.identity()));
        Device a = devicesById.get("01:23:45:67:89:ab");
        Device b = devicesById.get("1c:c1:1a:fe:dc:ba");
        Device c = devicesById.get("ca:ff:ee:00:11:22");
        Device eblocker = devicesById.get("eb:00:00:00:00:00");
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(eblocker);
        assertEquals("caffee001122", c.getHardwareAddress(false));
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.4")), a.getIpAddresses());
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.6")), b.getIpAddresses());
        assertEquals(Collections.singletonList(IpAddress.parse("10.2.3.5")), c.getIpAddresses());
        assertEquals(Collections.singletonList(IpAddress.parse("10.10.10.10")), eblocker.getIpAddresses());
        assertTrue(a.isEnabled());
        assertFalse(b.isEnabled());
        assertFalse(c.isEnabled());
        assertNull(a.getVendor());
        assertNull(c.getVendor());
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateNonExistingCurrentDevice() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.42", true);
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        Mockito.when(request.getHeader("deviceId")).thenReturn(device.getId());
        controller.updateDeviceDashboard(request, response);
    }

    @Test(expected = ForbiddenException.class)
    public void testUpdateCurrentDeviceForbidden() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.42", true);
        Mockito.when(deviceService.getDeviceByIp(device.getIpAddresses().get(0))).thenReturn(device);
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        Mockito.when(request.getHeader("deviceId")).thenReturn(device.getId());
        Mockito.when(devicePermissionsService.operatingUserMayUpdate(device)).thenReturn(false);
        controller.updateDeviceDashboard(request, response);
    }

    @Test
    public void testUpdateCurrentDevice() {
        Device deviceFromRequest = TestDeviceFactory.createDevice("abcdef000000", "192.168.1.42", "John's Phone", true);
        Device deviceFromDB = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.42", "Jane's Phone", true);
        Request request = ControllerTestUtils.mockRequestByDevice(deviceFromRequest);
        Mockito.when(deviceService.getDeviceByIp(deviceFromRequest.getIpAddresses().get(0))).thenReturn(deviceFromDB);
        Mockito.when(deviceService.getDeviceById(deviceFromDB.getId())).thenReturn(deviceFromDB);
        Mockito.when(devicePermissionsService.operatingUserMayUpdate(deviceFromDB)).thenReturn(true);
        Mockito.when(request.getBodyAs(Device.class)).thenReturn(deviceFromRequest);
        Mockito.when(request.getHeader("deviceId")).thenReturn(deviceFromDB.getId());
        Object result = controller.updateDeviceDashboard(request, response);
        ArgumentCaptor<Device> updated = ArgumentCaptor.forClass(Device.class);
        Mockito.verify(deviceService).updateDevice(updated.capture());
        assertEquals(updated.getValue().getName(), deviceFromRequest.getName());
        assertTrue(result instanceof Device);
        Device returned = (Device) result;
        assertEquals(returned.getName(), deviceFromRequest.getName());
        assertEquals(returned.getId(), deviceFromDB.getId()); // Device ID from request must be ignored!
    }

    @Test
    public void testUpdateShowWelcomeFlags() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.42", true);
        device.setShowWelcomePage(true);
        device.setShowBookmarkDialog(false);
        ShowWelcomeFlags flags = new ShowWelcomeFlags(false, true);
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        Mockito.when(deviceService.getDeviceByIp(device.getIpAddresses().get(0))).thenReturn(device);
        Mockito.when(request.getBodyAs(ShowWelcomeFlags.class)).thenReturn(flags);
        controller.updateShowWelcomeFlags(request, response);
        ArgumentCaptor<Device> updated = ArgumentCaptor.forClass(Device.class);
        Mockito.verify(deviceService).updateDevice(updated.capture());
        assertFalse(updated.getValue().isShowWelcomePage());
        assertTrue(updated.getValue().isShowBookmarkDialog());
    }
}
