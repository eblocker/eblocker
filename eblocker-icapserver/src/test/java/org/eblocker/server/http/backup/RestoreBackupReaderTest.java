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

import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RestoreBackupReaderTest {
    RestoreBackupReader reader;
    Path tmpDir;
    Path backupFile;

    @BeforeEach
    void setUp() throws IOException {
        reader = new RestoreBackupReader();
        tmpDir = Files.createTempDirectory(null);
        backupFile = tmpDir.resolve("eblocker-config.eblcfg");
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    void filesNotFound() throws IOException {
        InputStream backupRestore = ResourceHandler.getClassPathInputStream("test-data/backup/backup-empty.jar");
        Files.copy(backupRestore, backupFile);
        assertNull(reader.readNetworkConfiguration(backupFile));
        assertNull(reader.readGeneralSettings(backupFile));
    }

    @Test
    void readConfigurations() throws IOException {
        InputStream backupRestore = ResourceHandler.getClassPathInputStream("test-data/backup/backup-restore.jar");
        Files.copy(backupRestore, backupFile);

        NetworkConfiguration config = reader.readNetworkConfiguration(backupFile);
        GeneralSettingsBackup settings = reader.readGeneralSettings(backupFile);

        assertEquals("192.168.0.21", config.getIpAddress());
        assertEquals("America/Argentina/Catamarca", settings.getLocaleSettings().getTimezone());
    }
}