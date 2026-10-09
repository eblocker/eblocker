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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.backup.BackupProviderTestBase;
import org.eblocker.server.http.backup.GeneralSettingsBackup;
import org.eblocker.server.http.backup.RestoreBackupReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFromBackupServiceTest extends BackupProviderTestBase {
    private RestoreFromBackupService service;
    private ConfigurationBackupFileService backupFileService;
    private RestoreBackupReader restoreBackupReader;
    private NetworkStateMachine networkStateMachine;
    private SettingsService settingsService;
    private Path tmpDir;
    private String backupFilename = "eblocker-config.eblcfg";

    @BeforeEach
    void setUp() throws IOException {
        backupFileService = Mockito.mock(ConfigurationBackupFileService.class);
        restoreBackupReader = Mockito.mock(RestoreBackupReader.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        settingsService = Mockito.mock(SettingsService.class);
        tmpDir = Files.createTempDirectory(null);
        service = new RestoreFromBackupService(backupFileService, restoreBackupReader, networkStateMachine, settingsService, tmpDir.toString(), backupFilename);
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    void testInitializeWithoutBackup() {
        service.initialize();
        assertFalse(service.isBackupAvailable());
    }

    @Test
    void testInitializeWithBackup() throws IOException {
        byte[] backupData = "Backup data".getBytes(StandardCharsets.UTF_8);
        Path backupFile = tmpDir.resolve(backupFilename);
        Files.write(backupFile, backupData);

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setIpAddress("192.168.0.2");
        Mockito.when(restoreBackupReader.readNetworkConfiguration(backupFile)).thenReturn(networkConfiguration);

        GeneralSettingsBackup generalSettings = new GeneralSettingsBackup();
        LocaleSettings localeSettings = new LocaleSettings(null, null, null, null, null);
        generalSettings.setLocaleSettings(localeSettings);
        Mockito.when(restoreBackupReader.readGeneralSettings(backupFile)).thenReturn(generalSettings);

        service.initialize();
        assertTrue(service.isBackupAvailable());

        Mockito.verify(networkStateMachine).updateConfiguration(networkConfiguration);
        Mockito.verify(settingsService).setLocaleSettings(localeSettings);
    }

    @Test
    void testCancelImport() throws IOException, InterruptedException {
        service.cancelImport();
        assertFalse(service.isBackupAvailable());
        Mockito.verify(backupFileService).unmountExternalDisk();
    }

    @Test
    void testRenameBackup() throws IOException {
        Path sourceFile = tmpDir.resolve(backupFilename);
        String targetFilename = "eblocker-config-imported-2026-10-08.eblcfg";
        Path targetFile = tmpDir.resolve(targetFilename);
        Mockito.when(backupFileService.getTimestampedFilename("imported")).thenReturn(targetFilename);

        // no source file yet:
        assertFalse(service.renameBackup(true));

        // now with source file:
        Files.write(sourceFile, "backup data".getBytes(StandardCharsets.UTF_8));
        assertFalse(Files.exists(targetFile));
        assertTrue(service.renameBackup(true));
        assertTrue(Files.exists(targetFile));
    }
}