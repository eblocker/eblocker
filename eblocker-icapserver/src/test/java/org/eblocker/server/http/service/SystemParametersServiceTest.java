/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.data.systemstatus.SystemParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemParametersServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsTemperatureLoadAndMemory() throws Exception {
        Path temperatureFile = tempDir.resolve("temp");
        Path loadAverageFile = tempDir.resolve("loadavg");
        Path meminfoFile = tempDir.resolve("meminfo");

        Files.writeString(temperatureFile, "52375\n");
        Files.writeString(loadAverageFile, "0.11 0.22 0.33 1/234 567\n");
        Files.writeString(meminfoFile, "MemTotal:       1024000 kB\n" +
                "MemFree:         128000 kB\n" +
                "MemAvailable:    512000 kB\n");

        SystemParametersService service = new SystemParametersService(
                temperatureFile,
                loadAverageFile,
                meminfoFile
        );

        SystemParameters parameters = service.getSystemParameters();

        assertEquals(52.4, parameters.getCpuTemperatureCelsius(), 0.01);
        assertEquals(0.11, parameters.getLoadAverage1Minute(), 0.001);
        assertEquals(0.22, parameters.getLoadAverage5Minutes(), 0.001);
        assertEquals(0.33, parameters.getLoadAverage15Minutes(), 0.001);
        assertEquals(1048576000L, parameters.getMemoryTotalBytes());
        assertEquals(524288000L, parameters.getMemoryAvailableBytes());
    }

    @Test
    void missingTemperatureDoesNotHideOtherParameters() throws Exception {
        Path missingTemperatureFile = tempDir.resolve("missing-temp");
        Path loadAverageFile = tempDir.resolve("loadavg");
        Path meminfoFile = tempDir.resolve("meminfo");

        Files.writeString(loadAverageFile, "1.00 2.00 3.00 1/234 567\n");
        Files.writeString(meminfoFile, "MemTotal:       2048000 kB\n" +
                "MemAvailable:   1024000 kB\n");

        SystemParametersService service = new SystemParametersService(
                missingTemperatureFile,
                loadAverageFile,
                meminfoFile
        );

        SystemParameters parameters = service.getSystemParameters();

        assertNull(parameters.getCpuTemperatureCelsius());
        assertEquals(1.00, parameters.getLoadAverage1Minute(), 0.001);
        assertEquals(2097152000L, parameters.getMemoryTotalBytes());
        assertEquals(1048576000L, parameters.getMemoryAvailableBytes());
    }
}
