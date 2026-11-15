# WireGuard configuration backup and restore

## Backup version

WireGuard enters the native eBlocker configuration backup format with container
version **6**.

Versions 1 through 5 retain their existing entry/provider contracts. The
version 6 provider order is based on version 5 and inserts
`WireGuardBackupProvider` immediately after `UsersBackupProvider`.

## Backed-up semantic state

`eblocker-config/wireGuard.json` contains:

- global WireGuard enabled intent;
- external endpoint selection;
- complete persistent peer records:
  - peer id
  - name
  - private key
  - public key
  - preshared key
  - tunnel address (`allowedIp`)
  - optional device binding
  - LAN-access policy
  - tunnel mode
  - custom AllowedIPs;
- persistent WireGuard server private key, when one exists.

Device `wireGuardEnabled` is already part of the serialized `Device`.
User `wireGuardEnabled` is explicitly copied by `UsersBackupProvider` because
users receive new IDs during restore.

Runtime telemetry, handshakes, counters and derived firewall/wg0 state are not
backed up.

## Encryption

`WireGuardBackupProvider` uses Boris' existing password-derived
`JsonEncryptionModule`.

The WireGuard server private key and the existing `WireGuardPeer` private and
preshared keys use `@JsonEncrypt`.

When an export is created without a backup password, secret-bearing WireGuard
peer/server material is intentionally omitted. An import that cannot decrypt
WireGuard data leaves WireGuard disabled and returns
`NO_PASSWORD_WIREGUARD_NOT_IMPORTED`.

## Server identity

The persistent server identity is `/etc/wireguard/wg0.key` with public key
derived into `/etc/wireguard/wg0.pub`.

Preserving the private key is required so previously distributed client
configurations keep the same server public key.

The Java service never passes the key through process arguments. Backup/restore
uses an owner-only `/tmp/eblocker-wireguard-server-key-*.key` transfer file.
The privileged script validates the path and key, derives the public key and
regenerates `wg0.conf` from the restored identity.

## Import lifecycle

`BackupProvider` has backward-compatible no-op hooks:

- `prepareImport()`
- `finishImport()`

Configuration import invokes all preparation hooks before the first provider
mutates state and invokes finish hooks only after every provider imported
successfully.

WireGuard uses these hooks to:

1. stop and persist global disabled state before Users/Devices are changed;
2. restore encrypted semantic state while runtime stays disabled;
3. re-enable WireGuard only after the complete configuration import succeeds.

If a provider fails after preparation, finish hooks are not called and
WireGuard stays fail-closed.

## Peer IDs and allocator state

Peer IDs are restored exactly. The persisted peer ID allocator sequence is
also semantic backup state and is restored exactly. This matters after peer
deletion: active peers can be IDs 1 and 3 while the allocator has already
advanced to 5. In that state the next peer must remain ID 6 after restore, not
reuse retired IDs 4 or 5.

An early local version-6 backup without `peerIdSequence` remains readable: the
import falls back to the highest restored peer ID. A present sequence lower
than the highest restored peer ID is rejected as inconsistent.

## Endpoint verification

`WireGuardClientConfigurationService.normalizeEndpointConfig()` is the shared
side-effect-free validator/normalizer used by both the settings API and backup
verification.

## Missing device bindings

A peer's device binding is semantic intent and is preserved even if its device
cannot currently be resolved. The shared runtime peer selector fails such a
peer closed until the bound device exists and authorization permits it.

## Deployment gate

Before production use, `.167` must pass a real backup roundtrip:

backup -> controlled state change -> verify -> restore -> runtime reconcile

and then validate:

- server public key unchanged;
- peer IDs and client addresses unchanged;
- private/preshared peer key usability;
- endpoint;
- device/user authorization;
- LAN access;
- FULL/LAN_ONLY/CUSTOM routing;
- wg0 runtime peer set;
- firewall;
- OpenVPN unaffected.
