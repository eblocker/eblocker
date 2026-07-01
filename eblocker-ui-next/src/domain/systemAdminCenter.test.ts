import { describe, expect, it } from 'vitest';
import {
  adminPasswordState,
  backupFlows,
  diagnosticReport,
  eventRows,
  getSystemAdminCenterTotals,
  licenseInfo,
  localeSettings,
  openSourceLicenseGroups,
  schedulerStats,
  systemAdminCapabilities,
  systemAdminCenterEndpoints,
  systemAdminLegacyStates,
  systemStatusCards,
  taskRows,
  updateStatus
} from './systemAdminCenter';

describe('system admin center legacy parity', () => {
  it('covers every legacy System/Updates/Admin state', () => {
    expect(systemAdminLegacyStates).toEqual([
      'default',
      'license',
      'update',
      'about',
      'legal',
      'system',
      'adminpassword',
      'diagnostics',
      'events',
      'backup',
      'tasks',
      'timeandlanguage',
      'systempending',
      'logout',
      'open-source-licenses',
      'open-source-licenses-java',
      'open-source-licenses-ccpp',
      'open-source-licenses-javascript',
      'open-source-licenses-ruby',
      'open-source-licenses-debian'
    ]);
  });

  it('maps registration, update, diagnostics, events, tasks, backup, locale and password endpoints', () => {
    expect(systemAdminCenterEndpoints).toHaveLength(31);
    expect(systemAdminCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/system/'))).toBe(true);
    expect(systemAdminCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/api/adminconsole/registration',
      '/api/adminconsole/updates/status',
      '/api/adminconsole/updates/automaticUpdatesConfig',
      '/api/adminconsole/diagnostics/report',
      '/api/adminconsole/events/{mode}',
      '/api/adminconsole/tasks/viewConfig',
      '/api/configbackup/export',
      '/api/adminconsole/language',
      '/api/adminconsole/timezone/continent/countries',
      '/api/adminconsole/authentication/executeReset'
    ]));
  });

  it('keeps license and update state visible', () => {
    expect(licenseInfo.registrationState).toBe('OK');
    expect(licenseInfo.productFeatures).toEqual(expect.arrayContaining(['FAM', 'PRO']));
    expect(licenseInfo.licenseLifetime).toBe(true);
    expect(updateStatus.projectVersion).toBe('4.0.3');
    expect(updateStatus.automaticUpdatesActivated).toBe(true);
    expect(updateStatus.updateablePackages).toContain('eblocker-ui-next');
    expect(updateStatus.progress).toBeGreaterThan(0);
  });

  it('keeps diagnostics, events, backup and tasks represented', () => {
    expect(diagnosticReport.status).toBe('READY');
    expect(diagnosticReport.downloadPath).toBe('/api/adminconsole/diagnostics/download');
    expect(eventRows).toHaveLength(4);
    expect(eventRows.some((event) => event.severity === 'ERROR')).toBe(true);
    expect(backupFlows.map((flow) => flow.key)).toEqual(['export', 'download', 'upload', 'verify', 'import']);
    expect(taskRows.some((task) => task.status === 'RUNNING')).toBe(true);
    expect(schedulerStats.some((scheduler) => scheduler.queueLength > 0)).toBe(true);
  });

  it('keeps admin password, locale/timezone and open source licenses visible', () => {
    expect(adminPasswordState.passwordRequired).toBe(true);
    expect(adminPasswordState.resetFlowSteps).toEqual(['initiateReset', 'executeReset', 'cancelReset']);
    expect(localeSettings.language).toBe('de_DE');
    expect(localeSettings.timezone).toBe('Europe/Berlin');
    expect(localeSettings.regions).toEqual(expect.arrayContaining(['Europe', 'America', 'Asia']));
    expect(openSourceLicenseGroups.map((group) => group.route)).toEqual(expect.arrayContaining([
      'open-source-licenses-java',
      'open-source-licenses-ccpp',
      'open-source-licenses-javascript',
      'open-source-licenses-ruby',
      'open-source-licenses-debian'
    ]));
  });

  it('summarizes center totals and migrated capabilities', () => {
    expect(getSystemAdminCenterTotals()).toEqual({
      legacyStates: 20,
      endpoints: 31,
      statusCards: 9,
      events: 4,
      tasks: 5,
      licenseGroups: 5
    });
    expect(systemStatusCards.map((card) => card.key)).toEqual([
      'license',
      'updates',
      'diagnostics',
      'events',
      'backup',
      'tasks',
      'time-language',
      'admin-password',
      'open-source'
    ]);
    expect(systemAdminCapabilities).toEqual(expect.arrayContaining([
      'Lizenzstatus, Produktfeatures und Aktivierung sichtbar machen',
      'Update-Status, automatische Updates, Check/Recovery und Fortschritt zusammenführen',
      'Diagnosebericht, Events, Backup/Restore und Task-Monitoring als moderne Admin-Flows abbilden',
      'Sprache, Zeitzone, Admin-Passwort und Passwort-Reset ohne versteckte Dialogabhängigkeit zeigen',
      'Open-Source-Lizenzgruppen und rechtliche Systemseiten im neuen UI verankern'
    ]));
  });
});
