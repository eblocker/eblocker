package org.eblocker.server.common.data.wireguard;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal container used by controllers/services to return a peer list.
 */
public class WireGuardPeerStore {

    private List<WireGuardPeer> peers = new ArrayList<>();

    public List<WireGuardPeer> getPeers() {
        return peers;
    }

    public void setPeers(List<WireGuardPeer> peers) {
        this.peers = peers;
    }
}
