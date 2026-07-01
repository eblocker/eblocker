import { describe, expect, it } from 'vitest';
import {
  accessDeniedReasons,
  blockPageActions,
  blockPageCapabilities,
  blockPageEndpointMap,
  blockPageLegacyStates,
  blockPageScenarios,
  blockRouteDecisions,
  getBlockPageCenterTotals,
  redirectDecisionRows,
  squidMitigationRows
} from './blockPagesCenter';

describe('block and redirect pages legacy parity', () => {
  it('covers every legacy dashboard block, redirect, console and logout state', () => {
    expect(blockPageLegacyStates).toEqual([
      'redirect',
      'redirectOptions',
      'blockOptions',
      'blocker',
      'blockerAccessDenied',
      'blockerAdsTrackers',
      'blockerMalware',
      'blockerWhitelisted',
      'squidError',
      'blockerSslWhitelisted',
      'console',
      'logoutAdmin'
    ]);
  });

  it('preserves legacy router decisions for squid/eBlocker error types', () => {
    expect(blockRouteDecisions).toEqual(expect.arrayContaining([
      { type: 'ERR_SECURE_CONNECT_FAIL', targetState: 'blockerSslWhitelisted', pageKind: 'ssl-whitelist' },
      { type: 'EBLKR_ACCESS_DENIED', targetState: 'blockerAccessDenied', pageKind: 'access-denied' },
      { type: 'ERR_ACCESS_DENIED', targetState: 'blockerAccessDenied', pageKind: 'access-denied' },
      { type: 'EBLKR_BLOCKED_MALWARE', targetState: 'blockerMalware', pageKind: 'malware' },
      { type: 'EBLKR_BLOCKED_ADS_TRACKERS', targetState: 'blockerAdsTrackers', pageKind: 'ads-trackers' },
      { type: 'EBLKR_BLOCKED_WHITELISTED', targetState: 'blockerWhitelisted', pageKind: 'whitelisted' },
      { type: '*', targetState: 'squidError', pageKind: 'squid-error' }
    ]));
  });

  it('maps legacy redirect, blocker, parental-control, SSL, Tor, VPN and logout endpoints', () => {
    expect(blockPageEndpointMap).toHaveLength(20);
    expect(blockPageEndpointMap.every((endpoint) => endpoint.modern.startsWith('/api/v1/block-pages/'))).toBe(true);
    expect(blockPageEndpointMap.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/controlbar/console/ip',
      '/redirect/{decision}/{txid}',
      '/api/dashboard/filterlists',
      '/api/dashboard/users',
      '/api/dashboard/users/operatinguser',
      '/api/dashboard/userprofiles',
      '/api/dashboard/searchEngineConfig',
      '/api/parentalcontrol/usage',
      '/api/dashboard/customdomainfilter/{userId}',
      '/api/device/pause/{deviceId}',
      '/api/dashboard/ssl/whitelist',
      '/api/dashboard/tor/config',
      '/api/dashboard/vpn/profiles/status/{deviceId}',
      '/api/dashboard/vpn/profiles/{profileId}/status-this',
      '/api/token/DASHBOARD'
    ]));
  });

  it('keeps user-facing page scenarios and actions explicit', () => {
    expect(blockPageScenarios).toHaveLength(9);
    expect(blockPageScenarios.map((scenario) => scenario.key)).toEqual([
      'redirect-options',
      'block-options',
      'access-denied',
      'ads-trackers',
      'malware',
      'whitelisted',
      'ssl-whitelist',
      'squid-error',
      'logout-admin'
    ]);
    expect(blockPageActions).toEqual(expect.arrayContaining([
      'PASS decision',
      'REDIRECT decision',
      'remember redirect decision',
      'back to browser history',
      'retry target URL',
      'open parental-control console',
      'change/unlock operating user by PIN',
      'toggle remaining online time',
      'pause protection for 300 seconds',
      'remove custom blocked domain',
      'add SSL whitelist domain and continue',
      'disable Tor and retry',
      'disable VPN and retry',
      'logout admin token and return to dashboard'
    ]));
  });

  it('keeps access-denied reasons, redirect decisions and squid mitigations visible', () => {
    expect(accessDeniedReasons.map((reason) => reason.key)).toEqual([
      'domain-blacklist',
      'domain-not-whitelisted',
      'time-frame',
      'max-usage',
      'usage-time-disabled',
      'internet-blocked',
      'generic-access-denied'
    ]);
    expect(redirectDecisionRows.map((row) => row.decision)).toEqual(['PASS', 'REDIRECT']);
    expect(squidMitigationRows.map((row) => row.key)).toEqual(['retry', 'disable-tor', 'disable-vpn']);
  });

  it('summarizes migrated states, endpoints and capabilities', () => {
    expect(getBlockPageCenterTotals()).toEqual({
      legacyStates: 12,
      endpoints: 20,
      scenarios: 9,
      routeDecisions: 7,
      actions: 14,
      accessReasons: 7
    });
    expect(blockPageCapabilities).toEqual(expect.arrayContaining([
      'Redirect-/Block-Entscheidungen mit PASS/REDIRECT und Merken-Option erhalten',
      'Access-Denied-Gründe aus Profil, Liste, Zeitfenster, Max-Usage und Gerätesperre sichtbar machen',
      'Ads/Tracker/Custom-Domain-Blocker mit Pause, Retry und Domain entfernen abbilden',
      'Malware-, Whitelist-, SSL-Handshake- und Squid-Fehlerseiten als moderne Nutzerseiten erhalten',
      'Console-Redirect und Admin-Logout aus der Dashboard-App nicht vergessen'
    ]));
  });
});
