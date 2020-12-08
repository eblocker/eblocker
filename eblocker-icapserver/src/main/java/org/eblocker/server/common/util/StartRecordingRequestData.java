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
package org.eblocker.server.common.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StartRecordingRequestData {
    private final String recordingStatus;
    private final String targetID;
    private final Integer timeLimit;
    private final Integer sizeLimit;

    @JsonCreator
    public StartRecordingRequestData(
        @JsonProperty("recordingStatus") String recordingStatus,
        @JsonProperty("targetID") String targetID,
        @JsonProperty("timeLimit") int timeLimit,
        @JsonProperty("sizeLimit") int sizeLimit) {
        this.recordingStatus = recordingStatus;
        this.targetID = targetID;
        this.timeLimit = timeLimit;
        this.sizeLimit = sizeLimit;
    }

    public String getRecordingStatus() {
        return this.recordingStatus;
    }

    public String getTargetID() {
        return this.targetID;
    }

    public int getTimeLimit() {
        return this.timeLimit;
    }

    public int getSizeLimit() {
        return this.sizeLimit;
    }
}
