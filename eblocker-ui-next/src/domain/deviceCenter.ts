export type DeviceProtectionMode = 'protected' | 'learning' | 'paused' | 'disabled';
export type DeviceConnectionState = 'online' | 'offline';
export type DeviceKind = 'laptop' | 'mobile' | 'desktop' | 'tablet' | 'tv' | 'server' | 'printer' | 'guest';

export interface DeviceCenterEndpoint {
  readonly id: string;
  readonly method: 'GET' | 'PUT' | 'POST' | 'DELETE';
  readonly legacyPath: string;
  readonly modernPath: string;
  readonly purpose: string;
}

export interface DeviceCenterRow {
  readonly id: string;
  readonly name: string;
  readonly kind: DeviceKind;
  readonly ipAddresses: readonly string[];
  readonly macAddress: string;
  readonly vendor: string;
  readonly assignedProfile: string;
  readonly assignedUser: string;
  readonly protectionMode: DeviceProtectionMode;
  readonly connectionState: DeviceConnectionState;
  readonly blockedToday: number;
  readonly isCurrentDevice: boolean;
  readonly isEblocker: boolean;
  readonly isGateway: boolean;
  readonly lastSeen: string;
  readonly sslEnabled: boolean;
  readonly mobileEnabled: boolean;
  readonly vpnActive: boolean;
  readonly torActive: boolean;
  readonly iconMode: string;
  readonly deletable: boolean;
}

export interface DeviceDetailTab {
  readonly id: string;
  readonly label: string;
  readonly legacyTemplate: string;
  readonly modernIntent: string;
}

export interface DeviceDetailPanel {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly action: string;
}

export interface DeviceDetail extends DeviceCenterRow {
  readonly detailPanels: readonly DeviceDetailPanel[];
  readonly enabledActions: readonly string[];
}

export interface DeviceDiscoverySettings {
  readonly scanIntervalSeconds: number;
  readonly scanningAvailable: boolean;
  readonly autoEnableNewDevices: boolean;
  readonly lastManualScan: string;
}

export interface DeviceCenterTotals {
  readonly total: number;
  readonly protected: number;
  readonly paused: number;
  readonly learning: number;
  readonly offline: number;
  readonly deletable: number;
  readonly currentDeviceName: string;
}

export const legacyDeviceStateNames = [
  'devices',
  'devicesstate',
  'deviceslist',
  'devicesdiscovery',
  'devicedetails'
] as const;

export const deviceCenterEndpoints: readonly DeviceCenterEndpoint[] = [
  {
    id: 'list',
    method: 'GET',
    legacyPath: '/api/adminconsole/devices',
    modernPath: '/api/v1/devices',
    purpose: 'Geräteliste laden, sortieren, filtern und mit Anzeige-IP/Name normalisieren.'
  },
  {
    id: 'detail-update',
    method: 'PUT',
    legacyPath: '/api/adminconsole/devices/{id}',
    modernPath: '/api/v1/devices/{id}',
    purpose: 'Geräte-Details aktualisieren: Name, statische IPs, Benutzer, HTTPS, Filter, Icon, Nachrichten.'
  },
  {
    id: 'bulk-delete',
    method: 'DELETE',
    legacyPath: '/api/adminconsole/devices/all/{mode}',
    modernPath: '/api/v1/devices/bulk-delete/{mode}',
    purpose: 'Mehrfachauswahl löschen: Offline, No-IP oder alle löschbaren Geräte.'
  },
  {
    id: 'scan-interval',
    method: 'POST',
    legacyPath: '/api/adminconsole/devices/scanningInterval',
    modernPath: '/api/v1/devices/discovery/scanning-interval',
    purpose: 'Automatisches Scan-Intervall lesen/ändern.'
  },
  {
    id: 'manual-scan',
    method: 'POST',
    legacyPath: '/api/adminconsole/devices/scan',
    modernPath: '/api/v1/devices/discovery/scan',
    purpose: 'Manuellen Netzwerkscan starten und Fortschritt anzeigen.'
  },
  {
    id: 'auto-enable',
    method: 'POST',
    legacyPath: '/api/adminconsole/devices/autoEnableNewDevices',
    modernPath: '/api/v1/devices/discovery/auto-enable-new-devices',
    purpose: 'Neue Geräte automatisch aktivieren oder nur erkennen.'
  }
];

