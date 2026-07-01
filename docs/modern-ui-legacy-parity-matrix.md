# Modern UI Legacy Parity Matrix

Generated from AngularJS route files and curated into migration groups. Do not remove the old UI until every group reaches real feature parity in `eblocker-ui-next`.

- Route files scanned: 6
- Legacy states mapped: 109

| Legacy group | Legacy states | Modules | Modern target | Stage |
|---|---:|---|---|---|
| Dashboard & Controlbar | 11 | controlbar, dashboard, settings | #dashboard, /api/v1/dashboard, /api/v1/controlbar | Prototype visible |
| Geräte & Clients | 5 | settings | #devices, /api/v1/devices | Prototype visible |
| Familie & Kinderschutz | 9 | settings | #family, /api/v1/parental-control | Prototype visible |
| Schutz, Filter & Privatsphäre | 11 | settings | #protection, /api/v1/protection, /api/v1/dns | API wiring needed |
| HTTPS & Zertifikate | 10 | dashboard, settings | #https, /api/v1/ssl | API wiring needed |
| Netzwerk, DNS & DHCP | 9 | settings | #network, /api/v1/network, /api/v1/dns | API wiring needed |
| VPN, Mobile & Remote | 6 | dashboard, settings | #vpn, /api/v1/vpn | API wiring needed |
| System, Updates & Administration | 20 | settings | #system, /api/v1/system | Mapped, not rebuilt yet |
| Zugang, Setup & Lifecycle | 16 | advice, settings | #setup, /api/v1/setup, /api/v1/auth | Mapped, not rebuilt yet |
| Block-/Fehlerseiten & Redirects | 12 | dashboard | #block-pages, /api/v1/block-pages | Mapped, not rebuilt yet |

## Dashboard & Controlbar

**Legacy:** Dashboard cards, current-device controls, console jump, controlbar status and remote dashboard behaviour.

**Modern target:** Das neue Dashboard übernimmt Monitoring, Gerätefokus, Empfehlungen und Schnellaktionen; Controlbar-spezifika werden als kompakte Command-Bar/Quick-Actions weitergeführt.

Legacy states:
- `home`
- `reset`
- `status`
- `print`
- `app`
- `main`
- `app`
- `action`
- `main`
- `appState`
- `mainState`

## Geräte & Clients

**Legacy:** Device list, discovery, details, pause/protection/profile state and per-device actions.

**Modern target:** Gerätetabelle und Schnellaktionen sind sichtbar; als nächstes echte DeviceService-Daten und Detailseiten anbinden.

Legacy states:
- `devices`
- `devicesstate`
- `deviceslist`
- `devicesdiscovery`
- `devicedetails`

## Familie & Kinderschutz

**Legacy:** Users, profiles, online-time schedules, blacklists/whitelists and profile-specific restrictions.

**Modern target:** Familienprofile, Schutzmodule und Empfehlungen sind im Dashboard sichtbar; Detail-CRUD und Listen-Migration folgen.

Legacy states:
- `parentalcontrol`
- `parentalcontrolstate`
- `users`
- `user-details`
- `userprofiledetails`
- `blacklists`
- `whitelists`
- `blacklistdetails`
- `whitelistdetails`

## Schutz, Filter & Privatsphäre

**Legacy:** Filter analysis, block/pass lists, anonymization, Tor, malware/ad/tracker blocking and advanced protection settings.

**Modern target:** Schutzmodule, Blockrate, Top-Domains und Empfehlungen sind angelegt; tiefe Filteranalyse wird als eigener Screen migriert.

Legacy states:
- `anonymization`
- `anonymizationstate`
- `tor`
- `filter`
- `filterstate`
- `filteroverview`
- `filter-details`
- `advancedsettings`
- `filteranalysis`
- `analysisdetails`
- `doctor`

## HTTPS & Zertifikate

**Legacy:** HTTPS wizard/status, root CA, trusted apps/domains, SSL failures and manual recording.

**Modern target:** HTTPS-Metrik, Root-CA Schnellaktion und Warnungen sind sichtbar; Zertifikats-/Trusted-App-Details brauchen echte API-Anbindung.

Legacy states:
- `https`
- `sslstate`
- `sslstatus`
- `sslcertificate`
- `sslfails`
- `trustedapps`
- `trustedappsdetails`
- `trusteddomains`
- `manualrecording`
- `https`

## Netzwerk, DNS & DHCP

**Legacy:** DNS status, DNS server/local records, network IPv4/IPv6 settings, setup wizard and resolver state.

**Modern target:** Netzwerkfluss, Resolver-Karten und DNS-Telemetrie sind sichtbar; Network/DNS-Editoren werden als nächstes ersetzt.

Legacy states:
- `dns`
- `dnsstate`
- `dnsstatus`
- `dnsserver`
- `dnslocal`
- `network`
- `networksettings`
- `networksettingsip6`
- `network-wizard`

## VPN, Mobile & Remote

**Legacy:** VPN Home, mobile setup wizard, OpenVPN compatibility, remote dashboard and future WireGuard path.

**Modern target:** VPN/Mobile-Modul und WireGuard-Empfehlung sind sichtbar; echte OpenVPN/WireGuard-Side-by-side UI folgt.

Legacy states:
- `vpnconnect`
- `vpnconnectdetails`
- `mobile`
- `vpn-home-wizard`
- `remote`
- `mobile`

## System, Updates & Administration

**Legacy:** License, update, about/legal, diagnostics, events, backup/reset, tasks, language, network doctor and OSS licenses.

**Modern target:** Systemzustand, Update-Kanal, Diagnose und Backup-Hinweise sind sichtbar; Admin-Detailseiten werden in neue System-Sektion migriert.

Legacy states:
- `default`
- `license`
- `update`
- `about`
- `legal`
- `system`
- `adminpassword`
- `diagnostics`
- `events`
- `backup`
- `tasks`
- `timeandlanguage`
- `systempending`
- `logout`
- `open-source-licenses`
- `open-source-licenses-java`
- `open-source-licenses-ccpp`
- `open-source-licenses-javascript`
- `open-source-licenses-ruby`
- `open-source-licenses-debian`

## Zugang, Setup & Lifecycle

**Legacy:** Login/auth, setup, activation, splash/boot/update/shutdown/factory-reset/standby screens and advice/reminders.

**Modern target:** Lifecycle-Screens sind vollständig inventarisiert und bekommen eigene Modern-UI-Flows vor Abschaltung des alten UI.

Legacy states:
- `nolicense`
- `standby`
- `factoryResetScreen`
- `booting`
- `updating`
- `shutdown`
- `activation`
- `activationfinish`
- `login`
- `resetpassword`
- `auth`
- `splash`
- `expired`
- `app`
- `welcome`
- `reminder`

## Block-/Fehlerseiten & Redirects

**Legacy:** Blocked/access-denied/malware/ad-tracker/squid/redirect option screens that users see during browsing.

**Modern target:** Blockseiten sind als eigener Migrationsbereich erfasst; sie dürfen nicht hinter Admin-Dashboard-Arbeit vergessen werden.

Legacy states:
- `redirect`
- `redirectOptions`
- `blockOptions`
- `blocker`
- `blockerAccessDenied`
- `blockerAdsTrackers`
- `blockerMalware`
- `blockerWhitelisted`
- `squidError`
- `blockerSslWhitelisted`
- `console`
- `logoutAdmin`
