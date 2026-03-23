/*
 * Copyright 2026 eBlocker Open Source GmbH
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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.openvpn.OpenVpnProfileFiles;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class OpenVpnClientBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpenVpnClientBackupProvider.class);
    public static final String OPENVPN_CLIENTS_ENTRY = "eblocker-config/openVpnClients.json";

    private final OpenVpnService openVpnService;
    private final OpenVpnProfileFiles openVpnProfileFiles;
    private final DeviceService deviceService;

    @AssistedInject
    public OpenVpnClientBackupProvider(OpenVpnService openVpnService,
                                       OpenVpnProfileFiles openVpnProfileFiles,
                                       DeviceService deviceService,
                                       @Assisted @Nullable CryptoService cryptoService) {
        super(cryptoService);
        this.openVpnService = openVpnService;
        this.openVpnProfileFiles = openVpnProfileFiles;
        this.deviceService = deviceService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        List<OpenVpnClientBackup> backup = createBackups();
        writeNextEntry(outputStream, OPENVPN_CLIENTS_ENTRY, objectMapper.writeValueAsBytes(backup));
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, false);
    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, true);
    }

    private void importConfiguration(JarInputStream inputStream, int schemaVersion, boolean dryRun) throws IOException {
        getNextEntry(inputStream, OPENVPN_CLIENTS_ENTRY);
        List<OpenVpnClientBackup> backups = objectMapper.readValue(inputStream, new TypeReference<List<OpenVpnClientBackup>>() {});
        if (backups == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        if (!canDecrypt()) {
            addWarning(BackupWarning.NO_PASSWORD_OPENVPN_CLIENTS_NOT_IMPORTED);
            return;
        }

        if (!dryRun) {
            restoreBackups(backups);
        }
    }

    private List<OpenVpnClientBackup> createBackups() throws IOException {
        List<OpenVpnClientBackup> result = new ArrayList<>();
        Collection<VpnProfile> profiles = openVpnService.getVpnProfiles();
        Set<Device> vpnClientDevices = deviceService.getDevices(true).stream()
                .filter(device -> device.isUseAnonymizationService() && !device.isRoutedThroughTor() && device.getUseVPNProfileID() != null)
                .collect(Collectors.toSet());
        for (VpnProfile profile : profiles) {
            Integer profileId = profile.getId();
            OpenVpnClientBackup backup = new OpenVpnClientBackup();

            // get OpenVpnProfile with real password (not masked):
            OpenVpnProfile openVpnProfile = openVpnService.getOpenVpnProfileById(profileId);
            backup.setProfile(openVpnProfile);

            OpenVpnConfiguration configuration = openVpnService.getProfileClientConfig(profileId);
            backup.setConfiguration(configuration);

            addExternalFiles(backup, profileId, configuration);

            backup.setDeviceIds(vpnClientDevices.stream()
                    .filter(device -> device.getUseVPNProfileID().equals(profileId))
                    .map(Device::getId)
                    .collect(Collectors.toList()));
            result.add(backup);
        }
        return result;
    }

    private void addExternalFiles(OpenVpnClientBackup backup, Integer profileId, OpenVpnConfiguration configuration) throws IOException {
        Map<String, String> inlined = configuration.getInlinedContentByName();
        List<EncryptedContainer> externalFiles = new ArrayList<>();
        for (String option : inlined.keySet()) {
            EncryptedContainer container = new EncryptedContainer();
            container.setName(option);
            container.setContent(openVpnProfileFiles.readConfigOptionFile(profileId, option));
            externalFiles.add(container);
        }
        backup.setExternalFiles(externalFiles);
    }

    private void restoreBackups(List<OpenVpnClientBackup> backups) throws IOException {
        // remove existing profiles
        for (VpnProfile profile: openVpnService.getVpnProfiles()) {
            openVpnService.deleteVpnProfile(profile.getId());
        }

        // restore profiles
        for (OpenVpnClientBackup backup: backups) {
            // Save a new empty profile to get a new ID:
            OpenVpnProfile newProfile = openVpnService.saveProfile(new OpenVpnProfile());
            Integer newId = newProfile.getId();

            OpenVpnProfile profile = backup.getProfile();
            profile.setId(newId);
            openVpnService.saveProfile(profile);
            openVpnService.setProfileClientConfig(newId, backup.getConfiguration().getSourceConfig());
            for (EncryptedContainer container: backup.getExternalFiles()) {
                openVpnService.setProfileClientConfigOptionFile(newId, container.getName(), container.getContent());
            }
            restoreClientDevices(newId, backup.getDeviceIds());
        }
    }

    private void restoreClientDevices(Integer profileId, List<String> deviceIds) {
        for (String deviceId: deviceIds) {
            Device device = deviceService.getDeviceById(deviceId);
            if (device != null) {
                device.setUseVPNProfileID(profileId);
                deviceService.updateDevice(device);
            }
        }
    }
}
