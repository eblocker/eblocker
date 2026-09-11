package org.eblocker.server.http.model;

import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;

import java.util.ArrayList;
import java.util.List;

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
    private WireGuardTunnelMode tunnelMode;
    private List<String> customAllowedIps =
            new ArrayList<>();

    public WireGuardPeerView() {
    }

    public WireGuardPeerView(
            int id,
            String name,
            String publicKey,
            String allowedIp,
            String deviceId,
            boolean allowLanAccess) {

        this(
                id,
                name,
                publicKey,
                allowedIp,
                deviceId,
                allowLanAccess,
                WireGuardTunnelMode.FULL_TUNNEL,
                new ArrayList<>()
        );
    }

    public WireGuardPeerView(
            int id,
            String name,
            String publicKey,
            String allowedIp,
            String deviceId,
            boolean allowLanAccess,
            WireGuardTunnelMode tunnelMode,
            List<String> customAllowedIps) {

        this.id = id;
        this.name = name;
        this.publicKey = publicKey;
        this.allowedIp = allowedIp;
        this.deviceId = deviceId;
        this.allowLanAccess = allowLanAccess;
        this.tunnelMode = tunnelMode == null
                ? WireGuardTunnelMode.FULL_TUNNEL
                : tunnelMode;
        setCustomAllowedIps(customAllowedIps);
    }

    public static WireGuardPeerView fromPeer(
            WireGuardPeer peer) {

        return new WireGuardPeerView(
                peer.getId(),
                peer.getName(),
                peer.getPublicKey(),
                peer.getAllowedIp(),
                peer.getDeviceId(),
                peer.isAllowLanAccess(),
                peer.getTunnelMode(),
                peer.getCustomAllowedIps()
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

    public WireGuardTunnelMode getTunnelMode() {
        return tunnelMode == null
                ? WireGuardTunnelMode.FULL_TUNNEL
                : tunnelMode;
    }

    public void setTunnelMode(
            WireGuardTunnelMode tunnelMode) {

        this.tunnelMode = tunnelMode == null
                ? WireGuardTunnelMode.FULL_TUNNEL
                : tunnelMode;
    }

    public List<String> getCustomAllowedIps() {
        return new ArrayList<>(
                customAllowedIps
        );
    }

    public void setCustomAllowedIps(
            List<String> customAllowedIps) {

        this.customAllowedIps =
                customAllowedIps == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                                customAllowedIps
                        );
    }
}
