export type SystemEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
export type EventSeverity = 'INFO' | 'WARNING' | 'ERROR';
export type TaskStatus = 'OK' | 'RUNNING' | 'ERROR';
export type DiagnosticStatus = 'IDLE' | 'GENERATING' | 'READY' | 'ERROR';

export interface SystemEndpoint { readonly method: SystemEndpointMethod; readonly legacy: string; readonly modern: string; readonly purpose: string; }
export interface SystemStatusCard { readonly key: string; readonly title: string; readonly value: string; readonly detail: string; readonly tone: 'green' | 'amber' | 'blue' | 'violet' | 'red'; }
export interface LicenseInfo { readonly deviceId: string; readonly deviceName: string; readonly registrationState: 'OK' | 'NEW' | 'REVOKED' | 'EXPIRED'; readonly licenseType: 'FAM' | 'PRO' | 'BAS' | 'WOL'; readonly productFeatures: readonly string[]; readonly registeredBy: string; readonly licenseLifetime: boolean; readonly licenseAboutToExpire: boolean; readonly validFrom: string; readonly validUntil: string; }
export interface UpdateStatus { readonly projectVersion: string; readonly listsPacketVersion: string; readonly displayFilterVersion: string; readonly automaticUpdatesActivated: boolean; readonly automaticUpdatesAllowed: boolean; readonly updatesAvailable: boolean; readonly checking: boolean; readonly updating: boolean; readonly recovering: boolean; readonly lastAutomaticUpdate: string; readonly nextAutomaticUpdate: string; readonly updateablePackages: readonly string[]; readonly updateProgress: readonly string[]; readonly progress: number; readonly lastUpdateAttemptFailed: boolean; }
export interface DiagnosticReport { readonly status: DiagnosticStatus; readonly startedAt: string; readonly downloadPath: string; readonly size: string; }
export interface EventRow { readonly id: string; readonly timestamp: string; readonly severity: EventSeverity; readonly message: string; readonly category: string; }
export interface BackupFlow { readonly key: string; readonly endpoint: string; readonly requiresPassword: boolean; readonly state: 'ready' | 'pending' | 'failed'; }
export interface TaskRow { readonly name: string; readonly executor: string; readonly status: TaskStatus; readonly type: 'ONCE' | 'SCHEDULED'; readonly executions: number; readonly running: number; readonly lastStart: string; readonly lastStop: string | null; readonly avgRuntimeMs: number | null; }
export interface SchedulerStat { readonly name: string; readonly activeCount: number; readonly completedTaskCount: number; readonly corePoolSize: number; readonly queueLength: number; readonly taskCount: number; }
export interface LocaleSettings { readonly language: string; readonly country: string; readonly timezone: string; readonly regions: readonly string[]; readonly cities: readonly string[]; }
export interface AdminPasswordState { readonly passwordRequired: boolean; readonly defaultParentEnsured: boolean; readonly currentDeviceAssigned: boolean; readonly resetFlowSteps: readonly string[]; }
export interface OpenSourceLicenseGroup { readonly key: string; readonly label: string; readonly route: string; readonly packageCount: number; }

export const systemAdminLegacyStates = [
  'default', 'license', 'update', 'about', 'legal', 'system', 'adminpassword', 'diagnostics', 'events', 'backup', 'tasks', 'timeandlanguage', 'systempending', 'logout', 'open-source-licenses', 'open-source-licenses-java', 'open-source-licenses-ccpp', 'open-source-licenses-javascript', 'open-source-licenses-ruby', 'open-source-licenses-debian'
] as const;

