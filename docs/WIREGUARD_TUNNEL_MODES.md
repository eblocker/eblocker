# WireGuard Tunnel Modes – Architecture

Status: WG-10B routing core

## Scope

WireGuard client routing supports three policy modes:

- `FULL_TUNNEL`
- `LAN_ONLY`
- `CUSTOM`

WG-10 is IPv4-only. There is no `::/0` route and no DNS directive added by
this work. `PersistentKeepalive = 25` remains unchanged.

## Policy is persisted; effective routes are resolved

`WireGuardPeer` stores routing intent:

- `tunnelMode`
- `customAllowedIps`

It does not persist the expanded route set for `LAN_ONLY`.

This separation is deliberate. It allows the implementation that resolves
LAN routes to improve later without changing the peer data model, API shape,
or client-config renderer.

Missing or explicit-null `tunnelMode` resolves to `FULL_TUNNEL`, preserving
existing peers. Missing/null `customAllowedIps` resolves to an empty list.

## `allowedIp` is not a client route list

`WireGuardPeer.allowedIp` remains exclusively the peer tunnel address, for
example `10.13.13.x/32`.

It is not reused for client `AllowedIPs`.

## Central route resolver

`WireGuardClientRouteResolver` is the single policy-to-route translation
point used by client configuration rendering.

- `FULL_TUNNEL` -> `0.0.0.0/0`
- `LAN_ONLY` -> `WireGuardLanRouteProvider`
- `CUSTOM` -> validated/canonicalized `customAllowedIps`

QR rendering continues to call the same `renderClientConfig()` method as
download rendering.

## LAN_ONLY – WG-10A decision B

WG-10A did not identify a canonical product-level API that returns the
complete actual local IPv4 LAN subnet set.

Therefore the current `WireGuardLanRouteProvider` implementation follows the
already established WireGuard firewall private/link-local contract:

1. `192.168.0.0/16`
2. `172.16.0.0/12`
3. `10.0.0.0/8`
4. `169.254.0.0/16`

The provider references the existing `NetworkUtils` constants instead of
duplicating those CIDR literals in production code.

## Future LAN resolver migration

`LAN_ONLY` is intentionally resolved through the
`WireGuardLanRouteProvider` abstraction.

A future implementation may add a canonical local-subnet provider. Such a
change must not silently alter the meaning of existing peers. If the
effective route contract changes, migration/versioning or a new explicit
policy must be used so existing installations keep their prior semantics
unless an administrator intentionally migrates them.

## LAN routing and LAN authorization are orthogonal

`tunnelMode` controls which destinations a client routes into WireGuard.

`allowLanAccess` controls whether server-side firewall policy permits the
peer to reach private/LAN targets.

Selecting `LAN_ONLY` never changes `allowLanAccess`.

## Reusable IPv4 CIDR primitive

`Ipv4Cidr` is a reusable project-level IPv4 CIDR value object.

It reuses the existing `Ip4Address` parser and adds:

- `/0` through `/32` prefix validation
- canonical network-address calculation
- canonical string form
- value equality

The older `NetworkUtils` netmask conversion helpers intentionally restrict
prefixes to LAN-style ranges and are therefore not reused for generic `/0`
or `/32` CIDR normalization.

`WireGuardCustomRouteValidator` adds WireGuard policy rules around this
generic primitive:

- CUSTOM requires at least one route
- IPv4 CIDRs only
- canonicalization
- stable-order deduplication
- no null/empty entries
- maximum 64 entries
- maximum 4096 characters of input

## Migration principle

New WireGuard work follows a project-wide rule:

- persist stable policy/intent
- isolate replaceable implementation behind small abstractions
- avoid coupling new behavior directly to known interim implementations
- never silently change existing semantics during later modernization
- document migration paths and compatibility boundaries

Atlas/CODEMAP remains paused and is not part of WG-10B.

## WG-10C – routing API and persistence boundary

The authenticated admin API adds:

`PUT /api/adminconsole/wireguard/peers/{id}/routing`

Request shape:

```json
{
  "tunnelMode": "FULL_TUNNEL | LAN_ONLY | CUSTOM",
  "customAllowedIps": ["192.168.1.0/24"]
}
```

The HTTP request model keeps `tunnelMode` as text so invalid mode names are
explicitly mapped to HTTP 400 rather than depending on implicit enum binding.

Validation and canonicalization are also enforced inside
`WireGuardPeerService`, below the controller boundary. A different future
caller therefore cannot bypass the CUSTOM IPv4 CIDR policy merely by avoiding
the HTTP controller.

Persistence semantics:

- missing peer -> not found
- `FULL_TUNNEL` and `LAN_ONLY` persist an empty CUSTOM route list
- `CUSTOM` persists the normalized/deduplicated IPv4 CIDR list
- idempotent updates do not write again
- a thrown persistence failure restores the previous in-memory mode/routes
- a null persistence result also restores the previous in-memory mode/routes

