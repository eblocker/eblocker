package org.eblocker.server.common.data.wireguard;

import java.util.ArrayList;
import java.util.List;

public class WireGuardPeerStore {

    private List<WireGuardPeer> peers = new ArrayList<>();

    public WireGuardPeerStore() {
        // für JSON / Jackson
    }

    public List<WireGuardPeer> getPeers() {
        return peers;
    }

    public void setPeers(List<WireGuardPeer> peers) {
        this.peers = (peers != null) ? peers : new ArrayList<>();
    }
}