export const systemAdminCenterEndpoints: readonly SystemEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/registration', modern: '/api/v1/system/registration', purpose: 'Registrierung/Lizenz laden' },
  { method: 'POST', legacy: '/api/adminconsole/registration', modern: '/api/v1/system/registration', purpose: 'Lizenz registrieren/aktualisieren' },
  { method: 'DELETE', legacy: '/api/adminconsole/registration', modern: '/api/v1/system/registration', purpose: 'Registrierung zurücksetzen' },
  { method: 'GET', legacy: '/api/adminconsole/updates/status', modern: '/api/v1/system/updates/status', purpose: 'Update-Status laden' },
  { method: 'POST', legacy: '/api/adminconsole/updates/status', modern: '/api/v1/system/updates/status', purpose: 'Update-Status setzen' },
  { method: 'GET', legacy: '/api/adminconsole/updates/autoupdate', modern: '/api/v1/system/updates/automatic', purpose: 'Auto-Update-Info laden' },
  { method: 'POST', legacy: '/api/adminconsole/updates/automaticUpdatesStatus', modern: '/api/v1/system/updates/automatic/status', purpose: 'Auto-Updates aktivieren' },
  { method: 'POST', legacy: '/api/adminconsole/updates/automaticUpdatesConfig', modern: '/api/v1/system/updates/automatic/config', purpose: 'Auto-Update-Zeitfenster speichern' },
  { method: 'GET', legacy: '/api/adminconsole/updates/check', modern: '/api/v1/system/updates/check', purpose: 'Nach Updates suchen' },
  { method: 'POST', legacy: '/api/adminconsole/diagnostics/report', modern: '/api/v1/system/diagnostics/report', purpose: 'Diagnosebericht erzeugen' },
  { method: 'GET', legacy: '/api/adminconsole/diagnostics/report', modern: '/api/v1/system/diagnostics/report', purpose: 'Diagnoseberichtstatus laden' },
  { method: 'GET', legacy: '/api/adminconsole/diagnostics/download', modern: '/api/v1/system/diagnostics/download', purpose: 'Diagnosebericht herunterladen' },
  { method: 'GET', legacy: '/api/adminconsole/events', modern: '/api/v1/system/events', purpose: 'Events laden' },
  { method: 'DELETE', legacy: '/api/adminconsole/events/{mode}', modern: '/api/v1/system/events/{mode}', purpose: 'Events löschen' },
  { method: 'GET', legacy: '/api/adminconsole/tasks/log', modern: '/api/v1/system/tasks/log', purpose: 'Task-Log laden' },
  { method: 'GET', legacy: '/api/adminconsole/tasks/viewConfig', modern: '/api/v1/system/tasks/view-config', purpose: 'Task-Tabellenkonfig laden' },
  { method: 'PUT', legacy: '/api/adminconsole/tasks/viewConfig', modern: '/api/v1/system/tasks/view-config', purpose: 'Task-Tabellenkonfig speichern' },
  { method: 'GET', legacy: '/api/adminconsole/tasks/stats', modern: '/api/v1/system/tasks/stats', purpose: 'Scheduler-Stats laden' },
  { method: 'POST', legacy: '/api/configbackup/export', modern: '/api/v1/system/backup/export', purpose: 'Backup exportieren' },
  { method: 'GET', legacy: '/api/configbackup/download/{fileReference}', modern: '/api/v1/system/backup/download/{fileReference}', purpose: 'Backup herunterladen' },
  { method: 'PUT', legacy: '/api/configbackup/upload', modern: '/api/v1/system/backup/upload', purpose: 'Backup hochladen' },
  { method: 'POST', legacy: '/api/configbackup/verify', modern: '/api/v1/system/backup/verify', purpose: 'Backup prüfen' },
  { method: 'POST', legacy: '/api/configbackup/import', modern: '/api/v1/system/backup/import', purpose: 'Backup importieren' },
  { method: 'POST', legacy: '/api/adminconsole/language', modern: '/api/v1/system/locale/language', purpose: 'Sprache setzen' },
  { method: 'GET', legacy: '/api/adminconsole/timezone/continents', modern: '/api/v1/system/locale/timezones/continents', purpose: 'Zeitzonenregionen laden' },
  { method: 'PUT', legacy: '/api/adminconsole/timezone/continent/countries', modern: '/api/v1/system/locale/timezones/cities', purpose: 'Städte pro Region laden' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/enable', modern: '/api/v1/system/admin-password/enable', purpose: 'Admin-Passwort setzen' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/disable', modern: '/api/v1/system/admin-password/disable', purpose: 'Admin-Passwort deaktivieren' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/initiateReset', modern: '/api/v1/system/admin-password/initiate-reset', purpose: 'Passwort-Reset starten' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/executeReset', modern: '/api/v1/system/admin-password/execute-reset', purpose: 'Passwort-Reset ausführen' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/cancelReset', modern: '/api/v1/system/admin-password/cancel-reset', purpose: 'Passwort-Reset abbrechen' }
];

