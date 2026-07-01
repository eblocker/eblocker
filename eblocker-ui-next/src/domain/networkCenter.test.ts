import { describe, expect, it } from 'vitest';
import {
  dnsLocalRecords,
  dnsResolverModes,
  dnsServerRows,
  getDnsServerRatingTotals,
  getNetworkCenterTotals,
  networkCenterCapabilities,
  networkCenterEndpoints,
  networkCenterLegacyStates,
  networkDhcpServers,
  networkIpv4Config,
  networkIpv6Config,
  networkWizardFlows
} from './networkCenter';

describe('network/DNS center legacy parity', () => {
  it('covers every legacy DNS/network state that must move into the modern UI', () => {
    expect(networkCenterLegacyStates).toEqual([
      'dns',
      'dnsstate',
      'dnsstatus',
      'dnsserver',
      'dnslocal',
      'network',
      'networksettings',
      'networksettingsip6',
      'network-wizard'
    ]);
  });

  it('maps DnsService and NetworkService endpoints to /api/v1 targets', () => {
    expect(networkCenterEndpoints).toHaveLength(11);
    expect(networkCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual([
      '/api/adminconsole/dns/status',
      '/api/adminconsole/dns/cache',
      '/api/adminconsole/dns/config/resolvers',
      '/api/adminconsole/dns/config/records',
      '/api/adminconsole/dns/stats?hours={hours}',
      '/api/adminconsole/network',
      '/api/adminconsole/network/dhcpstate',
      '/api/adminconsole/network/setupPageInfo',
      '/api/adminconsole/network/dhcpservers',
      '/api/adminconsole/network/ip6',
      '/api/adminconsole/network/ip6'
    ]);
    expect(networkCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/'))).toBe(true);
  });

  it('keeps legacy DNS modes, server ordering/statistics and local DNS record behavior visible', () => {
    expect(dnsResolverModes.map((mode) => mode.id)).toEqual(['dhcp', 'tor', 'custom']);
    expect(dnsServerRows.map((row) => row.server)).toContain('1.1.1.1');
    expect(getDnsServerRatingTotals()).toEqual({ good: 2, medium: 1, bad: 1 });
    expect(dnsLocalRecords.some((record) => record.builtin)).toBe(true);
    expect(dnsLocalRecords.some((record) => !record.builtin && record.ip6Address)).toBe(true);
  });

  it('keeps IPv4/DHCP settings, IPv6 warnings and wizard flows represented', () => {
    expect(networkIpv4Config.mode).toBe('expert');
    expect(networkIpv4Config.dhcpService).toBe('eblocker');
    expect(networkDhcpServers).toContain('10.0.17.254');
    expect(networkIpv6Config.routerAdvertisementsEnabled).toBe(true);
    expect(networkIpv6Config.warnings).toContain('global-address-missing');
    expect(networkWizardFlows.map((flow) => flow.id)).toEqual(['automatic', 'individual']);
  });

  it('summarizes the center for dashboard cards and documents migrated capabilities', () => {
    expect(getNetworkCenterTotals()).toEqual({
      legacyStates: 9,
      endpoints: 11,
      dnsServers: 4,
      localRecords: 5,
      wizardFlows: 2
    });
    expect(networkCenterCapabilities).toEqual(expect.arrayContaining([
      'DNS ein-/ausschalten und Cache leeren',
      'Resolver-Modus DHCP/Tor/Custom mit Strategie wählen',
      'DNS-Server sortieren, bewerten, hinzufügen, bearbeiten und löschen',
      'Lokale DNS-Records mit IPv4/IPv6 und Built-in-Schutz verwalten',
      'IPv4-Modus, Gateway, DHCP-Bereich und Lease-Time bearbeiten',
      'IPv6 Router Advertisements, Privacy Extensions und Warnungen steuern',
      'Automatischen und individuellen Netzwerk-Wizard inklusive DHCP-Prüfung abbilden'
    ]));
  });
});
