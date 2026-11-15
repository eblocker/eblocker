# WireGuard runtime peer projection

Persistent WireGuard peers are configuration source of truth, but only
runtime-eligible peers may influence active WireGuard state.

WG-11 introduces `WireGuardRuntimePeerSelector` as the shared projection.

Current implementation: `WireGuardAuthorizedRuntimePeerSelector`.

Rules:

- unbound admin-created peers remain eligible for backward compatibility;
- a bound peer whose device no longer exists is denied;
- a bound peer requires the existing WireGuard device/user policy;
- the global server switch is intentionally not part of the projection because
  startup reconciles peers before persisting the enabled state.

Consumers:

1. `WireGuardPeerSyncService` before rendering/applying active `wg0` peers;
2. `NetworkServicesBase` before peers are passed to the IPv4 firewall.

`TableGeneratorIp4` stays a firewall renderer and does not evaluate
device/user authorization.

This ensures a peer denied from active `wg0` cannot retain a peer-specific LAN
firewall exception merely because its persistent `allowLanAccess` remains true.
