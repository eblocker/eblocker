package org.eblocker.server.common.data.wireguard;

import org.eblocker.crypto.json.JsonEncrypt;

public class WireGuardPeer {

    private String id;

    private String name;

    @JsonEncrypt
    private String privateKey;

    @JsonEncrypt
    private String publicKey;

    @JsonEncrypt
    private String presharedKey;

    private String allowedIp;

    public WireGuardPeer() {
        // für JSON / Jackson
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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
}
