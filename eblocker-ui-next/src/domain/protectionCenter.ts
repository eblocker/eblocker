export type ProtectionEndpointMethod = 'GET' | 'PUT' | 'POST' | 'DELETE';
export type ProtectionModuleType = 'domain' | 'pattern';
export type ProtectionCategory = 'ads' | 'trackers' | 'malware' | 'content';
export type PrivacySettingState = 'enabled' | 'disabled' | 'conditional';

export interface ProtectionCenterEndpoint {
  readonly method: ProtectionEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface ProtectionModuleRow {
  readonly id: string;
  readonly label: string;
  readonly type: ProtectionModuleType;
  readonly category: ProtectionCategory;
  readonly usedByDevices: number;
  readonly blockedToday: number;
  readonly needsDns: boolean;
  readonly needsSsl: boolean;
  readonly licensed: boolean;
  readonly legacyTemplate: string;
}

export interface FilterListRow {
  readonly id: string;
  readonly name: string;
  readonly type: ProtectionModuleType;
  readonly builtin: boolean;
  readonly editable: boolean;
  readonly domains: number;
  readonly enabled: boolean;
  readonly lastUpdate: string;
}

export interface AdvancedPrivacySetting {
  readonly id: string;
  readonly label: string;
  readonly state: PrivacySettingState;
  readonly value: string;
  readonly warnsIfSslDisabled: boolean;
  readonly endpoint: string;
  readonly legacyHelp: string;
}

export interface TorExitPolicy {
  readonly mode: 'automatic' | 'manual';
  readonly selectedCountries: readonly string[];
  readonly availableCountries: number;
  readonly showWarnings: boolean;
}

export interface AnalysisRecorderState {
  readonly device: string;
  readonly active: boolean;
  readonly whatIfMode: boolean;
  readonly timeLimitSeconds: number;
  readonly sizeLimitBytes: number;
  readonly recordedTransactions: number;
  readonly tableColumns: readonly string[];
}

export interface DoctorProbeRow {
  readonly tag: string;
  readonly severity: 'GOOD' | 'HINT' | 'RECOMMENDATION_NOT_FOLLOWED' | 'FAILED_PROBE';
  readonly audience: 'EVERYONE' | 'NOVICE' | 'EXPERT';
  readonly message: string;
}

export const protectionCenterLegacyStates = [
  'anonymization',
  'anonymizationstate',
  'tor',
  'filter',
  'filterstate',
  'filteroverview',
  'filter-details',
  'advancedsettings',
  'filteranalysis',
  'analysisdetails',
  'doctor'
] as const;

export const protectionCenterEndpoints: readonly ProtectionCenterEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/filterlists', modern: '/api/v1/protection/filter-lists', purpose: 'Filterlisten laden' },
  { method: 'GET', legacy: '/api/adminconsole/filterlists/meta', modern: '/api/v1/protection/filter-lists/meta', purpose: 'Filterlisten-Metadaten laden' },
  { method: 'POST', legacy: '/api/adminconsole/filterlists?filterType={type}', modern: '/api/v1/protection/filter-lists?type={type}', purpose: 'Custom Filterliste anlegen' },
  { method: 'PUT', legacy: '/api/adminconsole/filterlists/{id}/update?filterType={type}', modern: '/api/v1/protection/filter-lists/{id}?type={type}', purpose: 'Filterliste aktualisieren' },
  { method: 'DELETE', legacy: '/api/adminconsole/filterlists/{id}', modern: '/api/v1/protection/filter-lists/{id}', purpose: 'Filterliste löschen' },
  { method: 'GET', legacy: '/api/adminconsole/filterlists/{id}/domains', modern: '/api/v1/protection/filter-lists/{id}/domains', purpose: 'Domains einer Filterliste laden' },
  { method: 'GET', legacy: '/api/adminconsole/useragent/cloaked', modern: '/api/v1/privacy/user-agent/cloaked', purpose: 'User-Agent-Cloaking pro Gerät/User laden' },
  { method: 'PUT', legacy: '/api/adminconsole/useragent/cloaked', modern: '/api/v1/privacy/user-agent/cloaked', purpose: 'User-Agent-Cloaking speichern' },
  { method: 'GET', legacy: '/api/adminconsole/dnt', modern: '/api/v1/privacy/do-not-track', purpose: 'Do-Not-Track laden' },
  { method: 'GET', legacy: '/api/adminconsole/webrtc', modern: '/api/v1/privacy/webrtc', purpose: 'WebRTC-Schutz laden' },
  { method: 'GET', legacy: '/api/adminconsole/referrer', modern: '/api/v1/privacy/referrer', purpose: 'Referrer-Schutz laden' },
  { method: 'GET', legacy: '/api/adminconsole/compressionmode', modern: '/api/v1/protection/compression-mode', purpose: 'Web-Kompression laden' },
  { method: 'GET', legacy: '/api/adminconsole/captiveportal', modern: '/api/v1/protection/captive-portal', purpose: 'Captive-Portal-Responder laden' },
  { method: 'GET', legacy: '/api/adminconsole/tor/countries', modern: '/api/v1/privacy/tor/countries', purpose: 'Tor-Länder laden' },
  { method: 'PUT', legacy: '/api/adminconsole/tor/countries/selected', modern: '/api/v1/privacy/tor/countries/selected', purpose: 'Tor Exit Nodes speichern' },
  { method: 'POST', legacy: '/api/adminconsole/recorder', modern: '/api/v1/protection/recorder', purpose: 'Filteranalyse-Recorder starten' },
  { method: 'GET', legacy: '/api/adminconsole/recorder/results', modern: '/api/v1/protection/recorder/results', purpose: 'Recorder-Ergebnisse laden' },
  { method: 'GET', legacy: '/api/adminconsole/doctor/diagnosis', modern: '/api/v1/protection/doctor/diagnosis', purpose: 'Doctor-Diagnose ausführen' }
];

