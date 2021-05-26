package org.eblocker.server.common.recorder;

public class RecordedDomainRequests {
    private String domain;
    private boolean blocked;
    private int count;

    public RecordedDomainRequests(String domain) {
        this.domain = domain;
        this.count = 0;
    }

    public void update(boolean blocked) {
        this.blocked = blocked;
        count++;
    }
}
