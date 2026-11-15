package org.eblocker.server.common.data.wireguard;

import java.util.Collection;
import java.util.List;

/**
 * Projects persisted WireGuard peers into peers eligible to influence active
 * runtime state.
 */
public interface WireGuardRuntimePeerSelector {

    List<WireGuardPeer> select(
            Collection<WireGuardPeer> peers);
}
