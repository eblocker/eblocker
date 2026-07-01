export type HttpsEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export interface HttpsEndpoint {
  readonly method: HttpsEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface HttpsStatus {
  readonly enabled: boolean;
  readonly ataEnabled: boolean;
  readonly certificatesReady: boolean;
  readonly recordingEnabled: boolean;
  readonly patternDevices: number;
  readonly controlBarDevices: number;
}

export interface RootCertificateSummary {
  readonly commonName: string;
  readonly validFrom: string;
  readonly validUntil: string;
  readonly renewalCommonName: string;
  readonly renewalReady: boolean;
  readonly caRenewWeeks: number;
}

export interface TrustedAppRow {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly domainsIps: readonly string[];
  readonly builtin: boolean;
  readonly enabled: boolean;
  readonly modified: boolean;
  readonly hidden: boolean;
}

export interface TrustedDomainRow {
  readonly name: string;
  readonly url: string;
  readonly trustedAppName?: string;
  readonly builtin: boolean;
  readonly deletable: boolean;
  readonly enabled: boolean;
}

export interface SslFailureSuggestion {
  readonly domainIp: string;
  readonly devices: readonly string[];
  readonly lastOccurrence: string;
  readonly enabledModule?: string;
  readonly enabledWhitelist?: boolean;
}

export interface ManualRecordedConnection {
  readonly recordedDomain?: string;
  readonly recordedIp: string;
  readonly protocol: 'HTTPS' | 'HTTP';
  readonly currentRule: 'FILTER' | 'ALLOW';
  readonly recommendedRule: 'FILTER' | 'ALLOW' | 'NO_CHANGE';
  readonly tempRule: 'FILTER' | 'ALLOW' | 'NO_CHANGE';
}

export interface ManualRecordingState {
  readonly selectedDevice: string;
  readonly recordingStatus: boolean;
  readonly timeLimitMinutes: number;
  readonly sizeLimitMb: number;
  readonly tableColumns: readonly string[];
  readonly recordedConnections: readonly ManualRecordedConnection[];
}

export const httpsCenterLegacyStates = [
  'https',
  'sslstate',
  'sslstatus',
  'sslcertificate',
  'sslfails',
  'trustedapps',
  'trustedappsdetails',
  'trusteddomains',
  'manualrecording',
  'https'
] as const;

export const httpsCenterEndpoints: readonly HttpsEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/ssl/status', modern: '/api/v1/ssl/status', purpose: 'HTTPS-Status laden' },
  { method: 'POST', legacy: '/api/adminconsole/ssl/status', modern: '/api/v1/ssl/status', purpose: 'HTTPS-Status speichern' },
  { method: 'GET', legacy: '/api/adminconsole/ata/status', modern: '/api/v1/ssl/attack-target-analysis/status', purpose: 'ATA-Status laden' },
  { method: 'POST', legacy: '/api/adminconsole/ata/status', modern: '/api/v1/ssl/attack-target-analysis/status', purpose: 'ATA-Status speichern' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/status/renewal', modern: '/api/v1/ssl/certificates/renewal', purpose: 'CA-Erneuerungsstatus laden' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/rootca', modern: '/api/v1/ssl/root-ca', purpose: 'Root-CA laden' },
  { method: 'POST', legacy: '/api/adminconsole/ssl/rootca', modern: '/api/v1/ssl/root-ca', purpose: 'Root-CA erzeugen/erneuern' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/rootca/options', modern: '/api/v1/ssl/root-ca/options', purpose: 'CA-Optionen laden' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/certs/status', modern: '/api/v1/ssl/certificates/status', purpose: 'Zertifikatsstatus laden' },
  { method: 'POST', legacy: '/api/adminconsole/ssl/whitelist', modern: '/api/v1/ssl/trusted/whitelist', purpose: 'Trusted App/Domain speichern' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/errors', modern: '/api/v1/ssl/errors', purpose: 'SSL-Fehler laden' },
  { method: 'DELETE', legacy: '/api/adminconsole/ssl/errors', modern: '/api/v1/ssl/errors', purpose: 'SSL-Fehler leeren' },
  { method: 'GET', legacy: '/api/adminconsole/ssl/errors/recording', modern: '/api/v1/ssl/errors/recording', purpose: 'SSL-Fehleraufzeichnung laden' },
  { method: 'PUT', legacy: '/api/adminconsole/ssl/errors/recording', modern: '/api/v1/ssl/errors/recording', purpose: 'SSL-Fehleraufzeichnung speichern' },
  { method: 'GET', legacy: '/api/adminconsole/trustedapps/all', modern: '/api/v1/ssl/trusted-apps', purpose: 'Trusted Apps laden' },
  { method: 'GET', legacy: '/api/adminconsole/trustedapps/id/{id}', modern: '/api/v1/ssl/trusted-apps/{id}', purpose: 'Trusted App laden' },
  { method: 'POST', legacy: '/api/adminconsole/trustedapps/id/{id}', modern: '/api/v1/ssl/trusted-apps', purpose: 'Trusted App anlegen' },
  { method: 'PUT', legacy: '/api/adminconsole/trustedapps/id/{id}', modern: '/api/v1/ssl/trusted-apps/{id}', purpose: 'Trusted App aktualisieren' },
  { method: 'DELETE', legacy: '/api/adminconsole/trustedapps/id/{id}', modern: '/api/v1/ssl/trusted-apps/{id}', purpose: 'Trusted App löschen/zurücksetzen' },
  { method: 'PUT', legacy: '/api/adminconsole/trustedapps/enable', modern: '/api/v1/ssl/trusted-apps/{id}/enabled', purpose: 'Trusted App aktivieren/deaktivieren' },
  { method: 'GET', legacy: '/api/adminconsole/trusteddomains/onlyenabled', modern: '/api/v1/ssl/trusted-domains', purpose: 'Trusted Domains laden' },
  { method: 'PUT', legacy: '/api/adminconsole/trusteddomains/delete', modern: '/api/v1/ssl/trusted-domains/delete', purpose: 'Trusted Domain löschen' },
  { method: 'POST', legacy: '/api/adminconsole/recording/toggle', modern: '/api/v1/ssl/manual-recording/toggle', purpose: 'Manuelle Aufzeichnung starten/stoppen' },
  { method: 'GET', legacy: '/api/adminconsole/recording/status', modern: '/api/v1/ssl/manual-recording/status', purpose: 'Aufzeichnungsstatus laden' },
  { method: 'GET', legacy: '/api/adminconsole/recording/result', modern: '/api/v1/ssl/manual-recording/result', purpose: 'Aufzeichnungsergebnis laden' }
];

export const httpsStatus: HttpsStatus = {
  enabled: true,
  ataEnabled: true,
  certificatesReady: true,
  recordingEnabled: true,
  patternDevices: 5,
  controlBarDevices: 6
};

export const rootCertificate: RootCertificateSummary = {
  commonName: 'eBlocker Root CA',
  validFrom: '2026-01-12',
  validUntil: '2036-01-12',
  renewalCommonName: 'eBlocker Root CA 2036',
  renewalReady: true,
  caRenewWeeks: 8
};

export const trustedApps: readonly TrustedAppRow[] = [
  { id: 'banking', name: 'Banking Apps', description: 'HSTS/Pinning Ausnahmen', domainsIps: ['bank.example', 'secure-pay.example'], builtin: true, enabled: true, modified: false, hidden: false },
  { id: 'streaming', name: 'Streaming', description: 'CDN/DRM Ausnahmen', domainsIps: ['video.example', 'cdn.video.example'], builtin: true, enabled: true, modified: true, hidden: false },
  { id: 'school', name: 'Schule', description: 'Eigene Trusted-App', domainsIps: ['portal.school.example'], builtin: false, enabled: false, modified: true, hidden: false },
  { id: 'internal', name: 'Interne Services', description: 'Vault/Jellyfin', domainsIps: ['vaultwarden.lan', 'jellyfin.lan'], builtin: false, enabled: true, modified: true, hidden: false }
];

export const trustedDomains: readonly TrustedDomainRow[] = [
  { name: 'Bank Login', url: 'bank.example', trustedAppName: 'Banking Apps', builtin: true, deletable: false, enabled: true },
  { name: 'Streaming CDN', url: 'cdn.video.example', trustedAppName: 'Streaming', builtin: true, deletable: false, enabled: true },
  { name: 'Vaultwarden', url: 'vaultwarden.lan', trustedAppName: 'Interne Services', builtin: false, deletable: true, enabled: true },
  { name: 'Jellyfin', url: 'jellyfin.lan', trustedAppName: 'Interne Services', builtin: false, deletable: true, enabled: true },
  { name: 'Schulportal', url: 'portal.school.example', trustedAppName: 'Schule', builtin: false, deletable: true, enabled: false }
];

export const sslFailureSuggestions = {
  domainsIps: [
    { domainIp: 'login.bank.example', devices: ['MacBook Pro', 'iPhone'], lastOccurrence: 'vor 4 Minuten' },
    { domainIp: 'api.video.example', devices: ['Wohnzimmer TV'], lastOccurrence: 'vor 18 Minuten', enabledModule: 'Streaming' },
    { domainIp: '10.0.17.4', devices: ['MacBook Pro'], lastOccurrence: 'vor 31 Minuten', enabledWhitelist: true }
  ] as readonly SslFailureSuggestion[],
  modules: ['Banking Apps', 'Streaming', 'Interne Services'] as const
};

export const manualRecording: ManualRecordingState = {
  selectedDevice: 'MacBook Pro',
  recordingStatus: false,
  timeLimitMinutes: 5,
  sizeLimitMb: 100,
  tableColumns: ['domain', 'ip', 'protocol', 'currentRule', 'recommendedRule', 'tempRule'],
  recordedConnections: [
    { recordedDomain: 'login.bank.example', recordedIp: '203.0.113.10', protocol: 'HTTPS', currentRule: 'FILTER', recommendedRule: 'ALLOW', tempRule: 'NO_CHANGE' },
    { recordedDomain: 'api.video.example', recordedIp: '198.51.100.25', protocol: 'HTTPS', currentRule: 'FILTER', recommendedRule: 'ALLOW', tempRule: 'ALLOW' },
    { recordedIp: '10.0.17.4', protocol: 'HTTPS', currentRule: 'ALLOW', recommendedRule: 'NO_CHANGE', tempRule: 'ALLOW' },
    { recordedDomain: 'tracker.example', recordedIp: '192.0.2.9', protocol: 'HTTP', currentRule: 'FILTER', recommendedRule: 'FILTER', tempRule: 'FILTER' }
  ]
};

export const httpsCapabilities = [
  'HTTPS/SSL-Inspection und Attack-Target-Analysis schalten',
  'Root-CA anzeigen, erneuern und Zertifikatsstatus prüfen',
  'SSL-Fehler auswerten, leeren und Recording aktivieren',
  'Trusted Apps anlegen, aktivieren, zurücksetzen und Domains/IPs verwalten',
  'Trusted Domains einzeln oder gesammelt löschen',
  'Manuelle Aufzeichnung mit Testregeln, Empfehlung und App-Speicherung abbilden'
] as const;

export function getHttpsCenterTotals(): {
  legacyStates: number;
  endpoints: number;
  trustedApps: number;
  trustedDomains: number;
  sslFailures: number;
  recordings: number;
} {
  return {
    legacyStates: httpsCenterLegacyStates.length,
    endpoints: httpsCenterEndpoints.length,
    trustedApps: trustedApps.length,
    trustedDomains: trustedDomains.length,
    sslFailures: sslFailureSuggestions.domainsIps.length,
    recordings: manualRecording.recordedConnections.length
  };
}
