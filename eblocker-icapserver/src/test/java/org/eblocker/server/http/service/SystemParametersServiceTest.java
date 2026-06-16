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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemParametersServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsTemperatureLoadMemoryUptimeDiskAndSpecs() throws Exception {
        Path temperatureFile = tempDir.resolve("temp");
        Path loadAverageFile = tempDir.resolve("loadavg");
        Path meminfoFile = tempDir.resolve("meminfo");
        Path uptimeFile = tempDir.resolve("uptime");
        Path deviceModelFile = tempDir.resolve("model");
        Path cpuInfoFile = tempDir.resolve("cpuinfo");
        Path osReleaseFile = tempDir.resolve("os-release");

        Files.writeString(temperatureFile, "52375\n");
        Files.writeString(loadAverageFile, "0.11 0.22 0.33 1/234 567\n");
        Files.writeString(meminfoFile, "MemTotal:       1024000 kB\n" +
                "MemFree:         128000 kB\n" +
                "MemAvailable:    512000 kB\n" +
                "SwapTotal:       256000 kB\n" +
                "SwapFree:        128000 kB\n");
        Files.writeString(uptimeFile, "12345.67 234.56\n");
        Files.writeString(deviceModelFile, "Raspberry Pi 5 Model B Rev 1.0\0");
        Files.writeString(cpuInfoFile, "Hardware\t: BCM2712\nModel\t\t: Raspberry Pi fallback\n");
        Files.writeString(osReleaseFile, "PRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\"\n");

        SystemParametersService service = new SystemParametersService(
                temperatureFile,
                loadAverageFile,
                meminfoFile,
                uptimeFile,
                deviceModelFile,
                cpuInfoFile,
                osReleaseFile,
                tempDir
        );

        SystemParameters parameters = service.getSystemParameters();

        assertEquals(52.4, parameters.getCpuTemperatureCelsius(), 0.01);
        assertEquals(0.11, parameters.getLoadAverage1Minute(), 0.001);
        assertEquals(0.22, parameters.getLoadAverage5Minutes(), 0.001);
        assertEquals(0.33, parameters.getLoadAverage15Minutes(), 0.001);
        assertEquals(1048576000L, parameters.getMemoryTotalBytes());
        assertEquals(524288000L, parameters.getMemoryAvailableBytes());
        assertEquals(262144000L, parameters.getSwapTotalBytes());
        assertEquals(131072000L, parameters.getSwapFreeBytes());
        assertEquals(12345L, parameters.getUptimeSeconds());
        assertNotNull(parameters.getRootDiskAvailableBytes());
        assertNotNull(parameters.getRootDiskTotalBytes());
        assertTrue(parameters.getRootDiskTotalBytes() > 0);
        assertTrue(parameters.getCpuCoreCount() > 0);
        assertNotNull(parameters.getArchitecture());
        assertEquals("Debian GNU/Linux 12 (bookworm)", parameters.getOperatingSystemName());
        assertNotNull(parameters.getKernelVersion());
        assertEquals("Raspberry Pi 5 Model B Rev 1.0", parameters.getHardwareModel());
    }

    @Test
    void missingTemperatureAndSpecsDoNotHideOtherParameters() throws Exception {
        Path missingTemperatureFile = tempDir.resolve("missing-temp");
        Path loadAverageFile = tempDir.resolve("loadavg");
        Path meminfoFile = tempDir.resolve("meminfo");
        Path uptimeFile = tempDir.resolve("uptime");
        Path missingDeviceModelFile = tempDir.resolve("missing-model");
        Path cpuInfoFile = tempDir.resolve("cpuinfo");
        Path missingOsReleaseFile = tempDir.resolve("missing-os-release");

        Files.writeString(loadAverageFile, "1.00 2.00 3.00 1/234 567\n");
        Files.writeString(meminfoFile, "MemTotal:       2048000 kB\n" +
                "MemAvailable:   1024000 kB\n");
        Files.writeString(uptimeFile, "1.23 4.56\n");
        Files.writeString(cpuInfoFile, "Hardware\t: BCM2712\nModel\t\t: Raspberry Pi fallback\n");

        SystemParametersService service = new SystemParametersService(
                missingTemperatureFile,
                loadAverageFile,
                meminfoFile,
                uptimeFile,
                missingDeviceModelFile,
                cpuInfoFile,
                missingOsReleaseFile,
                tempDir
        );

        SystemParameters parameters = service.getSystemParameters();

        assertNull(parameters.getCpuTemperatureCelsius());
        assertEquals(1.00, parameters.getLoadAverage1Minute(), 0.001);
        assertEquals(2097152000L, parameters.getMemoryTotalBytes());
        assertEquals(1048576000L, parameters.getMemoryAvailableBytes());
        assertNull(parameters.getSwapTotalBytes());
        assertNull(parameters.getSwapFreeBytes());
        assertEquals(1L, parameters.getUptimeSeconds());
        assertNull(parameters.getOperatingSystemName());
        assertEquals("Raspberry Pi fallback", parameters.getHardwareModel());
    }
}
