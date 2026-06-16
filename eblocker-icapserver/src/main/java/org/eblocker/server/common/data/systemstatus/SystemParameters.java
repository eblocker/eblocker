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
package org.eblocker.server.common.data.systemstatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemParameters {
    private final Double cpuTemperatureCelsius;
    private final Double loadAverage1Minute;
    private final Double loadAverage5Minutes;
    private final Double loadAverage15Minutes;
    private final Long memoryAvailableBytes;
    private final Long memoryTotalBytes;
    private final Long swapFreeBytes;
    private final Long swapTotalBytes;
    private final Long uptimeSeconds;
    private final Long rootDiskAvailableBytes;
    private final Long rootDiskTotalBytes;
    private final Integer cpuCoreCount;
    private final String architecture;
    private final String operatingSystemName;
    private final String kernelVersion;
    private final String hardwareModel;

    public SystemParameters(
            Double cpuTemperatureCelsius,
            Double loadAverage1Minute,
            Double loadAverage5Minutes,
            Double loadAverage15Minutes,
            Long memoryAvailableBytes,
            Long memoryTotalBytes,
            Long swapFreeBytes,
            Long swapTotalBytes,
            Long uptimeSeconds,
            Long rootDiskAvailableBytes,
            Long rootDiskTotalBytes,
            Integer cpuCoreCount,
            String architecture,
            String operatingSystemName,
            String kernelVersion,
            String hardwareModel
    ) {
        this.cpuTemperatureCelsius = cpuTemperatureCelsius;
        this.loadAverage1Minute = loadAverage1Minute;
        this.loadAverage5Minutes = loadAverage5Minutes;
        this.loadAverage15Minutes = loadAverage15Minutes;
        this.memoryAvailableBytes = memoryAvailableBytes;
        this.memoryTotalBytes = memoryTotalBytes;
        this.swapFreeBytes = swapFreeBytes;
        this.swapTotalBytes = swapTotalBytes;
        this.uptimeSeconds = uptimeSeconds;
        this.rootDiskAvailableBytes = rootDiskAvailableBytes;
        this.rootDiskTotalBytes = rootDiskTotalBytes;
        this.cpuCoreCount = cpuCoreCount;
        this.architecture = architecture;
        this.operatingSystemName = operatingSystemName;
        this.kernelVersion = kernelVersion;
        this.hardwareModel = hardwareModel;
    }

    @JsonProperty
    public Double getCpuTemperatureCelsius() {
        return cpuTemperatureCelsius;
    }

    @JsonProperty
    public Double getLoadAverage1Minute() {
        return loadAverage1Minute;
    }

    @JsonProperty
    public Double getLoadAverage5Minutes() {
        return loadAverage5Minutes;
    }

    @JsonProperty
    public Double getLoadAverage15Minutes() {
        return loadAverage15Minutes;
    }

    @JsonProperty
    public Long getMemoryAvailableBytes() {
        return memoryAvailableBytes;
    }

    @JsonProperty
    public Long getMemoryTotalBytes() {
        return memoryTotalBytes;
    }

    @JsonProperty
    public Long getSwapFreeBytes() {
        return swapFreeBytes;
    }

    @JsonProperty
    public Long getSwapTotalBytes() {
        return swapTotalBytes;
    }

    @JsonProperty
    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    @JsonProperty
    public Long getRootDiskAvailableBytes() {
        return rootDiskAvailableBytes;
    }

    @JsonProperty
    public Long getRootDiskTotalBytes() {
        return rootDiskTotalBytes;
    }

    @JsonProperty
    public Integer getCpuCoreCount() {
        return cpuCoreCount;
    }

    @JsonProperty
    public String getArchitecture() {
        return architecture;
    }

    @JsonProperty
    public String getOperatingSystemName() {
        return operatingSystemName;
    }

    @JsonProperty
    public String getKernelVersion() {
        return kernelVersion;
    }

    @JsonProperty
    public String getHardwareModel() {
        return hardwareModel;
    }
}
