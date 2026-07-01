import { describe, expect, it } from 'vitest';
import {
  advancedPrivacySettings,
  analysisRecorder,
  filterListRows,
  getProtectionCenterTotals,
  protectionCenterCapabilities,
  protectionCenterEndpoints,
  protectionCenterLegacyStates,
  protectionModulesModern,
  torExitPolicy
} from './protectionCenter';

describe('protection center legacy parity', () => {
  it('covers every legacy protection/filter/privacy state', () => {
    expect(protectionCenterLegacyStates).toEqual([
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
    ]);
  });

  it('maps filter, privacy, tor, recorder and doctor endpoints to /api/v1 targets', () => {
    expect(protectionCenterEndpoints).toHaveLength(18);
    expect(protectionCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/'))).toBe(true);
    expect(protectionCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/api/adminconsole/filterlists',
      '/api/adminconsole/filterlists/meta',
      '/api/adminconsole/filterlists/{id}/domains',
      '/api/adminconsole/useragent/cloaked',
      '/api/adminconsole/dnt',
      '/api/adminconsole/webrtc',
      '/api/adminconsole/referrer',
      '/api/adminconsole/compressionmode',
      '/api/adminconsole/captiveportal',
      '/api/adminconsole/tor/countries',
      '/api/adminconsole/tor/countries/selected',
      '/api/adminconsole/recorder',
      '/api/adminconsole/recorder/results',
      '/api/adminconsole/doctor/diagnosis'
    ]));
  });

  it('keeps domain/pattern blocker modules and editable filter lists visible', () => {
    expect(protectionModulesModern.map((module) => module.id)).toEqual([
      'domain-ads',
      'domain-trackers',
      'domain-malware',
      'pattern-ads',
      'pattern-trackers',
      'pattern-malware',
      'pattern-content'
    ]);
    expect(protectionModulesModern.filter((module) => module.needsSsl)).toHaveLength(2);
    expect(filterListRows.some((row) => !row.builtin && row.editable)).toBe(true);
  });

  it('keeps advanced privacy controls, Tor exit-node selection and analysis recorder behavior', () => {
    expect(advancedPrivacySettings.map((setting) => setting.id)).toEqual([
      'captive-portal',
      'web-compression',
      'web-rtc',
      'referrer',
      'do-not-track',
      'user-agent-cloaking'
    ]);
    expect(torExitPolicy.mode).toBe('manual');
    expect(torExitPolicy.selectedCountries).toEqual(['DE', 'NL', 'SE']);
    expect(analysisRecorder.whatIfMode).toBe(true);
    expect(analysisRecorder.tableColumns).toEqual(['id', 'timestamp', 'domain', 'method', 'url', 'decision', 'decider']);
  });

  it('summarizes the center for dashboard cards and documents migrated capabilities', () => {
    expect(getProtectionCenterTotals()).toEqual({
      legacyStates: 11,
      endpoints: 18,
      modules: 7,
      filterLists: 4,
      privacySettings: 6,
      torCountries: 3
    });
    expect(protectionCenterCapabilities).toEqual(expect.arrayContaining([
      'Domain- und Pattern-Filter getrennt nach Werbung, Trackern, Malware und Content anzeigen',
      'Filterlisten mit Built-in-/Custom-Status, Domainanzahl, Updatezustand und CRUD-Mapping darstellen',
      'Advanced Privacy: Captive Portal, Kompression, WebRTC, Referrer und DNT steuern',
      'User-Agent-Cloaking pro Gerät/User als Privacy-Baustein abbilden',
      'Tor-Länder automatisch/manuell wählen und neue Identität anfordern',
      'Filteranalyse mit Recorder, What-if-Modus, CSV-Export und Details abbilden',
      'Doctor-Diagnose nach Severity und Erfahrungslevel sichtbar machen'
    ]));
  });
});
