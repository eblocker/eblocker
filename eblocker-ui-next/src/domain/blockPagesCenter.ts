export type BlockEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
export type BlockPageKind = 'redirect' | 'block-options' | 'access-denied' | 'ads-trackers' | 'malware' | 'whitelisted' | 'ssl-whitelist' | 'squid-error' | 'console' | 'logout';
export type ScenarioSeverity = 'info' | 'warning' | 'danger';

export interface BlockEndpoint {
  readonly method: BlockEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface BlockRouteDecision {
  readonly type: string;
  readonly targetState: string;
  readonly pageKind: string;
}

export interface BlockPageScenario {
  readonly key: string;
  readonly legacyState: string;
  readonly title: string;
  readonly pageKind: BlockPageKind;
  readonly severity: ScenarioSeverity;
  readonly params: readonly string[];
  readonly preservedActions: readonly string[];
}

export interface AccessDeniedReason {
  readonly key: string;
  readonly legacyCondition: string;
  readonly visibleMessage: string;
}

export interface RedirectDecisionRow {
  readonly decision: 'PASS' | 'REDIRECT';
  readonly target: string;
  readonly rememberable: boolean;
}

export interface SquidMitigationRow {
  readonly key: string;
  readonly legacyService: string;
  readonly action: string;
  readonly delayMs: number;
}

export const blockPageLegacyStates = [
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
] as const;

export const blockRouteDecisions: readonly BlockRouteDecision[] = [
  { type: 'ERR_SECURE_CONNECT_FAIL', targetState: 'blockerSslWhitelisted', pageKind: 'ssl-whitelist' },
  { type: 'EBLKR_ACCESS_DENIED', targetState: 'blockerAccessDenied', pageKind: 'access-denied' },
  { type: 'ERR_ACCESS_DENIED', targetState: 'blockerAccessDenied', pageKind: 'access-denied' },
  { type: 'EBLKR_BLOCKED_MALWARE', targetState: 'blockerMalware', pageKind: 'malware' },
  { type: 'EBLKR_BLOCKED_ADS_TRACKERS', targetState: 'blockerAdsTrackers', pageKind: 'ads-trackers' },
  { type: 'EBLKR_BLOCKED_WHITELISTED', targetState: 'blockerWhitelisted', pageKind: 'whitelisted' },
  { type: '*', targetState: 'squidError', pageKind: 'squid-error' }
];

export const blockPageEndpointMap: readonly BlockEndpoint[] = [
  { method: 'GET', legacy: '/controlbar/console/ip', modern: '/api/v1/block-pages/console/url', purpose: 'Admin-Konsole/Console-Redirect vorbereiten' },
  { method: 'GET', legacy: '/redirect/{decision}/{txid}', modern: '/api/v1/block-pages/redirect/{decision}/{txid}', purpose: 'Redirect- oder PASS-Entscheidung ausführen' },
  { method: 'PUT', legacy: '/redirect/{decision}/{txid}', modern: '/api/v1/block-pages/redirect/{decision}/{txid}', purpose: 'Redirect-Entscheidung dauerhaft merken' },
  { method: 'GET', legacy: '/api/dashboard/filterlists', modern: '/api/v1/block-pages/filter-lists', purpose: 'Blockierte Liste für Access-Denied auflösen' },
  { method: 'GET', legacy: '/api/dashboard/users', modern: '/api/v1/block-pages/users', purpose: 'Aktuellen/wechselbaren Nutzer laden' },
  { method: 'PUT', legacy: '/api/dashboard/users/operatinguser', modern: '/api/v1/block-pages/users/operating-user', purpose: 'Betriebsnutzer per PIN wechseln' },
  { method: 'GET', legacy: '/api/dashboard/userprofiles', modern: '/api/v1/block-pages/user-profiles', purpose: 'Profil und Sperrgründe laden' },
  { method: 'GET', legacy: '/api/dashboard/searchEngineConfig', modern: '/api/v1/block-pages/search-engine-config', purpose: 'Suchmaschinen-Hilfe für Domain-Sperren laden' },
  { method: 'GET', legacy: '/api/parentalcontrol/usage', modern: '/api/v1/block-pages/online-time/usage', purpose: 'Restzeit im Access-Denied-Flow laden' },
  { method: 'POST', legacy: '/api/parentalcontrol/usage', modern: '/api/v1/block-pages/online-time/usage', purpose: 'Restzeit temporär aktivieren' },
  { method: 'DELETE', legacy: '/api/parentalcontrol/usage', modern: '/api/v1/block-pages/online-time/usage', purpose: 'Restzeit wieder deaktivieren' },
  { method: 'GET', legacy: '/api/dashboard/customdomainfilter/{userId}', modern: '/api/v1/block-pages/custom-domain-filter/{userId}', purpose: 'Custom-Domain-Filter laden' },
  { method: 'PUT', legacy: '/api/dashboard/customdomainfilter/{userId}', modern: '/api/v1/block-pages/custom-domain-filter/{userId}', purpose: 'Custom-Domain-Filter speichern' },
  { method: 'PUT', legacy: '/api/device/pause/{deviceId}', modern: '/api/v1/block-pages/devices/{deviceId}/pause', purpose: 'Schutz 300 Sekunden pausieren und weiterleiten' },
  { method: 'POST', legacy: '/api/dashboard/ssl/whitelist', modern: '/api/v1/block-pages/ssl/whitelist', purpose: 'SSL-Domain freigeben und fortsetzen' },
  { method: 'GET', legacy: '/api/dashboard/tor/config', modern: '/api/v1/block-pages/tor/config', purpose: 'Tor-Status für Squid-Fehler laden' },
  { method: 'PUT', legacy: '/api/dashboard/tor/config', modern: '/api/v1/block-pages/tor/config', purpose: 'Tor deaktivieren und erneut versuchen' },
  { method: 'GET', legacy: '/api/dashboard/vpn/profiles/status/{deviceId}', modern: '/api/v1/block-pages/vpn/status/{deviceId}', purpose: 'VPN-Status für Squid-Fehler laden' },
  { method: 'PUT', legacy: '/api/dashboard/vpn/profiles/{profileId}/status-this', modern: '/api/v1/block-pages/vpn/profiles/{profileId}/status-this', purpose: 'VPN deaktivieren und erneut versuchen' },
  { method: 'GET', legacy: '/api/token/DASHBOARD', modern: '/api/v1/block-pages/auth/dashboard-token', purpose: 'Admin-Token löschen und Dashboard-Token erneuern' }
];

export const blockPageScenarios: readonly BlockPageScenario[] = [
  { key: 'redirect-options', legacyState: 'redirectOptions', title: 'Redirect-Optionen', pageKind: 'redirect', severity: 'warning', params: ['txid', 'originalDomain', 'targetDomain'], preservedActions: ['PASS decision', 'REDIRECT decision', 'remember redirect decision'] },
  { key: 'block-options', legacyState: 'blockOptions', title: 'Block-Optionen', pageKind: 'block-options', severity: 'warning', params: ['txid', 'originalDomain'], preservedActions: ['PASS decision', 'back to browser history'] },
  { key: 'access-denied', legacyState: 'blockerAccessDenied', title: 'Zugriff verweigert', pageKind: 'access-denied', severity: 'danger', params: ['target', 'profileId', 'userId', 'restrictions', 'listId', 'domain', 'externalAclMessage'], preservedActions: ['retry target URL', 'open parental-control console', 'change/unlock operating user by PIN', 'toggle remaining online time'] },
  { key: 'ads-trackers', legacyState: 'blockerAdsTrackers', title: 'Werbung/Tracker oder Custom Domain blockiert', pageKind: 'ads-trackers', severity: 'warning', params: ['target', 'category', 'domain', 'listId'], preservedActions: ['pause protection for 300 seconds', 'remove custom blocked domain', 'retry target URL'] },
  { key: 'malware', legacyState: 'blockerMalware', title: 'Malware blockiert', pageKind: 'malware', severity: 'danger', params: ['target', 'malware'], preservedActions: ['back to browser history'] },
  { key: 'whitelisted', legacyState: 'blockerWhitelisted', title: 'Whitelist-Ausnahme blockiert', pageKind: 'whitelisted', severity: 'info', params: ['target', 'category', 'domain'], preservedActions: ['retry target URL', 'back to browser history'] },
  { key: 'ssl-whitelist', legacyState: 'blockerSslWhitelisted', title: 'SSL-Ausnahme hinzufügen', pageKind: 'ssl-whitelist', severity: 'warning', params: ['target', 'token', 'appErrorCode', 'error'], preservedActions: ['add SSL whitelist domain and continue', 'back to browser history'] },
  { key: 'squid-error', legacyState: 'squidError', title: 'Squid-/Proxy-Fehler', pageKind: 'squid-error', severity: 'danger', params: ['target', 'token', 'error', 'errorDetails'], preservedActions: ['retry target URL', 'disable Tor and retry', 'disable VPN and retry'] },
  { key: 'logout-admin', legacyState: 'logoutAdmin', title: 'Admin abmelden', pageKind: 'logout', severity: 'info', params: [], preservedActions: ['logout admin token and return to dashboard'] }
];

export const blockPageActions = [
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
] as const;

export const accessDeniedReasons: readonly AccessDeniedReason[] = [
  { key: 'domain-blacklist', legacyCondition: 'externalAclMessage or listId/domain with blacklisting mode', visibleMessage: 'Domain steht auf einer Sperrliste' },
  { key: 'domain-not-whitelisted', legacyCondition: 'domain with whitelisting mode', visibleMessage: 'Domain ist nicht freigegeben' },
  { key: 'time-frame', legacyCondition: 'restrictions contains TIME_FRAME', visibleMessage: 'Zeitfenster erlaubt aktuell keinen Zugriff' },
  { key: 'max-usage', legacyCondition: 'restrictions contains MAX_USAGE_TIME', visibleMessage: 'Maximale Nutzungszeit ist erreicht' },
  { key: 'usage-time-disabled', legacyCondition: 'restrictions contains USAGE_TIME_DISABLED', visibleMessage: 'Restzeit kann ein-/ausgeschaltet werden' },
  { key: 'internet-blocked', legacyCondition: 'INTERNET_ACCESS_BLOCKED or profile.internetBlocked', visibleMessage: 'Internet ist für Gerät/Profil gesperrt' },
  { key: 'generic-access-denied', legacyCondition: 'no profile/external ACL data', visibleMessage: 'Allgemeiner Zugriff-verweigert-Fall' }
];

export const redirectDecisionRows: readonly RedirectDecisionRow[] = [
  { decision: 'PASS', target: '/redirect/PASS/{txid}', rememberable: true },
  { decision: 'REDIRECT', target: '/redirect/REDIRECT/{txid}', rememberable: true }
];

export const squidMitigationRows: readonly SquidMitigationRow[] = [
  { key: 'retry', legacyService: 'window.location.replace(target)', action: 'Erneut versuchen', delayMs: 0 },
  { key: 'disable-tor', legacyService: 'TorService.setDeviceConfig({sessionUseTor:false})', action: 'Tor deaktivieren und nach 1s erneut versuchen', delayMs: 1000 },
  { key: 'disable-vpn', legacyService: 'VpnService.setVpnThisDeviceStatus(profileId)', action: 'VPN deaktivieren und nach 2s erneut versuchen', delayMs: 2000 }
];

export const blockPageCapabilities = [
  'Redirect-/Block-Entscheidungen mit PASS/REDIRECT und Merken-Option erhalten',
  'Access-Denied-Gründe aus Profil, Liste, Zeitfenster, Max-Usage und Gerätesperre sichtbar machen',
  'Ads/Tracker/Custom-Domain-Blocker mit Pause, Retry und Domain entfernen abbilden',
  'Malware-, Whitelist-, SSL-Handshake- und Squid-Fehlerseiten als moderne Nutzerseiten erhalten',
  'Console-Redirect und Admin-Logout aus der Dashboard-App nicht vergessen'
] as const;

export function getBlockPageCenterTotals(): { legacyStates: number; endpoints: number; scenarios: number; routeDecisions: number; actions: number; accessReasons: number; } {
  return {
    legacyStates: blockPageLegacyStates.length,
    endpoints: blockPageEndpointMap.length,
    scenarios: blockPageScenarios.length,
    routeDecisions: blockRouteDecisions.length,
    actions: blockPageActions.length,
    accessReasons: accessDeniedReasons.length
  };
}