Routing-only changes do **not** call `WireGuardPeerSyncService` and do **not**
refresh the firewall. This is intentional: the server-side peer public key,
preshared key, `10.13.13.x/32` peer address and `allowLanAccess` policy are
unchanged. A newly downloaded/rendered client configuration picks up the new
routing policy through the central `WireGuardClientRouteResolver`.

`WireGuardPeerView` remains the normal secret-free representation and now also
contains:

- `tunnelMode`
- `customAllowedIps`

It still contains no private key or preshared key.

There is intentionally no Dashboard routing setter in WG-10. Tunnel routing
is an administrator-owned WireGuard peer configuration policy.

The existing constructor shapes used by focused legacy callers/tests remain
available. Production dependency injection uses the validator-aware
constructor.

## WG-10D – Settings peer-routing UI

The WireGuard peer table remains an overview and now also displays the
persisted tunnel mode. Editing is kept in a separate routing configuration
frame below the table. This isolates the editing workflow from the overview
and leaves room for later routing-policy extensions.

The editor provides peer selection plus `FULL_TUNNEL`, `LAN_ONLY`, and
`CUSTOM`. CUSTOM accepts one IPv4 CIDR per line.

The frontend intentionally does not duplicate the backend IPv4 CIDR parser.
It only requires at least one non-empty CUSTOM line. Backend
`WireGuardCustomRouteValidator` remains authoritative for validation,
normalization and deduplication.

`LAN_ONLY` with `allowLanAccess == false` shows an informational warning only.
The tunnel-mode UI never modifies `allowLanAccess`.

Failed writes restore the editor to the last persisted routing values and use
the normal Settings error notification. Successful writes use the canonical
routing fields returned by the backend.

This source tree currently has Settings locale bundles for DE and EN, but no
`lang-settings-fr.json`. WG-10D therefore extends the existing DE/EN bundles
and does not invent a new unsupported Settings locale.

No Dashboard routing setter and no routing control under
`Geräte -> VPN-Zugriff` are introduced.

## WG-10E – final verified contract

WG-10 is complete as one coherent tunnel-routing feature.

### Stable persisted intent

Peers persist the routing policy, not an expanded LAN route snapshot:

- `FULL_TUNNEL`
- `LAN_ONLY`
- `CUSTOM`

Existing peer records with missing or explicit-null routing fields continue as
`FULL_TUNNEL` with an empty CUSTOM route list. `WireGuardPeer.allowedIp`
remains the `10.13.13.x/32` peer tunnel address and is not repurposed as a
client route list.

### Replaceable LAN implementation

`LAN_ONLY` resolves through `WireGuardLanRouteProvider`. The current provider
uses the existing `NetworkUtils` private/link-local firewall contract:

1. `192.168.0.0/16`
2. `172.16.0.0/12`
3. `10.0.0.0/8`
4. `169.254.0.0/16`

A future canonical local-subnet provider may be added behind this abstraction.
Changing the provider must not silently change existing peer semantics.
A changed route contract requires an explicit migration/versioning decision or
a new policy so existing installations retain their effective behavior unless
an administrator intentionally migrates them.

### CUSTOM route validation

`Ipv4Cidr` is the reusable IPv4 CIDR primitive and supports canonical `/0`
through `/32` network forms. WireGuard-specific route-count/input-size policy
stays in `WireGuardCustomRouteValidator`.

The backend remains authoritative. The Settings UI does not duplicate the CIDR
parser and only checks that CUSTOM contains at least one non-empty route line.

### Authorization and routing remain independent

`tunnelMode` determines which client destinations enter the WireGuard tunnel.
`allowLanAccess` determines whether the server firewall allows a peer to reach
private/LAN destinations.

The Settings routing editor never grants LAN access automatically. LAN-only
with denied LAN access shows a warning only.

### Runtime boundary

Changing only the routing policy persists the peer policy but does not invoke
WireGuard peer reconciliation and does not refresh the firewall, because the
server peer key/address and `allowLanAccess` remain unchanged.

QR and downloadable configuration continue through the same client
configuration renderer.

### API/UI boundary

Routing mutation exists only on the authenticated administrator route:

`PUT /api/adminconsole/wireguard/peers/{id}/routing`

WG-10 does not add a Dashboard routing setter and does not add routing controls
to `Geräte -> VPN-Zugriff`.

`WireGuardPeerView` exposes the routing policy without exposing private or
preshared keys.

### Final verification

The WG-10D R1 commit candidate was verified with:

- JSHint: PASS
- Gulp `start-dev` / Browserify: PASS
- FirefoxHeadless: 355 / 355 SUCCESS
- frontend increase over the pre-WG-10D baseline: +6 tests
- backend compile: PASS
- focused WG-10 backend regression: 151 / 151, 0 failures, 0 errors
- `git diff --check`: PASS
- exact WG-10 changeset: 29 files
- no Dashboard routing setter
- no routing control in `Geräte -> VPN-Zugriff`
- no synthetic Settings French locale
- no push
- no deployment

The project-wide modernization rule applies to later work: persist stable
intent, isolate replaceable implementations behind small abstractions, avoid
new coupling to known interim implementations, preserve existing semantics
across upgrades, and document explicit migration paths.