export const licenseInfo: LicenseInfo = { deviceId: 'EBL-001', deviceName: 'eBlocker Bookworm', registrationState: 'OK', licenseType: 'FAM', productFeatures: ['FAM', 'PRO', 'WOL'], registeredBy: 'Shedowe', licenseLifetime: true, licenseAboutToExpire: false, validFrom: '2026-01-01', validUntil: 'Lifetime' };
export const updateStatus: UpdateStatus = { projectVersion: '4.0.3', listsPacketVersion: '20260701212511', displayFilterVersion: '2026-07-01-21-25-11', automaticUpdatesActivated: true, automaticUpdatesAllowed: true, updatesAvailable: true, checking: false, updating: true, recovering: false, lastAutomaticUpdate: '2026-07-01 04:12', nextAutomaticUpdate: '2026-07-02 04:12', updateablePackages: ['eblocker-ui-next', 'eblocker-icap'], updateProgress: ['download', 'unpack', 'setup'], progress: 37, lastUpdateAttemptFailed: false };
export const diagnosticReport: DiagnosticReport = { status: 'READY', startedAt: '2026-07-01 23:20', downloadPath: '/api/adminconsole/diagnostics/download', size: '12 MB' };
export const eventRows: readonly EventRow[] = [
  { id: 'e1', timestamp: '23:18', severity: 'INFO', message: 'Updateprüfung gestartet', category: 'updates' },
  { id: 'e2', timestamp: '23:19', severity: 'WARNING', message: 'Backup wartet auf Download', category: 'backup' },
  { id: 'e3', timestamp: '23:20', severity: 'ERROR', message: 'DNS-Server kurzzeitig nicht erreichbar', category: 'network' },
  { id: 'e4', timestamp: '23:21', severity: 'INFO', message: 'Diagnosebericht erstellt', category: 'diagnostics' }
];
export const backupFlows: readonly BackupFlow[] = [
  { key: 'export', endpoint: '/api/configbackup/export', requiresPassword: true, state: 'ready' },
  { key: 'download', endpoint: '/api/configbackup/download/{fileReference}', requiresPassword: false, state: 'ready' },
  { key: 'upload', endpoint: '/api/configbackup/upload', requiresPassword: false, state: 'pending' },
  { key: 'verify', endpoint: '/api/configbackup/verify', requiresPassword: true, state: 'ready' },
  { key: 'import', endpoint: '/api/configbackup/import', requiresPassword: true, state: 'ready' }
];
export const taskRows: readonly TaskRow[] = [
  { name: 'UpdateCheck', executor: 'system', status: 'RUNNING', type: 'SCHEDULED', executions: 184, running: 1, lastStart: '23:18', lastStop: null, avgRuntimeMs: 1800 },
  { name: 'FilterListRefresh', executor: 'filter', status: 'OK', type: 'SCHEDULED', executions: 92, running: 0, lastStart: '22:00', lastStop: '22:01', avgRuntimeMs: 61000 },
  { name: 'DiagnosticsReport', executor: 'system', status: 'OK', type: 'ONCE', executions: 1, running: 0, lastStart: '23:20', lastStop: '23:21', avgRuntimeMs: 42000 },
  { name: 'BackupExport', executor: 'backup', status: 'OK', type: 'ONCE', executions: 3, running: 0, lastStart: '21:00', lastStop: '21:01', avgRuntimeMs: 35000 },
  { name: 'EventCleanup', executor: 'system', status: 'ERROR', type: 'SCHEDULED', executions: 10, running: 0, lastStart: '20:00', lastStop: '20:00', avgRuntimeMs: null }
];
export const schedulerStats: readonly SchedulerStat[] = [
  { name: 'system', activeCount: 1, completedTaskCount: 1220, corePoolSize: 2, queueLength: 1, taskCount: 1222 },
  { name: 'filter', activeCount: 0, completedTaskCount: 560, corePoolSize: 1, queueLength: 0, taskCount: 560 }
];
export const localeSettings: LocaleSettings = { language: 'de_DE', country: 'DE', timezone: 'Europe/Berlin', regions: ['Europe', 'America', 'Asia'], cities: ['Berlin', 'Paris', 'London'] };
export const adminPasswordState: AdminPasswordState = { passwordRequired: true, defaultParentEnsured: true, currentDeviceAssigned: true, resetFlowSteps: ['initiateReset', 'executeReset', 'cancelReset'] };
export const openSourceLicenseGroups: readonly OpenSourceLicenseGroup[] = [
  { key: 'java', label: 'Java', route: 'open-source-licenses-java', packageCount: 86 },
  { key: 'ccpp', label: 'C/C++', route: 'open-source-licenses-ccpp', packageCount: 41 },
  { key: 'javascript', label: 'JavaScript', route: 'open-source-licenses-javascript', packageCount: 124 },
  { key: 'ruby', label: 'Ruby', route: 'open-source-licenses-ruby', packageCount: 12 },
  { key: 'debian', label: 'Debian', route: 'open-source-licenses-debian', packageCount: 320 }
];
export const systemStatusCards: readonly SystemStatusCard[] = [
  { key: 'license', title: 'Lizenz', value: licenseInfo.registrationState, detail: `${licenseInfo.licenseType} · ${licenseInfo.validUntil}`, tone: 'green' },
  { key: 'updates', title: 'Updates', value: `${updateStatus.progress}%`, detail: `${updateStatus.updateablePackages.length} Pakete`, tone: 'amber' },
  { key: 'diagnostics', title: 'Diagnose', value: diagnosticReport.status, detail: diagnosticReport.size, tone: 'blue' },
  { key: 'events', title: 'Events', value: `${eventRows.length}`, detail: '1 Fehler offen', tone: 'red' },
  { key: 'backup', title: 'Backup', value: `${backupFlows.length} Flows`, detail: 'Export/Import bereit', tone: 'violet' },
  { key: 'tasks', title: 'Tasks', value: `${taskRows.length}`, detail: '1 läuft · 1 Fehler', tone: 'amber' },
  { key: 'time-language', title: 'Sprache/Zeit', value: localeSettings.language, detail: localeSettings.timezone, tone: 'blue' },
  { key: 'admin-password', title: 'Admin-Passwort', value: adminPasswordState.passwordRequired ? 'aktiv' : 'aus', detail: 'Reset-Flow gemappt', tone: 'green' },
  { key: 'open-source', title: 'Open Source', value: `${openSourceLicenseGroups.length}`, detail: 'Lizenzgruppen', tone: 'violet' }
];
export const systemAdminCapabilities = [
  'Lizenzstatus, Produktfeatures und Aktivierung sichtbar machen',
  'Update-Status, automatische Updates, Check/Recovery und Fortschritt zusammenführen',
  'Diagnosebericht, Events, Backup/Restore und Task-Monitoring als moderne Admin-Flows abbilden',
  'Sprache, Zeitzone, Admin-Passwort und Passwort-Reset ohne versteckte Dialogabhängigkeit zeigen',
  'Open-Source-Lizenzgruppen und rechtliche Systemseiten im neuen UI verankern'
] as const;

export function getSystemAdminCenterTotals(): { legacyStates: number; endpoints: number; statusCards: number; events: number; tasks: number; licenseGroups: number; } {
  return { legacyStates: systemAdminLegacyStates.length, endpoints: systemAdminCenterEndpoints.length, statusCards: systemStatusCards.length, events: eventRows.length, tasks: taskRows.length, licenseGroups: openSourceLicenseGroups.length };
}
