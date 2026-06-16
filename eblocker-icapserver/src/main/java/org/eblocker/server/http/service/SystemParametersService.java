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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.systemstatus.SystemParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class SystemParametersService {
    private static final Logger LOG = LoggerFactory.getLogger(SystemParametersService.class);

    private static final Path CPU_TEMPERATURE_FILE = Paths.get("/sys/class/thermal/thermal_zone0/temp");
    private static final Path LOAD_AVERAGE_FILE = Paths.get("/proc/loadavg");
    private static final Path MEMINFO_FILE = Paths.get("/proc/meminfo");
    private static final long BYTES_PER_KILOBYTE = 1024L;

    private final Path cpuTemperatureFile;
    private final Path loadAverageFile;
    private final Path meminfoFile;

    @Inject
    public SystemParametersService() {
        this(CPU_TEMPERATURE_FILE, LOAD_AVERAGE_FILE, MEMINFO_FILE);
    }

    public SystemParametersService(Path cpuTemperatureFile, Path loadAverageFile, Path meminfoFile) {
        this.cpuTemperatureFile = cpuTemperatureFile;
        this.loadAverageFile = loadAverageFile;
        this.meminfoFile = meminfoFile;
    }

    public SystemParameters getSystemParameters() {
        Double cpuTemperatureCelsius = readCpuTemperatureCelsius();
        double[] loadAverages = readLoadAverages();
        Map<String, Long> memoryValues = readMemoryValues();

        return new SystemParameters(
                cpuTemperatureCelsius,
                loadAverages == null ? null : loadAverages[0],
                loadAverages == null ? null : loadAverages[1],
                loadAverages == null ? null : loadAverages[2],
                memoryValues.get("MemAvailable"),
                memoryValues.get("MemTotal")
        );
    }

    private Double readCpuTemperatureCelsius() {
        try {
            String value = Files.readString(cpuTemperatureFile).trim();
            double millidegreesCelsius = Double.parseDouble(value);
            return Math.round(millidegreesCelsius / 100.0d) / 10.0d;
        } catch (IOException | NumberFormatException e) {
            LOG.debug("Could not read CPU temperature from {}", cpuTemperatureFile, e);
            return null;
        }
    }

    private double[] readLoadAverages() {
        try {
            String[] fields = Files.readString(loadAverageFile).trim().split("\\s+");
            if (fields.length < 3) {
                return null;
            }
            return new double[] {
                    Double.parseDouble(fields[0]),
                    Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2])
            };
        } catch (IOException | NumberFormatException e) {
            LOG.debug("Could not read load average from {}", loadAverageFile, e);
            return null;
        }
    }

    private Map<String, Long> readMemoryValues() {
        Map<String, Long> memoryValues = new HashMap<>();
        try {
            for (String line : Files.readAllLines(meminfoFile)) {
                String[] keyValue = line.split(":", 2);
                if (keyValue.length != 2) {
                    continue;
                }
                String key = keyValue[0];
                if (!"MemTotal".equals(key) && !"MemAvailable".equals(key)) {
                    continue;
                }
                memoryValues.put(key, parseKilobytesAsBytes(keyValue[1]));
            }
        } catch (IOException | NumberFormatException e) {
            LOG.debug("Could not read memory information from {}", meminfoFile, e);
        }
        return memoryValues;
    }

    private Long parseKilobytesAsBytes(String value) {
        String[] fields = value.trim().split("\\s+");
        if (fields.length == 0 || fields[0].isEmpty()) {
            return null;
        }
        return Long.parseLong(fields[0]) * BYTES_PER_KILOBYTE;
    }
}
