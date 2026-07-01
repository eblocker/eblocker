import { describe, expect, it } from 'vitest';
import {
  getHttpsCenterTotals,
  httpsCapabilities,
  httpsCenterEndpoints,
  httpsCenterLegacyStates,
  httpsStatus,
  manualRecording,
  rootCertificate,
  sslFailureSuggestions,
  trustedApps,
  trustedDomains
} from './httpsCenter';

describe('HTTPS center legacy parity', () => {
  it('covers every legacy HTTPS/certificate state, including dashboard HTTPS entry', () => {
    expect(httpsCenterLegacyStates).toEqual([
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
    ]);
  });

  it('maps SslService, TrustedApps, TrustedDomains and ManualRecording endpoints to /api/v1 targets', () => {
    expect(httpsCenterEndpoints).toHaveLength(25);
    expect(httpsCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/'))).toBe(true);
    expect(httpsCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/api/adminconsole/ssl/status',
      '/api/adminconsole/ssl/rootca',
      '/api/adminconsole/ssl/certs/status',
      '/api/adminconsole/ssl/errors',
      '/api/adminconsole/ssl/errors/recording',
      '/api/adminconsole/trustedapps/all',
      '/api/adminconsole/trustedapps/id/{id}',
      '/api/adminconsole/trusteddomains/onlyenabled',
      '/api/adminconsole/recording/toggle',
      '/api/adminconsole/recording/result'
    ]));
  });

  it('keeps HTTPS status, ATA, certificate renewal and CA metadata represented', () => {
    expect(httpsStatus.enabled).toBe(true);
    expect(httpsStatus.ataEnabled).toBe(true);
    expect(httpsStatus.certificatesReady).toBe(true);
    expect(rootCertificate.commonName).toContain('eBlocker');
    expect(rootCertificate.renewalReady).toBe(true);
  });

  it('keeps trusted apps/domains, SSL failures and manual recording visible', () => {
    expect(trustedApps.some((app) => app.builtin && app.enabled)).toBe(true);
    expect(trustedApps.some((app) => !app.builtin && app.modified)).toBe(true);
    expect(trustedDomains.some((domain) => domain.deletable)).toBe(true);
    expect(sslFailureSuggestions.domainsIps).toHaveLength(3);
    expect(manualRecording.recordedConnections).toHaveLength(4);
    expect(manualRecording.tableColumns).toEqual(['domain', 'ip', 'protocol', 'currentRule', 'recommendedRule', 'tempRule']);
  });

  it('summarizes the center and documents migrated capabilities', () => {
    expect(getHttpsCenterTotals()).toEqual({
      legacyStates: 10,
      endpoints: 25,
      trustedApps: 4,
      trustedDomains: 5,
      sslFailures: 3,
      recordings: 4
    });
    expect(httpsCapabilities).toEqual(expect.arrayContaining([
      'HTTPS/SSL-Inspection und Attack-Target-Analysis schalten',
      'Root-CA anzeigen, erneuern und Zertifikatsstatus prüfen',
      'SSL-Fehler auswerten, leeren und Recording aktivieren',
      'Trusted Apps anlegen, aktivieren, zurücksetzen und Domains/IPs verwalten',
      'Trusted Domains einzeln oder gesammelt löschen',
      'Manuelle Aufzeichnung mit Testregeln, Empfehlung und App-Speicherung abbilden'
    ]));
  });
});
