package org.eblocker.server.http.model;

/**
 * Dedicated authenticated response containing a WireGuard client
 * configuration.
 *
 * Unlike WireGuardPeerView this representation is intentionally sensitive:
 * the rendered configuration contains the peer private key and preshared key
 * required by the WireGuard client. It must therefore only be exposed through
 * an authenticated and explicitly authorized sensitive route. Adminconsole
 * access is permitted; dashboard access must additionally bind the request to
 * the authorized device and its associated WireGuard peer.
 */
public class WireGuardClientConfigurationView {

    private int peerId;
    private String configuration;

    public WireGuardClientConfigurationView() {
    }

    public WireGuardClientConfigurationView(
            int peerId,
            String configuration) {

        this.peerId = peerId;
        this.configuration = configuration;
    }

    public int getPeerId() {
        return peerId;
    }

    public void setPeerId(int peerId) {
        this.peerId = peerId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
}
