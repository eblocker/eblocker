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

import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.backup.BackupProviderTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.restexpress.exception.BadRequestException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationBackupFileServiceTest extends BackupProviderTestBase {
    private ConfigurationBackupFileService service;
    private ScriptRunner scriptRunner;
    private String externalDiskUnmountCommand = "mountpoint_unmount";
    private String externalDiskBackupFilename = "eblocker-config.eblcfg";
    private Path externalDiskMountpoint;
    private Path tmpDir;

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory(null);
        externalDiskMountpoint = tmpDir.resolve("mnt");
        Files.createDirectory(externalDiskMountpoint);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        service = new ConfigurationBackupFileService(scriptRunner, externalDiskUnmountCommand, externalDiskBackupFilename, externalDiskMountpoint.toString(), tmpDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    void getVerifiedLocalPath() {
        assertThrows(BadRequestException.class, () -> {
            service.getVerifiedLocalPath("foo.bar");
        });

        assertThrows(BadRequestException.class, () -> {
            service.getVerifiedLocalPath(null);
        });

        assertThrows(BadRequestException.class, () -> {
            service.getVerifiedLocalPath("");
        });

        String filename = "eblocker-config_abcd9876.eblcfg";
        assertEquals(tmpDir.resolve(filename), service.getVerifiedLocalPath(filename));
        assertEquals(tmpDir.resolve(filename), service.getVerifiedLocalPath("/invalid/but/ignored/path/" + filename));
    }

    @Test
    void getTimestampedFilename() {
        assertTrue(service.getTimestampedFilename("label").matches("eblocker-config-label-\\d{4}-\\d{2}-\\d{2}.eblcfg"));
        assertTrue(service.getTimestampedFilename(null).matches("eblocker-config-\\d{4}-\\d{2}-\\d{2}.eblcfg"));
    }

    @Test
    void unmountExternalDisk() throws IOException, InterruptedException {
        service.unmountExternalDisk();
        Mockito.verify(scriptRunner).runScript(externalDiskUnmountCommand);
    }

    @Test
    void moveToExternalDisk() throws IOException {
        String filename = "eblocker-config_abcd9876.eblcfg";
        Path sourceFile = tmpDir.resolve(filename);
        Path targetFile = externalDiskMountpoint.resolve("eblocker-config.eblcfg");

        // no source file yet:
        assertThrows(NoSuchFileException.class, () -> {
            service.moveToExternalDisk(filename);
        });

        // now with source file:
        byte[] data = "backup data".getBytes(StandardCharsets.UTF_8);
        Files.write(sourceFile, data);

        assertTrue(Files.exists(sourceFile));
        assertFalse(Files.exists(targetFile));

        service.moveToExternalDisk(filename);

        assertFalse(Files.exists(sourceFile));
        assertTrue(Files.exists(targetFile));
    }
}