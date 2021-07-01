package org.eblocker.server.common.recorder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Counts requested domains with their blocked state. Only the last blocked state is saved.
 */
public class RecordedDomainCounter {
    @JsonProperty("blocked")
    private boolean blocked;

    @JsonProperty("count")
    private int count;

    @JsonCreator
    public RecordedDomainCounter() {
        this.count = 0;
    }

    public void update(boolean blocked) {
        this.blocked = blocked;
        count++;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RecordedDomainCounter that = (RecordedDomainCounter) o;
        return blocked == that.blocked &&
                count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocked, count);
    }

    /**
     * Merge two counters. The blocked state of the given counter overwrites the existing blocked state.
     * @param counter
     */
    public void update(RecordedDomainCounter counter) {
        count += counter.count;
        blocked = counter.blocked;
    }
}
