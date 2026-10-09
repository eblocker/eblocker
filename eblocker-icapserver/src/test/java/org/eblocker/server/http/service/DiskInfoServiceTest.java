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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

class DiskInfoServiceTest extends BackupProviderTestBase {
    private final String listBlockDevicesCommand = "list-block-devices";
    private final String mountPoint = "/opt/eblocker-icap/mnt";

    @Test
    void testGetMountedPartition() throws Exception {
        assertNull(createService(createScriptRunner("vm-no-drive.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("vm-not-mounted.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("vm-eos4-no-drive.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("pc-no-drive.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("pc-not-mounted.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("raspi-no-drive.json")).getMountedPartition());
        assertNull(createService(createScriptRunner("raspi-not-mounted.json")).getMountedPartition());

        assertEquals("Slim Line / INTENSO", createService(createScriptRunner("raspi-mounted.json")).getMountedPartition().getFriendlyName());
        assertEquals("Alu_Line / INTENSO", createService(createScriptRunner("pc-mounted.json")).getMountedPartition().getFriendlyName());
        assertEquals("USB", createService(createScriptRunner("vm-mounted.json")).getMountedPartition().getFriendlyName());
    }

    private DiskInfoService createService(ScriptRunner scriptRunner) {
        return new DiskInfoService(scriptRunner, listBlockDevicesCommand, mountPoint);
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