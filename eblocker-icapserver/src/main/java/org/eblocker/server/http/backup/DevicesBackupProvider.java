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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class DevicesBackupProvider extends BackupProvider {
    public static final String DEVICES_ENTRY = "eblocker-config/devices.json";
    private final DeviceService deviceService;
    private final UserService userService;
    private final DeviceFactory deviceFactory;
    private final OpenVpnService openVpnService;
    private static final Logger LOG = LoggerFactory.getLogger(DevicesBackupProvider.class);

    @Inject
    public DevicesBackupProvider(DeviceService deviceService, UserService userService, DeviceFactory deviceFactory,
                                 OpenVpnService openVpnService) {
        this.deviceService = deviceService;
        this.userService = userService;
        this.deviceFactory = deviceFactory;
        this.openVpnService = openVpnService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream, CryptoService cryptoService) throws IOException {
        List<Device> allDevices = deviceService.getDevices(true).stream()
                .filter(device -> !device.isEblocker())
                .collect(Collectors.toList());
        JarEntry entry = new JarEntry(DEVICES_ENTRY);
        outputStream.putNextEntry(entry);
        outputStream.write(objectMapper.writeValueAsBytes(allDevices));
        outputStream.closeEntry();
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, CryptoService cryptoService, int schemaVersion) throws IOException {
        List<Device> devicesToRestore;
        JarEntry entry = inputStream.getNextJarEntry();
        if (entry.getName().equals(DEVICES_ENTRY)) {
            devicesToRestore = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            inputStream.closeEntry();
        } else {
            throw new EblockerException("Expected entry " + DEVICES_ENTRY + ", got " + entry.getName());
        }
        if (devicesToRestore != null && !devicesToRestore.isEmpty()) {
            // FUTURE: When the format or anything related to the Device
            // changes, apply the migrations to them here, using the parameter
            // schemaVersion
            restoreDevices(devicesToRestore);
        } else {
            throw new EblockerException("Deserialized backup object is null");
        }
    }

    private void restoreDevices(List<Device> devicesToRestore) {
        for (Device deviceToRestore : devicesToRestore) {
            LOG.info("restoring device {} {}", deviceToRestore.getName(), deviceToRestore.getId());
            Device existingDevice = deviceService.getDeviceById(deviceToRestore.getId());

            if (existingDevice == null) {
                existingDevice = deviceFactory.createDevice(deviceToRestore.getId(),
                        Collections.emptyList(), deviceToRestore.isIpAddressFixed());
                userService.restoreDefaultSystemUserAsUsers(existingDevice);
            }

            // This attribute must be restored for regular devices and the gateway alike
            existingDevice.setName(deviceToRestore.getName());

            // The following attributes only have a meaning in case of a regular
            // device, i.e. neither eBlocker nor gateway
            if (!deviceToRestore.isGateway() && !deviceToRestore.isEblocker()) {
                existingDevice.setAreDeviceMessagesSettingsDefault(deviceToRestore.getAreDeviceMessagesSettingsDefault());

                // Assigned User: If the device to restore is still in the list,
                // preserve the assigned user. If it is not in the list, the
                // default system user is used
                existingDevice.setControlBarAutoMode(deviceToRestore.isControlBarAutoMode());
                // If paused, it is enabled again; the pause does not survive
                // restoring the backup
                existingDevice.setEnabled(deviceToRestore.isEnabled() || deviceToRestore.isPaused());
                existingDevice.setFilterMode(deviceToRestore.getFilterMode());
                existingDevice.setFilterAdsEnabled(deviceToRestore.isFilterAdsEnabled());
                existingDevice
                        .setFilterTrackersEnabled(deviceToRestore.isFilterTrackersEnabled());
                // No guarantee the certificate is still the same
                existingDevice.setHasRootCAInstalled(false);
                existingDevice.setIconMode(deviceToRestore.getIconMode());
                existingDevice.setIconPosition(deviceToRestore.getIconPosition());
                // IP adressess are not copied since they do not rely on
                // whatever the user did but are provided by DHCP or other
                // service
                existingDevice.setIpAddressFixed(deviceToRestore.isIpAddressFixed());
                // Both eBlocker- and gateway-flag default to false
                // Future: Currently, the VPN settings are not saved
                existingDevice.setIsVpnClient(false);
                existingDevice.setMalwareFilterEnabled(deviceToRestore.isMalwareFilterEnabled());
                existingDevice.setMessageShowAlert(deviceToRestore.isMessageShowAlert());
                existingDevice.setMessageShowInfo(deviceToRestore.isMessageShowInfo());
                existingDevice.setMobilePrivateNetworkAccess(deviceToRestore.isMobilePrivateNetworkAccess());
                existingDevice.setMobileState(deviceToRestore.isEblockerMobileEnabled());
                // Online-flag defaults to false, if such a device is currently online, the flag is set accordingly

                // Operating User: If the device to restore is still in the
                // list, preserve the assigned user. If it is not in the ist,
                // the default system user is used

                // The pause does not survive restoring the backup. It is
                // reactivated at the end since that also updats the database
                existingDevice.setRouteThroughTor(deviceToRestore.isRoutedThroughTor());
                existingDevice.setShowBookmarkDialog(deviceToRestore.isShowBookmarkDialog());
                existingDevice.setShowDnsFilterInfoDialog(deviceToRestore.isShowDnsFilterInfoDialog());
                existingDevice.setShowPauseDialog(deviceToRestore.isShowPauseDialog());
                existingDevice.setShowPauseDialogDoNotShowAgain(deviceToRestore.isShowPauseDialogDoNotShowAgain());
                existingDevice.setShowWelcomePage(deviceToRestore.isShowWelcomePage());
                existingDevice.setSslEnabled(deviceToRestore.isSslEnabled());
                existingDevice.setSslRecordErrorsEnabled(deviceToRestore.isSslRecordErrorsEnabled());
                existingDevice.setUseAnonymizationService(deviceToRestore.isUseAnonymizationService());
                // Check VPN Profile ID for consistency before copying
                Integer vpnProfileId = deviceToRestore.getUseVPNProfileID();
                if (vpnProfileId == null || openVpnService.getVpnProfileById(vpnProfileId) == null) {
                    // Use default value
                    existingDevice.setUseVPNProfileID(null);
                    // If routed through tor, keep using anonymization. If no
                    // VPN Profile can be used and tor is not used, use no
                    // anonymization
                    existingDevice.setUseAnonymizationService(
                            deviceToRestore.isUseAnonymizationService() && deviceToRestore.isRoutedThroughTor());
                } else {
                    // Copy value
                    existingDevice.setUseVPNProfileID(deviceToRestore.getUseVPNProfileID());
                }
                existingDevice.setVendor(deviceToRestore.getVendor());
            }
            deviceService.updateDevice(existingDevice);
            LOG.info("Device copied and written back");
        }
    }
}
