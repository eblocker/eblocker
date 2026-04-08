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
package org.eblocker.server.http.backup;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Device.DisplayIconPosition;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.DisplayIconMode;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DevicesBackupProviderTest extends BackupProviderTestBase {
    private DeviceService deviceService;
    private UserService userService;
    private DeviceFactory deviceFactory;
    private OpenVpnService openVpnService;
    private DevicesBackupProvider provider;
    private int newDefaultSystemuser = 10;

    @BeforeEach
    public void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        userService = Mockito.mock(UserService.class);
        deviceFactory = Mockito.mock(DeviceFactory.class);
        openVpnService = Mockito.mock(OpenVpnService.class);
        provider = new DevicesBackupProvider(deviceService, userService, deviceFactory, openVpnService);
    }

    @Test
    public void testExportImport() throws IOException {
        // Devices before Backup
        Device eblockerBefore = new Device();
        eblockerBefore.setId("device:00:00:00:00:00:00");
        eblockerBefore.setIsEblocker(true);

        Device gatewayBefore = new Device();
        gatewayBefore.setIsGateway(true);
        gatewayBefore.setId("device:11:11:11:11:11:11");
        gatewayBefore.setName("Mein Router");

        // Here, everything is set to true (as far as applicable)
        Device computerBefore = new Device();
        computerBefore.setOnline(true);
        computerBefore.setId("device:22:22:22:22:22:22");
        computerBefore.setName("Mein Computer");
        // More attributes
        computerBefore.setAreDeviceMessagesSettingsDefault(true);
        computerBefore.setAssignedUser(123);
        computerBefore.setControlBarAutoMode(true);
        computerBefore.setDefaultSystemUser(1234);
        computerBefore.setEnabled(true);
        computerBefore.setFilterMode(FilterMode.AUTOMATIC);
        computerBefore.setFilterAdsEnabled(true);
        computerBefore.setFilterTrackersEnabled(true);
        computerBefore.setHasRootCAInstalled(true);
        computerBefore.setIconMode(DisplayIconMode.ON);
        computerBefore.setIconPosition(DisplayIconPosition.RIGHT);
        List<IpAddress> ipAddressesBefore = new ArrayList<>();
        ipAddressesBefore.add(Ip4Address.parse("192.168.0.2"));
        computerBefore.setIpAddresses(ipAddressesBefore);
        computerBefore.setIpAddressFixed(true);
        computerBefore.setIsVpnClient(true);
        computerBefore.setMalwareFilterEnabled(true);
        computerBefore.setMessageShowAlert(true);
        computerBefore.setMessageShowInfo(true);
        computerBefore.setMobilePrivateNetworkAccess(true);
        computerBefore.setMobileState(true);
        computerBefore.setOperatingUser(12345);
        computerBefore.setPaused(true);
        computerBefore.setRouteThroughTor(true);
        computerBefore.setShowBookmarkDialog(true);
        computerBefore.setShowDnsFilterInfoDialog(true);
        computerBefore.setShowPauseDialog(true);
        computerBefore.setShowPauseDialogDoNotShowAgain(true);
        computerBefore.setShowWelcomePage(true);
        computerBefore.setSslEnabled(true);
        computerBefore.setSslRecordErrorsEnabled(true);
        computerBefore.setUseAnonymizationService(true);
        Integer vpnProfileId = 1122;
        computerBefore.setUseVPNProfileID(vpnProfileId);
        computerBefore.setVendor("Computer-Company, Inc.");
        computerBefore.setLastSeen(Instant.ofEpochMilli(1703172239000L));

        List<Device> devicesBefore = new ArrayList<Device>(
                Arrays.asList(eblockerBefore, gatewayBefore, computerBefore));

        Mockito.when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(devicesBefore);

        VpnProfile vpnProfile = new OpenVpnProfile();
        Mockito.when(openVpnService.getVpnProfileById(Mockito.eq(vpnProfileId))).thenReturn(vpnProfile);
        // Devices after Backup - they have been modified
        Device eblockerAfter = new Device();
        eblockerAfter.setId("device:00:00:00:00:00:00");
        eblockerAfter.setIsEblocker(true);
        Mockito.when(deviceService.getDeviceById(Mockito.eq(eblockerAfter.getId()))).thenReturn(eblockerAfter);

        Device gatewayAfter = new Device();
        gatewayAfter.setIsGateway(true);
        gatewayAfter.setId("device:11:11:11:11:11:11");
        gatewayAfter.setName("Mein Router modifiziert");
        Mockito.when(deviceService.getDeviceById(Mockito.eq(gatewayAfter.getId()))).thenReturn(gatewayAfter);

        Device computerAfter = new Device();
        computerAfter.setOnline(true);
        computerAfter.setId("device:22:22:22:22:22:22");
        computerAfter.setName("Mein Computer modifiziert");
        // More attributes
        computerAfter.setAreDeviceMessagesSettingsDefault(false);
        int computerAfterAssignedUser = 1230;
        computerAfter.setAssignedUser(computerAfterAssignedUser);
        computerAfter.setControlBarAutoMode(false);
        int computerAfterDefaultSystemUser = 12340;
        computerAfter.setDefaultSystemUser(computerAfterDefaultSystemUser);
        computerAfter.setEnabled(false);
        computerAfter.setFilterMode(FilterMode.NONE);
        computerAfter.setFilterAdsEnabled(false);
        computerAfter.setFilterTrackersEnabled(false);
        computerAfter.setHasRootCAInstalled(false);
        computerAfter.setIconMode(DisplayIconMode.OFF);
        computerAfter.setIconPosition(DisplayIconPosition.LEFT);
        List<IpAddress> ipAddressesAfter = new ArrayList<>();
        ipAddressesAfter.add(Ip4Address.parse("192.168.0.22"));
        computerAfter.setIpAddresses(ipAddressesAfter);
        computerAfter.setIpAddressFixed(false);
        computerAfter.setIsVpnClient(false);
        computerAfter.setMalwareFilterEnabled(false);
        computerAfter.setMessageShowAlert(false);
        computerAfter.setMessageShowInfo(false);
        computerAfter.setMobilePrivateNetworkAccess(false);
        computerAfter.setMobileState(false);
        int computerAfterOperatingUser = 123450;
        computerAfter.setOperatingUser(computerAfterOperatingUser);
        computerAfter.setPaused(false);// The pause does not survive the backup
        computerAfter.setRouteThroughTor(false);
        computerAfter.setShowBookmarkDialog(false);
        computerAfter.setShowDnsFilterInfoDialog(false);
        computerAfter.setShowPauseDialog(false);
        computerAfter.setShowPauseDialogDoNotShowAgain(false);
        computerAfter.setShowWelcomePage(false);
        computerAfter.setSslEnabled(false);
        computerAfter.setSslRecordErrorsEnabled(false);
        computerAfter.setUseAnonymizationService(false);
        computerAfter.setUseVPNProfileID(11220);
        computerAfter.setVendor("Another Computer-Company, Inc.");
        Mockito.when(deviceService.getDeviceById(Mockito.eq(computerAfter.getId()))).thenReturn(computerAfter);

        Mockito.when(
                deviceFactory.createDevice(Mockito.eq("device:11:11:11:11:11:11"), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(gatewayAfter);
        Mockito.when(
                deviceFactory.createDevice(Mockito.eq("device:22:22:22:22:22:22"), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(computerAfter);

        exportVerifyImport(provider);

        //        Mockito.verify(deviceService).updateDevice(gatewayAfter);
        //        Mockito.verify(deviceService).updateDevice(computerAfter);
        ArgumentCaptor<Device> argCaptor = ArgumentCaptor.forClass(Device.class);
        Mockito.verify(deviceService, Mockito.times(2)).updateDevice(argCaptor.capture());
        Device restoredGateway = argCaptor.getAllValues().get(0);
        Device restoredDevice = argCaptor.getAllValues().get(1);
        // Verify gateway has been restored correctly
        assertTrue(restoredGateway.isGateway());
        assertFalse(restoredGateway.isEblocker());
        assertEquals(restoredGateway.getName(), gatewayAfter.getName());

        // Verify computer has been restored correctly
        // User References are set to default value
        assertTrue(restoredDevice.isOnline());// The device stayed online
        assertEquals(computerBefore.getName(), restoredDevice.getName());
        assertEquals(computerBefore.getAreDeviceMessagesSettingsDefault(),
                restoredDevice.getAreDeviceMessagesSettingsDefault());
        // User References of existing device are not restored
        assertEquals(computerAfterAssignedUser, restoredDevice.getAssignedUser());
        assertEquals(computerBefore.isControlBarAutoMode(), restoredDevice.isControlBarAutoMode());
        // User References of existing device are not restored
        assertEquals(computerAfterDefaultSystemUser, restoredDevice.getDefaultSystemUser());
        assertEquals(computerBefore.isEnabled(), restoredDevice.isEnabled());
        assertEquals(computerBefore.getFilterMode(), restoredDevice.getFilterMode());
        assertEquals(computerBefore.isFilterAdsEnabled(),
                restoredDevice.isFilterAdsEnabled());
        assertEquals(computerBefore.isFilterTrackersEnabled(),
                restoredDevice.isFilterTrackersEnabled());
        // No guarantee the installed certificate is still used by the eBlocker
        assertFalse(restoredDevice.hasRootCAInstalled());
        assertEquals(computerBefore.getIconMode(), restoredDevice.getIconMode());
        assertEquals(computerBefore.getIconPosition(), restoredDevice.getIconPosition());
        assertTrue(restoredDevice.getIpAddresses().size() == 1);
        assertEquals(Ip4Address.parse("192.168.0.22"), restoredDevice.getIpAddresses().get(0));
        assertEquals(computerBefore.isIpAddressFixed(), restoredDevice.isIpAddressFixed());
        // FUTURE: VPN settings are currently not saved
        //        assertEquals(computerBefore.isVpnClient(), computerAfter.isVpnClient());
        assertEquals(computerBefore.isMalwareFilterEnabled(), restoredDevice.isMalwareFilterEnabled());
        assertEquals(computerBefore.isMessageShowAlert(), restoredDevice.isMessageShowAlert());
        assertEquals(computerBefore.isMessageShowInfo(), restoredDevice.isMessageShowInfo());
        assertEquals(computerBefore.isMobilePrivateNetworkAccess(),
                restoredDevice.isMobilePrivateNetworkAccess());
        assertEquals(computerBefore.isEblockerMobileEnabled(), restoredDevice.isEblockerMobileEnabled());
        // User References of existing device are not restored
        assertEquals(computerAfterOperatingUser, restoredDevice.getOperatingUser());
        // The pause does not survive the backup
        assertFalse(restoredDevice.isPaused());
        assertEquals(computerBefore.isRoutedThroughTor(), restoredDevice.isRoutedThroughTor());
        assertEquals(computerBefore.isShowBookmarkDialog(), restoredDevice.isShowBookmarkDialog());
        assertEquals(computerBefore.isShowDnsFilterInfoDialog(), restoredDevice.isShowDnsFilterInfoDialog());
        assertEquals(computerBefore.isShowPauseDialog(), restoredDevice.isShowPauseDialog());
        assertEquals(computerBefore.isShowPauseDialogDoNotShowAgain(),
                restoredDevice.isShowPauseDialogDoNotShowAgain());
        assertEquals(computerBefore.isShowWelcomePage(), restoredDevice.isShowWelcomePage());
        assertEquals(computerBefore.isSslEnabled(), restoredDevice.isSslEnabled());
        assertEquals(computerBefore.isSslRecordErrorsEnabled(), restoredDevice.isSslRecordErrorsEnabled());
        assertEquals(computerBefore.isUseAnonymizationService(), restoredDevice.isUseAnonymizationService());
        // VPN Profile of existing device is not restored
        assertNull(restoredDevice.getUseVPNProfileID());
        assertEquals(computerBefore.getVendor(), restoredDevice.getVendor());
        assertNull(restoredDevice.getLastSeen()); // lastSeen is not restored
    }

    @Test
    public void testExportImportDeviceDoesNotExist() throws IOException {
        // Devices before Backup

        // Here, everything is set to true (as far as applicable)
        Device computerBefore = new Device();
        computerBefore.setOnline(true);
        computerBefore.setId("device:22:22:22:22:22:22");
        computerBefore.setName("Mein Computer");
        // More attributes (not all are needed for this test)
        computerBefore.setAssignedUser(123);
        computerBefore.setDefaultSystemUser(1234);
        computerBefore.setOperatingUser(12345);
        Integer vpnProfileId = 1122;
        computerBefore.setUseVPNProfileID(vpnProfileId);

        List<Device> devicesBefore = new ArrayList<Device>(
                Arrays.asList(computerBefore));

        Mockito.when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(devicesBefore);
        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                Device device = (Device) invocation.getArguments()[0];
                device.setDefaultSystemUser(newDefaultSystemuser);
                device.setAssignedUser(newDefaultSystemuser);
                device.setOperatingUser(newDefaultSystemuser);
                return null;
            }
        }).when(userService).restoreDefaultSystemUserAsUsers(Mockito.any(Device.class));

        // Newly generated device to be returned by device factory
        Device computerAfter = new Device();
        computerAfter.setId("device:22:22:22:22:22:22");
        computerAfter.setName("Name of producer (No. 1)");

        Mockito.when(
                deviceFactory.createDevice(Mockito.eq("device:22:22:22:22:22:22"), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(computerAfter);

        exportVerifyImport(provider);

        // Verify computer has been restored correctly
        ArgumentCaptor<Device> argCaptor = ArgumentCaptor.forClass(Device.class);
        Mockito.verify(deviceService).updateDevice(argCaptor.capture());
        Device restoredDevice = argCaptor.getValue();

        // User References are set to default value
        assertEquals(newDefaultSystemuser, restoredDevice.getAssignedUser());
        assertEquals(newDefaultSystemuser, restoredDevice.getDefaultSystemUser());
        assertEquals(newDefaultSystemuser, restoredDevice.getOperatingUser());
        // VPN Profile Reference is set to default value
        assertEquals(null, restoredDevice.getUseVPNProfileID());
    }
}
