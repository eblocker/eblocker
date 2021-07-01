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

    public void update(String domain, boolean blocked) {
        requests.computeIfAbsent(domain, d -> new RecordedDomainCounter()).update(blocked);
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