export const deviceCenterCapabilities = [
  'Schutz ein-/ausschalten',
  'Gerät löschen und Offline-/No-IP-Auswahl',
  'Aktuelles Gerät anspringen',
  'Scan-Intervall ändern',
  'Manuellen Netzwerk-Scan starten',
  'Neue Geräte automatisch aktivieren',
  'Name und statische IPv4/IPv6 bearbeiten',
  'Benutzer/Profile zuordnen',
  'HTTPS, Filter, Anonymisierung, Mobile, Icon und Nachrichten konfigurieren'
] as const;

export const deviceDetailTabs: readonly DeviceDetailTab[] = [
  { id: 'device', label: 'Gerät', legacyTemplate: 'devices-details-device.component.html', modernIntent: 'Name, Hersteller, MAC, IPs, statische IPv4/IPv6 und Reset.' },
  { id: 'users', label: 'Benutzer', legacyTemplate: 'devices-details-users.component.html', modernIntent: 'Zugeordneter und aktuell operierender Benutzer/Profile.' },
  { id: 'anon', label: 'Anonymisierung', legacyTemplate: 'devices-details-anon.component.html', modernIntent: 'Tor/VPN-Routing, User-Agent-Cloaking und Referrer-Schutz.' },
  { id: 'mobile', label: 'Mobile', legacyTemplate: 'devices-details-mobile.component.html', modernIntent: 'Mobile/VPN-Konfiguration, Zertifikat/Profil und Verbindungsstatus.' },
  { id: 'filters', label: 'Filter', legacyTemplate: 'devices-details-filters.component.html', modernIntent: 'Werbung, Tracker, Malware und Profilfilter pro Gerät.' },
  { id: 'https', label: 'HTTPS', legacyTemplate: 'devices-details-https.component.html', modernIntent: 'HTTPS-Inspection, Trusted Apps/Domains und Gerätezertifikat.' },
  { id: 'icon', label: 'Icon', legacyTemplate: 'devices-details-icon.component.html', modernIntent: 'Controlbar/eBlocker-Icon-Modus und Sichtbarkeit.' },
  { id: 'messages', label: 'Nachrichten', legacyTemplate: 'devices-details-messages.component.html', modernIntent: 'Info-/Warnmeldungen pro Gerät konfigurieren.' }
];

export const deviceDiscoverySettings: DeviceDiscoverySettings = {
  scanIntervalSeconds: 300,
  scanningAvailable: true,
  autoEnableNewDevices: false,
  lastManualScan: 'vor 12 Minuten'
};

export const deviceCenterRows: readonly DeviceCenterRow[] = [
  {
    id: 'macbook', name: 'MacBook Pro', kind: 'laptop', ipAddresses: ['10.0.17.24', '2a12:de40:39:1::24'], macAddress: 'A4:83:E7:12:44:90', vendor: 'Apple', assignedProfile: 'Erwachsene', assignedUser: 'Shedowe', protectionMode: 'protected', connectionState: 'online', blockedToday: 1842, isCurrentDevice: true, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: true, mobileEnabled: true, vpnActive: false, torActive: false, iconMode: 'Browser + 5s', deletable: false
  },
  {
    id: 'iphone', name: 'iPhone', kind: 'mobile', ipAddresses: ['10.0.17.42'], macAddress: '92:33:18:91:AA:FE', vendor: 'Apple private MAC', assignedProfile: 'Erwachsene', assignedUser: 'Shedowe', protectionMode: 'protected', connectionState: 'online', blockedToday: 921, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: true, mobileEnabled: true, vpnActive: true, torActive: false, iconMode: 'Browser', deletable: false
  },
  {
    id: 'gaming-pc', name: 'Gaming PC', kind: 'desktop', ipAddresses: ['10.0.17.71'], macAddress: '38:D5:47:11:C0:01', vendor: 'ASUS', assignedProfile: 'Gaming', assignedUser: 'Gaming', protectionMode: 'learning', connectionState: 'online', blockedToday: 337, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: false, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Aus', deletable: false
  },
  {
    id: 'kids-tablet', name: 'Kids Tablet', kind: 'tablet', ipAddresses: ['10.0.17.88'], macAddress: 'C0:FF:EE:44:12:88', vendor: 'Samsung', assignedProfile: 'Kinder', assignedUser: 'Kind', protectionMode: 'protected', connectionState: 'online', blockedToday: 2401, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: true, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Alle Geräte', deletable: false
  },
  {
    id: 'living-tv', name: 'Wohnzimmer TV', kind: 'tv', ipAddresses: ['10.0.17.109'], macAddress: '40:2F:86:82:11:09', vendor: 'LG', assignedProfile: 'Streaming', assignedUser: 'Streaming', protectionMode: 'paused', connectionState: 'online', blockedToday: 74, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: false, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Aus', deletable: false
  },
  {
    id: 'nas', name: 'NAS Backup', kind: 'server', ipAddresses: ['10.0.17.150'], macAddress: '00:11:32:8A:14:C2', vendor: 'Synology', assignedProfile: 'Infrastruktur', assignedUser: 'Admin', protectionMode: 'protected', connectionState: 'online', blockedToday: 159, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'online', sslEnabled: false, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Browser', deletable: false
  },
  {
    id: 'old-printer', name: 'Alter Drucker', kind: 'printer', ipAddresses: [], macAddress: '00:1B:A9:FF:00:12', vendor: 'HP', assignedProfile: 'Standard', assignedUser: 'Nicht zugeordnet', protectionMode: 'disabled', connectionState: 'offline', blockedToday: 0, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'vor 19 Tagen', sslEnabled: false, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Aus', deletable: true
  },
  {
    id: 'guest-phone', name: 'Gast Telefon', kind: 'guest', ipAddresses: ['10.0.17.201'], macAddress: 'A2:00:02:DE:AD:01', vendor: 'Private MAC', assignedProfile: 'Gast', assignedUser: 'Gast', protectionMode: 'protected', connectionState: 'offline', blockedToday: 0, isCurrentDevice: false, isEblocker: false, isGateway: false, lastSeen: 'vor 6 Tagen', sslEnabled: false, mobileEnabled: false, vpnActive: false, torActive: false, iconMode: 'Aus', deletable: true
  }
];