export const protectionModulesModern: readonly ProtectionModuleRow[] = [
  { id: 'domain-ads', label: 'DNS-Werbeblocker', type: 'domain', category: 'ads', usedByDevices: 7, blockedToday: 3821, needsDns: true, needsSsl: false, licensed: true, legacyTemplate: 'help-filters-plug-and-play.template.html' },
  { id: 'domain-trackers', label: 'DNS-Trackerblocker', type: 'domain', category: 'trackers', usedByDevices: 7, blockedToday: 1240, needsDns: true, needsSsl: false, licensed: true, legacyTemplate: 'help-filters-plug-and-play.template.html' },
  { id: 'domain-malware', label: 'DNS-Malwareschutz', type: 'domain', category: 'malware', usedByDevices: 8, blockedToday: 13, needsDns: true, needsSsl: false, licensed: true, legacyTemplate: 'help-filters-malware.template.html' },
  { id: 'pattern-ads', label: 'Pattern-Werbeblocker', type: 'pattern', category: 'ads', usedByDevices: 5, blockedToday: 1144, needsDns: false, needsSsl: true, licensed: true, legacyTemplate: 'help-filters-pattern.template.html' },
  { id: 'pattern-trackers', label: 'Pattern-Trackerblocker', type: 'pattern', category: 'trackers', usedByDevices: 5, blockedToday: 991, needsDns: false, needsSsl: true, licensed: true, legacyTemplate: 'help-filters-pattern.template.html' },
  { id: 'pattern-malware', label: 'Pattern-Malwareschutz', type: 'pattern', category: 'malware', usedByDevices: 5, blockedToday: 2, needsDns: true, needsSsl: false, licensed: true, legacyTemplate: 'help-filters-malware.template.html' },
  { id: 'pattern-content', label: 'Content-Filter', type: 'pattern', category: 'content', usedByDevices: 3, blockedToday: 84, needsDns: true, needsSsl: false, licensed: true, legacyTemplate: 'help-filters-content.template.html' }
];

export const filterListRows: readonly FilterListRow[] = [
  { id: 'ads-core', name: 'eBlocker Werbung', type: 'domain', builtin: true, editable: false, domains: 98214, enabled: true, lastUpdate: 'heute 03:14' },
  { id: 'tracker-core', name: 'eBlocker Tracker', type: 'domain', builtin: true, editable: false, domains: 41292, enabled: true, lastUpdate: 'heute 03:14' },
  { id: 'malware-core', name: 'Malware Domains', type: 'domain', builtin: true, editable: false, domains: 188302, enabled: true, lastUpdate: 'heute 03:14' },
  { id: 'custom-family', name: 'Custom Familie', type: 'pattern', builtin: false, editable: true, domains: 42, enabled: true, lastUpdate: 'gestern 22:08' }
];

