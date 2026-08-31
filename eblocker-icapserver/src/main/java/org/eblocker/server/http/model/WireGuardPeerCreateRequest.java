package org.eblocker.server.http.model;

public class WireGuardPeerCreateRequest {

    private String name;

    public WireGuardPeerCreateRequest() {
    }

    public WireGuardPeerCreateRequest(
            String name) {

        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name) {

        this.name = name;
    }
}
