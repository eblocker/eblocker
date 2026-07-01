export type DnsResolverModeId = 'dhcp' | 'tor' | 'custom';
export type DnsServerRating = 'GOOD' | 'MEDIUM' | 'BAD';
export type NetworkEndpointMethod = 'GET' | 'PUT' | 'POST' | 'DELETE';
export type NetworkWizardFlowId = 'automatic' | 'individual';

export interface NetworkCenterEndpoint {
  readonly method: NetworkEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface DnsResolverMode {
  readonly id: DnsResolverModeId;
  readonly label: string;
  readonly description: string;
  readonly available: boolean;
  readonly legacyTemplate: string;
}

export interface DnsServerRow {
  readonly orderNumber: number;
  readonly server: string;
  readonly responseTimeRating: 'schnell' | 'mittel' | 'langsam';
  readonly responseTimeAverageMs: number;
  readonly reliabilityRating: 'zuverlässig' | 'instabil' | 'fehlerhaft';
  readonly valid: number;
  readonly invalid: number;
  readonly timeout: number;
  readonly error: number;
  readonly rating: DnsServerRating;
  readonly editable: boolean;
}

export interface DnsLocalRecord {
  readonly name: string;
  readonly ipAddress?: string;
  readonly ip6Address?: string;
  readonly builtin: boolean;
}

export interface NetworkIpv4Config {
  readonly mode: 'automatic' | 'expert' | 'individual';
  readonly ipAddress: string;
  readonly networkMask: string;
  readonly gateway: string;
  readonly dhcpService: 'eblocker' | 'external';
  readonly dhcpRangeFirst: string;
  readonly dhcpRangeLast: string;
  readonly dhcpLeaseTimeSeconds: number;
  readonly advisedNameServer: string;
  readonly ipFixedByDefault: boolean;
}

export interface NetworkIpv6Config {
  readonly routerAdvertisementsEnabled: boolean;
  readonly privacyExtensionsEnabled: boolean;
  readonly localAddresses: readonly string[];
  readonly globalAddresses: readonly string[];
  readonly warnings: readonly ('global-address-missing' | 'ip6-leak')[];
}

export interface NetworkWizardFlow {
  readonly id: NetworkWizardFlowId;
  readonly title: string;
  readonly legacySteps: readonly string[];
  readonly dhcpCheck: string;
  readonly rebootAware: boolean;
}

export const networkCenterLegacyStates = [
  'dns',
  'dnsstate',
  'dnsstatus',
  'dnsserver',
  'dnslocal',
  'network',
  'networksettings',
  'networksettingsip6',
  'network-wizard'
] as const;

export const networkCenterEndpoints: readonly NetworkCenterEndpoint[] = [
  {
    method: 'GET',
    legacy: '/api/adminconsole/dns/status',
    modern: '/api/v1/dns/status',
    purpose: 'DNS-Schalter laden'
  },
  {
    method: 'DELETE',
    legacy: '/api/adminconsole/dns/cache',
    modern: '/api/v1/dns/cache',
    purpose: 'DNS-Cache leeren'
  },
  {
    method: 'PUT',
    legacy: '/api/adminconsole/dns/config/resolvers',
    modern: '/api/v1/dns/resolvers',
    purpose: 'Resolver-Modus und Custom-Server speichern'
  },
  {
    method: 'PUT',
    legacy: '/api/adminconsole/dns/config/records',
    modern: '/api/v1/dns/records',
    purpose: 'Lokale DNS-Records speichern'
  },
  {
    method: 'GET',
    legacy: '/api/adminconsole/dns/stats?hours={hours}',
    modern: '/api/v1/dns/stats?hours={hours}',
    purpose: 'DNS-Server-Qualität/Statistik laden'
  },
  {
    method: 'PUT',
    legacy: '/api/adminconsole/network',
    modern: '/api/v1/network/config',
    purpose: 'IPv4-/DHCP-Konfiguration speichern'
  },
  {
    method: 'GET',
    legacy: '/api/adminconsole/network/dhcpstate',
    modern: '/api/v1/network/dhcp/state',
    purpose: 'DHCP-Zustand prüfen'
  },
  {
    method: 'GET',
    legacy: '/api/adminconsole/network/setupPageInfo',
    modern: '/api/v1/network/setup-info',
    purpose: 'Setup-/Wizard-Startdaten laden'
  },
  {
    method: 'GET',
    legacy: '/api/adminconsole/network/dhcpservers',
    modern: '/api/v1/network/dhcp/servers',
    purpose: 'DHCP-Server im Netz scannen'
  },
  {
    method: 'GET',
    legacy: '/api/adminconsole/network/ip6',
    modern: '/api/v1/network/ipv6',
    purpose: 'IPv6-Konfiguration laden'
  },
  {
    method: 'PUT',
    legacy: '/api/adminconsole/network/ip6',
    modern: '/api/v1/network/ipv6',
    purpose: 'IPv6 Router Advertisements/Privacy speichern'
  }
];

export const dnsResolverModes: readonly DnsResolverMode[] = [
  {
    id: 'dhcp',
    label: 'Standard über DHCP',
    description: 'DNS an das Gateway/den lokalen DHCP-Resolver weiterleiten, nur im automatischen Modus verfügbar.',
    available: true,
    legacyTemplate: 'dns-status.component.html: MODE_STANDARD'
  },
  {
    id: 'tor',
    label: 'Tor-Resolver',
    description: 'DNS über 127.0.0.1/Tor auflösen, auch für Geräte ohne Tor-Routing.',
    available: true,
    legacyTemplate: 'dns-status.component.html: MODE_TOR'
  },
  {
    id: 'custom',
    label: 'Benutzerdefinierte Liste',
    description: 'Eigene Resolver-Liste mit Standard-, Round-Robin- oder Zufallsstrategie.',
    available: true,
    legacyTemplate: 'dns-server.component.html'
  }
];

export const dnsServerRows: readonly DnsServerRow[] = [
  {
    orderNumber: 0,
    server: '1.1.1.1',
    responseTimeRating: 'schnell',
    responseTimeAverageMs: 18,
    reliabilityRating: 'zuverlässig',
    valid: 2380,
    invalid: 2,
    timeout: 0,
    error: 0,
    rating: 'GOOD',
    editable: true
  },
  {
    orderNumber: 1,
    server: '9.9.9.9',
    responseTimeRating: 'schnell',
    responseTimeAverageMs: 23,
    reliabilityRating: 'zuverlässig',
    valid: 2301,
    invalid: 4,
    timeout: 1,
    error: 0,
    rating: 'GOOD',
    editable: true
  },
  {
    orderNumber: 2,
    server: '2a12:de40:39:1::1',
    responseTimeRating: 'mittel',
    responseTimeAverageMs: 47,
    reliabilityRating: 'instabil',
    valid: 2140,
    invalid: 23,
    timeout: 8,
    error: 1,
    rating: 'MEDIUM',
    editable: true
  },
  {
    orderNumber: 3,
    server: '8.8.8.8',
    responseTimeRating: 'langsam',
    responseTimeAverageMs: 91,
    reliabilityRating: 'fehlerhaft',
    valid: 1850,
    invalid: 130,
    timeout: 38,
    error: 12,
    rating: 'BAD',
    editable: true
  }
];

export const dnsLocalRecords: readonly DnsLocalRecord[] = [
  { name: 'eblocker.box', ipAddress: '10.0.17.2', ip6Address: '2a12:de40:39:1::142', builtin: true },
  { name: 'router.lan', ipAddress: '10.0.17.254', ip6Address: '2a12:de40:39:1::1', builtin: true },
  { name: 'vaultwarden.lan', ipAddress: '10.0.17.4', ip6Address: '2a12:de40:39:1::1e4', builtin: false },
  { name: 'jellyfin.lan', ipAddress: '10.0.17.120', builtin: false },
  { name: 'printer.lan', ipAddress: '10.0.17.88', ip6Address: 'fd00:17::88', builtin: false }
];

export const networkIpv4Config: NetworkIpv4Config = {
  mode: 'expert',
  ipAddress: '10.0.17.2',
  networkMask: '255.255.255.0',
  gateway: '10.0.17.254',
  dhcpService: 'eblocker',
  dhcpRangeFirst: '10.0.17.100',
  dhcpRangeLast: '10.0.17.220',
  dhcpLeaseTimeSeconds: 86400,
  advisedNameServer: '10.0.17.2',
  ipFixedByDefault: true
};

export const networkDhcpServers = ['10.0.17.254', '10.0.17.2'] as const;

export const networkIpv6Config: NetworkIpv6Config = {
  routerAdvertisementsEnabled: true,
  privacyExtensionsEnabled: false,
  localAddresses: ['fe80::142/64'],
  globalAddresses: [],
  warnings: ['global-address-missing']
};

export const networkWizardFlows: readonly NetworkWizardFlow[] = [
  {
    id: 'automatic',
    title: 'Automatischer Netzwerkmodus',
    legacySteps: ['Vorbereiten', 'Prozess', 'Ausführen'],
    dhcpCheck: 'Sucht bis zu 10-mal nach einem DHCP-Server außer eBlocker und erlaubt manuelle Bestätigung.',
    rebootAware: true
  },
  {
    id: 'individual',
    title: 'Individuelle Netzwerkeinstellungen',
    legacySteps: ['Vorbereiten', 'Prozess', 'Einstellungen drucken', 'Ausführen'],
    dhcpCheck: 'Prüft, ob externe DHCP-Server verschwunden sind und erzeugt druckbare IP/Gateway/DNS-Werte.',
    rebootAware: true
  }
];

export const networkCenterCapabilities = [
  'DNS ein-/ausschalten und Cache leeren',
  'Resolver-Modus DHCP/Tor/Custom mit Strategie wählen',
  'DNS-Server sortieren, bewerten, hinzufügen, bearbeiten und löschen',
  'Lokale DNS-Records mit IPv4/IPv6 und Built-in-Schutz verwalten',
  'IPv4-Modus, Gateway, DHCP-Bereich und Lease-Time bearbeiten',
  'IPv6 Router Advertisements, Privacy Extensions und Warnungen steuern',
  'Automatischen und individuellen Netzwerk-Wizard inklusive DHCP-Prüfung abbilden'
] as const;

export function getDnsServerRatingTotals(): { good: number; medium: number; bad: number } {
  return dnsServerRows.reduce(
    (totals, row) => {
      if (row.rating === 'GOOD') totals.good += 1;
      if (row.rating === 'MEDIUM') totals.medium += 1;
      if (row.rating === 'BAD') totals.bad += 1;
      return totals;
    },
    { good: 0, medium: 0, bad: 0 }
  );
}

export function getNetworkCenterTotals(): {
  legacyStates: number;
  endpoints: number;
  dnsServers: number;
  localRecords: number;
  wizardFlows: number;
} {
  return {
    legacyStates: networkCenterLegacyStates.length,
    endpoints: networkCenterEndpoints.length,
    dnsServers: dnsServerRows.length,
    localRecords: dnsLocalRecords.length,
    wizardFlows: networkWizardFlows.length
  };
}
