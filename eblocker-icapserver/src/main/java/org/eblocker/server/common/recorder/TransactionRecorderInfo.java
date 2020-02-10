/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.recorder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionRecorderInfo {

    private final String deviceId;

    private final long timeLimitSeconds;

    private final long sizeLimitBytes;

    private final boolean active;

    private final long runningTime;

    private final long gatheredBytes;

    private final int gatheredRequests;

    @JsonCreator
    public TransactionRecorderInfo(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("timeLimitSeconds") Long timeLimitSeconds,
            @JsonProperty("sizeLimitBytes") Long sizeLimitBytes,
            @JsonProperty("active") Boolean active,
            @JsonProperty("runningTime") Long runningTime,
            @JsonProperty("gatheredBytes") Long gatheredBytes,
            @JsonProperty("gatheredRequests") Integer gatheredRequests) {
        this.deviceId = deviceId;
        this.timeLimitSeconds = timeLimitSeconds == null ? -1 : timeLimitSeconds;
        this.sizeLimitBytes = sizeLimitBytes == null ? -1 : sizeLimitBytes;
        this.active = active == null ? false : active;
        this.runningTime = runningTime == null ? 0 : runningTime;
        this.gatheredBytes = gatheredBytes == null ? 0 : gatheredBytes;
        this.gatheredRequests = gatheredRequests == null ? 0 : gatheredRequests;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public long getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public long getSizeLimitBytes() {
        return sizeLimitBytes;
    }

    public boolean isActive() {
        return active;
    }

    public long getRunningTime() {
        return runningTime;
    }

    public long getGatheredBytes() {
        return gatheredBytes;
    }

    public int getGatheredRequests() {
        return gatheredRequests;
    }
}
