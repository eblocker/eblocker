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

import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.backup.BackupProviderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

class DiskMountServiceTest extends BackupProviderTestBase {
    private final String listBlockDevicesCommand = "list-block-devices";
    private final String mountCommand = "mount-external-disk";
    private final String unmountCommand = "unmount-external-disk";
    private final String mountPoint = "/opt/eblocker-icap/mnt";

    private DiskMountService service;
    private ScriptRunner scriptRunner;

    @BeforeEach
    void setUp() {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        service = createService(scriptRunner);
    }

    @Test
    void mountDisk() throws Exception {
        final String partition = "/dev/sda1";
        service.mountExternalDisk(partition);
        Mockito.verify(scriptRunner).runScript(mountCommand, partition, mountPoint);
    }

    @Test
    void unmountDisk() throws Exception {
        service.unmountExternalDisk();
        Mockito.verify(scriptRunner).runScript(unmountCommand, mountPoint);
    }

    @Test
    void mountDiskAlreadyMounted() throws Exception {
        final String partition = "/dev/sda1";
        Mockito.when(scriptRunner.runScript(mountCommand, partition, mountPoint)).thenReturn(32);
        assertThrows(IOException.class, () -> {
                service.mountExternalDisk(partition);
        });
    }

    @Test
    void getFirstUnmountedVfatPartition() throws Exception {
        assertNull(createService(createScriptRunner("vm-no-drive.json")).getFirstUnmountedVfatPartition());
        assertEquals("/dev/sda1", createService(createScriptRunner("raspi-not-mounted.json")).getFirstUnmountedVfatPartition());
        assertNull(createService(createScriptRunner("raspi-mounted.json")).getFirstUnmountedVfatPartition());
    }

    @Test
    void isExternalDiskMounted() throws Exception {
        assertFalse(createService(createScriptRunner("vm-no-drive.json")).isExternalDiskMounted());
        assertFalse(createService(createScriptRunner("raspi-not-mounted.json")).isExternalDiskMounted());
        assertTrue(createService(createScriptRunner("raspi-mounted.json")).isExternalDiskMounted());
    }

    private DiskMountService createService(ScriptRunner scriptRunner) {
        return new DiskMountService(scriptRunner, listBlockDevicesCommand, mountCommand, unmountCommand, mountPoint);
    }

    private ScriptRunner createScriptRunner(String jsonFile) {
        return new ScriptRunner() {
            @Override
            public int runScript(String scriptName, String... arguments) throws IOException {
                if (scriptName.equals(listBlockDevicesCommand)) {
                    if (arguments.length != 1) {
                        throw new IllegalArgumentException("Expected one script argument");
                    }
                    InputStream jsonInput = ClassLoader.getSystemResourceAsStream("test-data/mount/" + jsonFile);
                    Files.copy(jsonInput, Paths.get(arguments[0]), StandardCopyOption.REPLACE_EXISTING);
                    return 0;
                } else {
                    throw new RuntimeException("Not implemented");
                }
            }

            @Override
            public LoggingProcess startScript(String scriptName, String... arguments) {
                throw new RuntimeException("Not implemented");
            }

            @Override
            public void stopScript(LoggingProcess loggingProcess) {
                throw new RuntimeException("Not implemented");
            }
        };
    }
}