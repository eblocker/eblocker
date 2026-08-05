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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnLoginCredentials;
import org.eblocker.server.common.openvpn.OpenVpnProfileFiles;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.SimpleOption;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.*;

class OpenVpnClientBackupProviderTest extends BackupProviderTestBase {
    private OpenVpnClientBackupProvider provider;
    private OpenVpnClientBackupProvider providerNoPassword;
    private OpenVpnService openVpnService;
    private OpenVpnProfile openVpnProfile;
    private OpenVpnProfile newProfile;
    private OpenVpnProfileFiles profileFiles;
    private OpenVpnConfiguration openVpnConfiguration;
    private VpnLoginCredentials credentials;
    private DeviceService deviceService;
    private Device device;
    private static final String deviceMAC = "0123456789ab";
    private static final String deviceId = "device:" + deviceMAC;
    private static final String username = "TestUser";
    private static final String password = "TestPassword";
    private static final int profileId = 23;
    private static final String privateKey = "private key";
    private static final int nextProfileId = 42;
    private Path openVpnDir;

    @BeforeEach
    void setUp() throws IOException {
        openVpnDir = Files.createTempDirectory("OpenVpnClientBackupProviderTest");
        profileFiles = new OpenVpnProfileFiles(openVpnDir.toString(), new ObjectMapper());
        setUpOpenVpnService();
        setUpDeviceService();
        provider = new OpenVpnClientBackupProvider(openVpnService, profileFiles, deviceService, createCryptoService("top secret!!!"));
        providerNoPassword = new OpenVpnClientBackupProvider(openVpnService, profileFiles, deviceService, null);
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(openVpnDir);
    }

    private void setUpOpenVpnService() throws IOException {
        openVpnService = Mockito.mock(OpenVpnService.class);
        openVpnProfile = new OpenVpnProfile(profileId, "Affordable VPN Services Inc.");
        newProfile = new OpenVpnProfile(nextProfileId, null);
        credentials = new VpnLoginCredentials();
        credentials.setUsername(username);
        credentials.setPassword(password);
        openVpnProfile.setLoginCredentials(credentials);
        openVpnConfiguration = new OpenVpnConfiguration();
        openVpnConfiguration.setUserOptions(List.of(
                new SimpleOption(1, "remote", new String[]{"affordable-vpns.com", "1194"}),
                new SimpleOption(2, "key", new String[]{"private.key"})
        ));
        Map<String, String> inlineContent = new HashMap<>();
        inlineContent.put("key", null);
        profileFiles.createProfileDirectory(profileId);
        profileFiles.writeConfigOptionFile(profileId, "key", privateKey.getBytes(Charsets.UTF_8));
        openVpnConfiguration.setInlinedContentByName(inlineContent);
        Mockito.when(openVpnService.getVpnProfiles()).thenReturn(List.of(openVpnProfile));
        Mockito.when(openVpnService.getOpenVpnProfileById(profileId)).thenReturn(openVpnProfile);
        Mockito.when(openVpnService.getProfileClientConfig(profileId)).thenReturn(openVpnConfiguration);
        Mockito.when(openVpnService.saveProfile(Mockito.argThat((profile) -> profile.getId() == null))).thenReturn(newProfile);
    }

    private void setUpDeviceService() {
        deviceService = Mockito.mock(DeviceService.class);
        TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice(deviceMAC, "192.168.0.23", true);
        tdf.commit();
        device = tdf.getDevice(deviceId);
        device.setUseVPNProfileID(profileId);
        device.setUseAnonymizationService(true);
    }
    @Test
    void credentialsAreEncrypted() throws IOException {
        byte[] backup = exportBackup(provider);
        JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(backup));
        jarInputStream.getNextEntry();
        String rawJson = new String(jarInputStream.readAllBytes(), Charsets.UTF_8);
        assertTrue(rawJson.contains(username));
        assertFalse(rawJson.contains(password));
    }

    @Test
    public void noPasswordForImport() throws Exception {
        byte[] backup = exportBackup(provider);
        importBackup(backup, providerNoPassword);
        assertEquals(List.of(new BackupWarning(BackupWarning.Id.NO_PASSWORD_OPENVPN_CLIENTS_NOT_IMPORTED)), providerNoPassword.getWarnings());
    }

    @Test
    public void importFailure() throws Exception {
        byte[] backup = exportBackup(provider);
        Mockito.doThrow(new IOException("Could not save profile")).when(openVpnService).saveProfile(Mockito.any());
        importBackup(backup, provider);
        List<BackupWarning> warnings = provider.getWarnings();
        assertEquals(1, warnings.size());
        assertEquals(BackupWarning.Id.ITEM_NOT_IMPORTED, warnings.get(0).getId());
    }

    @Test
    public void deviceMapping() throws Exception {
        byte[] backup = exportBackup(provider);
        importBackup(backup, provider);
        Mockito.verify(deviceService).updateDevice(Mockito.argThat((device) -> device.getUseVPNProfileID() == nextProfileId && device.getId().equals(deviceId)));
    }

    @Test
    public void testExportVerifyImport() throws IOException {
        exportVerifyImport(provider);
    }
}