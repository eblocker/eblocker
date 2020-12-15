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
package org.eblocker.server.common.squid;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FailedConnection {
    private final List<String> deviceIds;
    private final List<String> domains;
    private final List<String> errors;
    private final Instant lastOccurrence;

    @JsonCreator
    public FailedConnection(@JsonProperty("deviceIds") List<String> deviceIds,
                            @JsonProperty("domains") List<String> domains,
                            @JsonProperty("errors") List<String> errors,
                            @JsonProperty("lastOccurrence") Instant lastOccurrence) {
        this.deviceIds = deviceIds;
        this.domains = domains;
        this.errors = errors;
        this.lastOccurrence = lastOccurrence;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    public List<String> getDomains() {
        return domains;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Instant getLastOccurrence() {
        return lastOccurrence;
    }

    @Override
    public String toString() {
        return "FailedConnection{" +
                "deviceIds=" + deviceIds +
                ", domains=" + domains +
                ", errors=" + errors +
                ", lastOccurrence=" + lastOccurrence +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FailedConnection that = (FailedConnection) o;
        return Objects.equals(deviceIds, that.deviceIds) &&
                Objects.equals(domains, that.domains) &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(lastOccurrence, that.lastOccurrence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceIds, domains, errors, lastOccurrence);
    }
}
