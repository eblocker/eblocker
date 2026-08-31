package org.eblocker.server.http.model;

import org.eblocker.server.common.data.wireguard.WireGuardPeer;

/**
 * Secret-free representation of a WireGuard peer for normal admin API use.
 *
 * Private keys and preshared keys intentionally do not exist in this type.
 */
public class WireGuardPeerView {

    private int id;
    private String name;
    private String publicKey;
    private String allowedIp;
    private String deviceId;
    private boolean allowLanAccess;

    public WireGuardPeerView() {
    }

    public WireGuardPeerView(
            int id,
            String name,
            String publicKey,
            String allowedIp,
            String deviceId,
            boolean allowLanAccess) {

        this.id = id;
        this.name = name;
        this.publicKey = publicKey;
        this.allowedIp = allowedIp;
        this.deviceId = deviceId;
        this.allowLanAccess = allowLanAccess;
    }

    public static WireGuardPeerView fromPeer(
            WireGuardPeer peer) {

        return new WireGuardPeerView(
                peer.getId(),
                peer.getName(),
                peer.getPublicKey(),
                peer.getAllowedIp(),
                peer.getDeviceId(),
                peer.isAllowLanAccess()
        );
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getAllowedIp() {
        return allowedIp;
    }

    public void setAllowedIp(String allowedIp) {
        this.allowedIp = allowedIp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isAllowLanAccess() {
        return allowLanAccess;
    }

    public void setAllowLanAccess(
            boolean allowLanAccess) {

        this.allowLanAccess = allowLanAccess;
    }
}
