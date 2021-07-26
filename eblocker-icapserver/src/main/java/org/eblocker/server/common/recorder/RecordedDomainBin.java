/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Records domains requested in a defined time interval
 */
public class RecordedDomainBin {
    private Instant begin, end;

    @JsonProperty("requests")
    private Map<String, RecordedDomainCounter> requests;

    @JsonCreator
    public RecordedDomainBin(@JsonProperty("begin") Instant begin,
                             @JsonProperty("end") Instant end) {
        this.begin = begin;
        this.end = end;
        this.requests = new HashMap<>();
    }

    public void update(String domain, boolean blocked, boolean patternRequest) {
        requests.computeIfAbsent(domain, d -> new RecordedDomainCounter()).update(blocked, patternRequest);
    }

    public Instant getEnd() {
        return end;
    }

    public Instant getBegin() {
        return begin;
    }

    public Map<String, RecordedDomainCounter> getRequests() {
        return requests;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RecordedDomainBin that = (RecordedDomainBin) o;
        return Objects.equals(begin, that.begin) &&
                Objects.equals(end, that.end) &&
                Objects.equals(requests, that.requests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(begin, end, requests);
    }

    public boolean isWritable(Instant now) {
        return now.isBefore(end);
    }
}