export const advancedPrivacySettings: readonly AdvancedPrivacySetting[] = [
  { id: 'captive-portal', label: 'Captive Portal Responder', state: 'enabled', value: 'aktiv', warnsIfSslDisabled: false, endpoint: '/api/v1/protection/captive-portal', legacyHelp: 'help-filters-captive-portal.template.html' },
  { id: 'web-compression', label: 'Web-Kompression', state: 'conditional', value: 'VPN-Clients', warnsIfSslDisabled: true, endpoint: '/api/v1/protection/compression-mode', legacyHelp: 'help-filters-web-compression.template.html' },
  { id: 'web-rtc', label: 'WebRTC-Schutz', state: 'enabled', value: 'blockiert lokale IP-Leaks', warnsIfSslDisabled: true, endpoint: '/api/v1/privacy/webrtc', legacyHelp: 'help-filters-web-rtc.template.html' },
  { id: 'referrer', label: 'Referrer entfernen', state: 'enabled', value: 'HTTP Referer Header aus', warnsIfSslDisabled: true, endpoint: '/api/v1/privacy/referrer', legacyHelp: 'help-filters-referrer.html' },
  { id: 'do-not-track', label: 'Do Not Track', state: 'enabled', value: 'DNT Header aktiv', warnsIfSslDisabled: true, endpoint: '/api/v1/privacy/do-not-track', legacyHelp: 'help-filters-do-not-track.html' },
  { id: 'user-agent-cloaking', label: 'User-Agent-Cloaking', state: 'conditional', value: 'pro Gerät/User', warnsIfSslDisabled: false, endpoint: '/api/v1/privacy/user-agent/cloaked', legacyHelp: 'CloakingService.js' }
];

export const torExitPolicy: TorExitPolicy = {
  mode: 'manual',
  selectedCountries: ['DE', 'NL', 'SE'],
  availableCountries: 74,
  showWarnings: true
};

export const analysisRecorder: AnalysisRecorderState = {
  device: 'MacBook Pro',
  active: false,
  whatIfMode: true,
  timeLimitSeconds: 300,
  sizeLimitBytes: 104857600,
  recordedTransactions: 26,
  tableColumns: ['id', 'timestamp', 'domain', 'method', 'url', 'decision', 'decider']
};

export const doctorProbeRows: readonly DoctorProbeRow[] = [
  { tag: 'dns-active', severity: 'GOOD', audience: 'EVERYONE', message: 'DNS-Schutz aktiv' },
  { tag: 'ssl-pattern-coverage', severity: 'RECOMMENDATION_NOT_FOLLOWED', audience: 'NOVICE', message: '2 Geräte ohne HTTPS-Inspektion' },
  { tag: 'tor-exit-selection', severity: 'HINT', audience: 'EXPERT', message: 'Manuelle Exit-Länder gesetzt' },
  { tag: 'malware-list-age', severity: 'GOOD', audience: 'EVERYONE', message: 'Malware-Liste aktuell' }
];

export const protectionCenterCapabilities = [
  'Domain- und Pattern-Filter getrennt nach Werbung, Trackern, Malware und Content anzeigen',
  'Filterlisten mit Built-in-/Custom-Status, Domainanzahl, Updatezustand und CRUD-Mapping darstellen',
  'Advanced Privacy: Captive Portal, Kompression, WebRTC, Referrer und DNT steuern',
  'User-Agent-Cloaking pro Gerät/User als Privacy-Baustein abbilden',
  'Tor-Länder automatisch/manuell wählen und neue Identität anfordern',
  'Filteranalyse mit Recorder, What-if-Modus, CSV-Export und Details abbilden',
  'Doctor-Diagnose nach Severity und Erfahrungslevel sichtbar machen'
] as const;

export function getProtectionCenterTotals(): {
  legacyStates: number;
  endpoints: number;
  modules: number;
  filterLists: number;
  privacySettings: number;
  torCountries: number;
} {
  return {
    legacyStates: protectionCenterLegacyStates.length,
    endpoints: protectionCenterEndpoints.length,
    modules: protectionModulesModern.length,
    filterLists: filterListRows.length,
    privacySettings: advancedPrivacySettings.length,
    torCountries: torExitPolicy.selectedCountries.length
  };
}

export function getBlockedProtectionTotal(): number {
  return protectionModulesModern.reduce((sum, module) => sum + module.blockedToday, 0);
}
