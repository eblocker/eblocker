package org.eblocker.server.common.data.wireguard;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.crypto.json.JsonEncrypt;

public class WireGuardPeer {

    private int id;
    private String name;

    private String privateKey;
    private String publicKey;
    private String presharedKey;

    private String allowedIp;

    /**
     * Optional association with an eBlocker device.
     *
     * Peers created independently of the device dashboard intentionally
     * keep this value null. This preserves compatibility with existing
     * WireGuard peers and allows dashboard-managed peers to be mapped
     * unambiguously to one eBlocker device.
     */
    private String deviceId;

    private boolean allowLanAccess;

    public WireGuardPeer() {
        // For JSON / Jackson
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

    @JsonProperty
    @JsonEncrypt
    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @JsonProperty
    @JsonEncrypt
    public String getPresharedKey() {
        return presharedKey;
    }

    public void setPresharedKey(String presharedKey) {
        this.presharedKey = presharedKey;
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

    public void setAllowLanAccess(boolean allowLanAccess) {
        this.allowLanAccess = allowLanAccess;
    }
}
