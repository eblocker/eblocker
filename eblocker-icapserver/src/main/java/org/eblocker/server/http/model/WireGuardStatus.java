package org.eblocker.server.http.model;

public class WireGuardStatus {

    private String iface;
    private String service;
    private String wg;
    private int peers;
    private String error;

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
}
