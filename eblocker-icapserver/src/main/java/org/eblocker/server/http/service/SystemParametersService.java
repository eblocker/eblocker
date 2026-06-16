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
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SystemParametersService {
    private static final Logger LOG = LoggerFactory.getLogger(SystemParametersService.class);

    private static final Path CPU_TEMPERATURE_FILE = Paths.get("/sys/class/thermal/thermal_zone0/temp");
    private static final Path LOAD_AVERAGE_FILE = Paths.get("/proc/loadavg");
    private static final Path MEMINFO_FILE = Paths.get("/proc/meminfo");
    private static final Path UPTIME_FILE = Paths.get("/proc/uptime");
    private static final Path DEVICE_MODEL_FILE = Paths.get("/proc/device-tree/model");
    private static final Path CPU_INFO_FILE = Paths.get("/proc/cpuinfo");
    private static final Path OS_RELEASE_FILE = Paths.get("/etc/os-release");
    private static final Path ROOT_PATH = Paths.get("/");
    private static final long BYTES_PER_KILOBYTE = 1024L;

    private final Path cpuTemperatureFile;
    private final Path loadAverageFile;
    private final Path meminfoFile;
    private final Path uptimeFile;
    private final Path deviceModelFile;
    private final Path cpuInfoFile;
    private final Path osReleaseFile;
    private final Path rootPath;

    @Inject
    public SystemParametersService() {
        this(CPU_TEMPERATURE_FILE, LOAD_AVERAGE_FILE, MEMINFO_FILE, UPTIME_FILE,
                DEVICE_MODEL_FILE, CPU_INFO_FILE, OS_RELEASE_FILE, ROOT_PATH);
    }

    public SystemParametersService(Path cpuTemperatureFile,
                                   Path loadAverageFile,
                                   Path meminfoFile,
                                   Path uptimeFile,
                                   Path deviceModelFile,
                                   Path cpuInfoFile,
                                   Path osReleaseFile,
                                   Path rootPath) {
        this.cpuTemperatureFile = cpuTemperatureFile;
        this.loadAverageFile = loadAverageFile;
        this.meminfoFile = meminfoFile;
        this.uptimeFile = uptimeFile;
        this.deviceModelFile = deviceModelFile;
        this.cpuInfoFile = cpuInfoFile;
        this.osReleaseFile = osReleaseFile;
        this.rootPath = rootPath;
    }

    public SystemParameters getSystemParameters() {
        Double cpuTemperatureCelsius = readCpuTemperatureCelsius();
        double[] loadAverages = readLoadAverages();
        Map<String, Long> memoryValues = readMemoryValues();
        long[] rootDiskValues = readRootDiskValues();

        return new SystemParameters(
                cpuTemperatureCelsius,
                loadAverages == null ? null : loadAverages[0],
                loadAverages == null ? null : loadAverages[1],
                loadAverages == null ? null : loadAverages[2],
                memoryValues.get("MemAvailable"),
                memoryValues.get("MemTotal"),
                memoryValues.get("SwapFree"),
                memoryValues.get("SwapTotal"),
                readUptimeSeconds(),
                rootDiskValues == null ? null : rootDiskValues[0],
                rootDiskValues == null ? null : rootDiskValues[1],
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("os.arch"),
                readOperatingSystemName(),
                System.getProperty("os.version"),
                readHardwareModel()
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
                if (!"MemTotal".equals(key) && !"MemAvailable".equals(key) &&
                        !"SwapTotal".equals(key) && !"SwapFree".equals(key)) {
                    continue;
                }
                memoryValues.put(key, parseKilobytesAsBytes(keyValue[1]));
            }
        } catch (IOException | NumberFormatException e) {
            LOG.debug("Could not read memory information from {}", meminfoFile, e);
        }
        return memoryValues;
    }

    private Long readUptimeSeconds() {
        try {
            String[] fields = Files.readString(uptimeFile).trim().split("\\s+");
            if (fields.length == 0) {
                return null;
            }
            return (long) Double.parseDouble(fields[0]);
        } catch (IOException | NumberFormatException e) {
            LOG.debug("Could not read uptime from {}", uptimeFile, e);
            return null;
        }
    }

    private long[] readRootDiskValues() {
        try {
            FileStore fileStore = Files.getFileStore(rootPath);
            return new long[] { fileStore.getUsableSpace(), fileStore.getTotalSpace() };
        } catch (IOException e) {
            LOG.debug("Could not read root filesystem information from {}", rootPath, e);
            return null;
        }
    }

    private String readOperatingSystemName() {
        return readOsReleaseValue("PRETTY_NAME");
    }

    private String readHardwareModel() {
        String model = readDeviceModelFile();
        if (model != null) {
            return model;
        }
        return readCpuInfoValue("Model");
    }

    private String readDeviceModelFile() {
        try {
            String model = Files.readString(deviceModelFile).replace("\0", "").trim();
            return model.isEmpty() ? null : model;
        } catch (IOException e) {
            LOG.debug("Could not read hardware model from {}", deviceModelFile, e);
            return null;
        }
    }

    private String readCpuInfoValue(String searchedKey) {
        try {
            for (String line : Files.readAllLines(cpuInfoFile)) {
                String[] keyValue = line.split(":", 2);
                if (keyValue.length == 2 && searchedKey.equals(keyValue[0].trim())) {
                    return keyValue[1].trim();
                }
            }
        } catch (IOException e) {
            LOG.debug("Could not read CPU information from {}", cpuInfoFile, e);
        }
        return null;
    }

    private String readOsReleaseValue(String searchedKey) {
        try {
            List<String> lines = Files.readAllLines(osReleaseFile);
            for (String line : lines) {
                String[] keyValue = line.split("=", 2);
                if (keyValue.length == 2 && searchedKey.equals(keyValue[0])) {
                    return unquote(keyValue[1]);
                }
            }
        } catch (IOException e) {
            LOG.debug("Could not read OS release information from {}", osReleaseFile, e);
        }
        return null;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Long parseKilobytesAsBytes(String value) {
        String[] fields = value.trim().split("\\s+");
        if (fields.length == 0 || fields[0].isEmpty()) {
            return null;
        }
        return Long.parseLong(fields[0]) * BYTES_PER_KILOBYTE;
    }
}
