# DNS transport modes and DNSSEC

The DNS status page offers the existing DHCP, Tor, and custom plain DNS modes
alongside plain DNS with optional DNSSEC validation and separate DNS-over-TLS (DoT) and DNS-over-HTTPS (DoH) modes. DoT and
DoH each have a tab with an independent ordered server list. Plain DNSSEC also
has a separate tab with its own IPv4/IPv6 server list and selection strategy. Each list supports
add, edit, removal, search, ordering, and sequential, round-robin, or random
selection. The active encrypted list reports response and reliability statistics
per endpoint.

Each encrypted endpoint uses an IP literal followed by | and the TLS certificate
name. Examples: 9.9.9.9|dns.quad9.net and
https://9.9.9.9/dns-query|dns.quad9.net. IPv6 literals are supported, for
example 2620:fe::fe|dns.quad9.net for DoT and
https://[2620:fe::fe]/dns-query|dns.quad9.net for DoH. IPv6 requires a
working IPv6 route; it was not tested live on the IPv4-only T620 network.
The IP literal avoids bootstrap DNS
recursion. Each endpoint has its own verified TLS name. DoH currently supports
the standard /dns-query path. Each list permits up to 15 distinct entries.

DNSSEC validation is optional for DoT and DoH. Without it, CoreDNS filters
and forwards to the selected encrypted upstream. With it, CoreDNS filters,
forwards to Unbound on 127.0.0.1:5354, and Unbound forwards through a
CoreDNS loopback listener on 127.0.0.1:5355. Unbound validates signed DNS
answers. It does not fall back to plain recursion when an upstream fails.
Each encrypted endpoint has its own loopback listener and TLS configuration.

The plain DNSSEC option uses the existing secure-provider setting and
validates answers through local Unbound. The old single-provider secure
configuration remains readable for existing
installations. The UI presents it as the corresponding DoT or DoH mode and
supplies default server lists. Existing DHCP, Tor, and custom DNS paths are
unchanged when selected. Client devices must use eBlocker for DNS; this feature
does not intercept device-initiated DoT/DoH connections.

The CoreDNS package depends on unbound and unbound-anchor and installs
/etc/unbound/unbound.conf.d/eblocker-dnssec.conf. The root trust anchor is
maintained in /var/lib/unbound/root.key. CoreDNS is pinned in go.mod.

## Verification

- Go tests including multi-provider and invalid-configuration cases.
- EblockerDnsServerTest including independent lists and invalid endpoints.
- UI build with 398 Karma tests.
- Live DNS checks for plain, DoT, DoH with DNSSEC on and off, followed by
  rollback to the original mode and service health checks.

Default Quad9 and Cloudflare lists include both providers' published IPv4 and
IPv6 addresses. Existing custom lists are retained; IPv6 entries are added
automatically only when the list still matches the old two-address IPv4 preset.
The default sequential policy prefers the IPv4 entries while IPv6 routing is
unavailable. A round-robin or random policy should be selected only after
IPv6 transport has been verified on the eBlocker host.
