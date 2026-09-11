package org.eblocker.server.http.backup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.crypto.json.JsonEncrypt;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic WireGuard configuration backup.
 *
 * Runtime telemetry and derived wg0 state are deliberately excluded.
 */
public class WireGuardBackup {

    private boolean enabled;
    private boolean secretsIncluded;
    private String serverPrivateKey;
    private WireGuardEndpointConfig endpointConfig;
    private Integer peerIdSequence;
    private List<WireGuardPeer> peers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSecretsIncluded() {
        return secretsIncluded;
    }

    public void setSecretsIncluded(boolean secretsIncluded) {
        this.secretsIncluded = secretsIncluded;
    }

    @JsonProperty
    @JsonEncrypt
    public String getServerPrivateKey() {
        return serverPrivateKey;
    }

    public void setServerPrivateKey(String serverPrivateKey) {
        this.serverPrivateKey = serverPrivateKey;
    }

    public WireGuardEndpointConfig getEndpointConfig() {
        return endpointConfig;
    }

    public void setEndpointConfig(WireGuardEndpointConfig endpointConfig) {
        this.endpointConfig = endpointConfig;
    }

    public Integer getPeerIdSequence() {
        return peerIdSequence;
    }

    public void setPeerIdSequence(Integer peerIdSequence) {
        this.peerIdSequence = peerIdSequence;
    }

    public List<WireGuardPeer> getPeers() {
        return peers == null
                ? new ArrayList<>()
                : new ArrayList<>(peers);
    }

    public void setPeers(List<WireGuardPeer> peers) {
        this.peers = peers == null
                ? new ArrayList<>()
                : new ArrayList<>(peers);
    }
}