export const selectedDeviceDetail: DeviceDetail = {
  ...deviceCenterRows[0],
  detailPanels: [
    { id: 'identity', label: 'Identität', value: 'Name, Hersteller, MAC und aktuelle IPs editierbar', action: 'Name bearbeiten' },
    { id: 'static-ip', label: 'Statische IP', value: 'IPv4 10.0.17.24 · IPv6 vorbereitet', action: 'IPv4/IPv6 setzen' },
    { id: 'owner', label: 'Benutzer/Profile', value: 'Shedowe · Erwachsene · Wechsel setzt operatingUser zurück', action: 'Profil ändern' },
    { id: 'https', label: 'HTTPS', value: 'HTTPS aktiv · Trusted Apps/Domains werden respektiert', action: 'HTTPS prüfen' },
    { id: 'filtering', label: 'Filter', value: 'Ads/Tracker/Malware aktiv · 1.842 Treffer heute', action: 'Filter öffnen' },
    { id: 'mobile', label: 'Mobile/VPN', value: 'Mobile-Konfiguration vorhanden · VPN aktuell getrennt', action: 'Konfiguration laden' },
    { id: 'messages', label: 'Nachrichten', value: 'Info + Warnungen sichtbar', action: 'Meldungen konfigurieren' }
  ],
  enabledActions: ['Schutz pausieren', 'Gerät aktualisieren', 'Reset bestätigen', 'Zum aktuellen Gerät springen']
};

export function getLegacyDeviceStateNames(): readonly string[] {
  return [...legacyDeviceStateNames];
}

export function getCurrentDevice(): DeviceCenterRow | undefined {
  return deviceCenterRows.find((device) => device.isCurrentDevice);
}

export function getDeletableDeviceIds(): readonly string[] {
  return deviceCenterRows.filter((device) => device.deletable).map((device) => device.id);
}

export function getSelectedDeviceDetail(): DeviceDetail {
  return selectedDeviceDetail;
}

export function getDeviceCenterTotals(): DeviceCenterTotals {
  const currentDevice = getCurrentDevice();
  return {
    total: deviceCenterRows.length,
    protected: deviceCenterRows.filter((device) => device.protectionMode === 'protected').length,
    paused: deviceCenterRows.filter((device) => device.protectionMode === 'paused').length,
    learning: deviceCenterRows.filter((device) => device.protectionMode === 'learning').length,
    offline: deviceCenterRows.filter((device) => device.connectionState === 'offline').length,
    deletable: getDeletableDeviceIds().length,
    currentDeviceName: currentDevice?.name ?? 'Unbekannt'
  };
}
