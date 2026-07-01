export type HealthLevel = 'online' | 'warning' | 'offline';
export type ProtectionState = 'protected' | 'learning' | 'paused';

export interface DashboardMetric {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly detail: string;
  readonly trend: string;
  readonly tone: 'cyan' | 'green' | 'violet' | 'amber';
}

export interface DeviceRow {
  readonly id: string;
  readonly name: string;
  readonly type: string;
  readonly ipAddress: string;
  readonly profile: string;
  readonly protection: ProtectionState;
  readonly blockedToday: number;
  readonly status: HealthLevel;
}

export interface ServiceHealth {
  readonly id: string;
  readonly name: string;
  readonly detail: string;
  readonly status: HealthLevel;
  readonly latency: string;
}

export interface ProtectionModule {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly state: 'active' | 'warning' | 'planned';
  readonly coverage: number;
}

export interface NetworkCard {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly detail: string;
}

export interface ActivityEvent {
  readonly id: string;
  readonly time: string;
  readonly title: string;
  readonly detail: string;
  readonly tone: 'info' | 'success' | 'warning';
}

export interface FamilyProfile {
  readonly id: string;
  readonly name: string;
  readonly devices: number;
  readonly schedule: string;
  readonly level: string;
}

export const dashboardMetrics: readonly DashboardMetric[] = [
  {
    id: 'devices',
    label: 'Aktive Geräte',
    value: '18',
    detail: '14 geschützt · 2 lernen · 2 pausiert',
    trend: '+3 seit gestern',
    tone: 'cyan'
  },
  {
    id: 'blocked',
    label: 'Blockierte Anfragen',
    value: '12.486',
    detail: 'Tracker, Ads, Malware und riskante Hosts',
    trend: '+18 % diese Woche',
    tone: 'green'
  },
  {
    id: 'dns',
    label: 'DNS-Anfragen',
    value: '84.201',
    detail: 'Lokale Resolver und verschlüsselte Upstreams',
    trend: '31 ms Ø Antwortzeit',
    tone: 'violet'
  },
  {
    id: 'https',
    label: 'HTTPS geprüft',
    value: '73 %',
    detail: 'Trusted Apps und Domains berücksichtigt',
    trend: '4 Hinweise offen',
    tone: 'amber'
  }
];

export const deviceRows: readonly DeviceRow[] = [
  { id: 'macbook', name: 'MacBook Pro', type: 'Laptop', ipAddress: '10.0.17.24', profile: 'Erwachsene', protection: 'protected', blockedToday: 1842, status: 'online' },
  { id: 'iphone', name: 'iPhone', type: 'Mobile', ipAddress: '10.0.17.42', profile: 'Erwachsene', protection: 'protected', blockedToday: 921, status: 'online' },
  { id: 'gaming-pc', name: 'Gaming PC', type: 'Desktop', ipAddress: '10.0.17.71', profile: 'Gaming', protection: 'learning', blockedToday: 337, status: 'warning' },
  { id: 'kids-tablet', name: 'Kids Tablet', type: 'Tablet', ipAddress: '10.0.17.88', profile: 'Kinder', protection: 'protected', blockedToday: 2401, status: 'online' },
  { id: 'tv', name: 'Wohnzimmer TV', type: 'Smart TV', ipAddress: '10.0.17.109', profile: 'Streaming', protection: 'paused', blockedToday: 74, status: 'online' },
  { id: 'nas', name: 'NAS Backup', type: 'Server', ipAddress: '10.0.17.150', profile: 'Infrastruktur', protection: 'protected', blockedToday: 159, status: 'online' }
];

export const serviceHealth: readonly ServiceHealth[] = [
  { id: 'dns', name: 'DNS Resolver', detail: 'Lokale Namensauflösung aktiv', status: 'online', latency: '31 ms' },
  { id: 'icap', name: 'ICAP Proxy', detail: 'HTTP/HTTPS-Filter bereit', status: 'online', latency: '12 ms' },
  { id: 'redis', name: 'Redis Datenbank', detail: 'Persistenz erreichbar', status: 'online', latency: '4 ms' },
  { id: 'squid', name: 'Squid Proxy', detail: 'Traffic-Inspection aktiv', status: 'online', latency: '18 ms' },
  { id: 'updates', name: 'Update-Kanal', detail: 'Letzter Check vor 38 Minuten', status: 'warning', latency: 'queued' }
];

export const protectionModules: readonly ProtectionModule[] = [
  { id: 'ads-trackers', name: 'Werbung & Tracker', description: 'Blocklisten, Pattern und Domain-Intelligence mit Live-Zähler.', state: 'active', coverage: 94 },
  { id: 'malware', name: 'Malware-Schutz', description: 'Riskante Hosts und Phishing-Domains mit Quarantäne-Hinweisen.', state: 'active', coverage: 88 },
  { id: 'parental-control', name: 'Kinderschutz', description: 'Profile, Zeitpläne, Ausnahmen und Bericht für jedes Gerät.', state: 'active', coverage: 76 },
  { id: 'vpn-mobile', name: 'VPN & Mobile', description: 'OpenVPN kompatibel, WireGuard-Ansicht vorbereitet.', state: 'warning', coverage: 61 }
];

export const networkCards: readonly NetworkCard[] = [
  { id: 'resolver', label: 'Primärer Resolver', value: '10.0.17.254', detail: 'OPNsense Gateway · Fallback DoT vorbereitet' },
  { id: 'https-mode', label: 'HTTPS-Modus', value: 'Selektiv', detail: 'Trusted Apps und Domains werden respektiert' },
  { id: 'vpn-users', label: 'Mobile Nutzer', value: '7', detail: '3 aktiv · 4 Konfigurationen ausgestellt' },
  { id: 'blocklists', label: 'Blocklisten', value: '42', detail: 'Letzte Aktualisierung vor 38 Minuten' }
];

export const activityEvents: readonly ActivityEvent[] = [
  { id: 'update', time: '19:08', title: 'Blocklisten aktualisiert', detail: '42 Listen synchronisiert, 18.201 neue Domains geprüft.', tone: 'success' },
  { id: 'device', time: '18:52', title: 'Neues Gerät erkannt', detail: 'Gaming PC wurde dem Profil „Gaming“ zugeordnet.', tone: 'info' },
  { id: 'ssl', time: '18:31', title: 'HTTPS-Hinweis', detail: '4 Apps nutzen noch den Legacy-Ausnahmepfad.', tone: 'warning' },
  { id: 'backup', time: '17:44', title: 'Konfigurationsbackup bereit', detail: 'Automatisches Backup kann heruntergeladen werden.', tone: 'success' }
];

export const familyProfiles: readonly FamilyProfile[] = [
  { id: 'adults', name: 'Erwachsene', devices: 7, schedule: 'Immer aktiv', level: 'Maximaler Schutz' },
  { id: 'kids', name: 'Kinder', devices: 4, schedule: '07:00–20:30', level: 'Streng' },
  { id: 'streaming', name: 'Streaming', devices: 3, schedule: 'Abends priorisiert', level: 'Werbung blockieren' }
];

export function getProtectedDeviceCount(): number {
  return deviceRows.filter((device) => device.protection === 'protected').length;
}

export function getTotalBlockedToday(): number {
  return deviceRows.reduce((sum, device) => sum + device.blockedToday, 0);
}

export function getCriticalServiceCount(): number {
  return serviceHealth.filter((service) => service.status === 'offline').length;
}
