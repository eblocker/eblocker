package org.eblocker.server.http.model;

import java.util.ArrayList;
import java.util.List;

public class WireGuardStatus {

    private String iface;
    private String service;
    private String wg;
    private int peers;
    private String error;
    private List<WireGuardPeerTelemetry> peerTelemetry =
            new ArrayList<>();

    public WireGuardStatus() {
    }

    public WireGuardStatus(String iface, String service, String wg, int peers, String error) {
        this.iface = iface;
        this.service = service;
        this.wg = wg;
        this.peers = peers;
        this.error = error;
    }

    public String getIface() {
        return iface;
    }

    public void setIface(String iface) {
        this.iface = iface;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getWg() {
        return wg;
    }

    public void setWg(String wg) {
        this.wg = wg;
    }

    public int getPeers() {
        return peers;
    }

    public void setPeers(int peers) {
        this.peers = peers;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<WireGuardPeerTelemetry> getPeerTelemetry() {
        return peerTelemetry;
    }

    public void setPeerTelemetry(
            List<WireGuardPeerTelemetry> peerTelemetry) {

        this.peerTelemetry = peerTelemetry == null
                ? new ArrayList<>()
                : peerTelemetry;
    }
}
