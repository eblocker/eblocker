package org.eblocker.server.http.model;

/**
 * Secret-free runtime telemetry for one configured WireGuard peer.
 *
 * The public key is used only to correlate observed WireGuard runtime data
 * with the persisted peer. Private and preshared keys must never be exposed
 * through this type.
 */
public class WireGuardPeerTelemetry {

    private String publicKey;
    private long latestHandshakeEpochSeconds;
    private long rxBytes;
    private long txBytes;

    public WireGuardPeerTelemetry() {
    }

    public WireGuardPeerTelemetry(
            String publicKey,
            long latestHandshakeEpochSeconds,
            long rxBytes,
            long txBytes) {

        this.publicKey = publicKey;
        this.latestHandshakeEpochSeconds =
                latestHandshakeEpochSeconds;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getLatestHandshakeEpochSeconds() {
        return latestHandshakeEpochSeconds;
    }

    public void setLatestHandshakeEpochSeconds(
            long latestHandshakeEpochSeconds) {

        this.latestHandshakeEpochSeconds =
                latestHandshakeEpochSeconds;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }
}
