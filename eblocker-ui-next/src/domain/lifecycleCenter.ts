export type LifecycleEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
export type LifecycleModule = 'settings' | 'advice';
export type LifecycleSeverity = 'info' | 'warning' | 'danger' | 'success';

export interface LifecycleLegacyState {
  module: LifecycleModule;
  stateName: string;
  stateVar: string;
  component: string;
  modernSurface: string;
  userOutcome: string;
}

export interface LifecycleStep {
  key: string;
  label: string;
  guard: string;
  services: string[];
  endpoints: string[];
}

export interface LifecycleEndpoint {
  method: LifecycleEndpointMethod;
  legacy: string;
  modern: string;
  purpose: string;
}

export const lifecycleLegacyStates: LifecycleLegacyState[] = [
  { module: 'settings', stateName: 'nolicense', stateVar: 'notLicensed', component: 'notLicensedComponent', modernSurface: 'license-gates-splash', userOutcome: 'Feature-spezifische Lizenz-/Upsell-Informationen anzeigen' },
  { module: 'settings', stateName: 'standby', stateVar: 'standBy', component: 'standByComponent', modernSurface: 'appliance-pending', userOutcome: 'Warten auf Reboot/Shutdown/Update und danach zurück zur Herkunft' },
  { module: 'settings', stateName: 'factoryResetScreen', stateVar: 'factoryResetScreen', component: 'factoryResetScreenComponent', modernSurface: 'appliance-pending', userOutcome: 'Factory-Reset-Übergang bis DOWN/OFF oder Escape-Schwelle begleiten' },
  { module: 'settings', stateName: 'booting', stateVar: 'booting', component: 'bootingComponent', modernSurface: 'appliance-pending', userOutcome: 'Boot/Reboot-Status pollend anzeigen' },
  { module: 'settings', stateName: 'updating', stateVar: 'updating', component: 'updatingComponent', modernSurface: 'appliance-pending', userOutcome: 'Update-Fortschritt und Reload-nach-Boot erhalten' },
  { module: 'settings', stateName: 'shutdown', stateVar: 'shutdown', component: 'shutdownComponent', modernSurface: 'appliance-pending', userOutcome: 'Shutdown/Reboot-Abschaltung bis stabilen Folgestatus verfolgen' },
  { module: 'settings', stateName: 'activation', stateVar: 'activation', component: 'activationComponent', modernSurface: 'activation-setup', userOutcome: 'Initialen Setup-/Aktivierungswizard ausführen' },
  { module: 'settings', stateName: 'activationfinish', stateVar: 'activationFinish', component: 'activationFinishComponent', modernSurface: 'activation-setup', userOutcome: 'Post-Registration, VPN-Reminder und Update-Check abschließen' },
  { module: 'settings', stateName: 'login', stateVar: 'login', component: 'loginComponent', modernSurface: 'auth-login-reset', userOutcome: 'Admin-Passwort prüfen und Login-Countdown anzeigen' },
  { module: 'settings', stateName: 'resetpassword', stateVar: 'resetPassword', component: 'resetPasswordComponent', modernSurface: 'auth-login-reset', userOutcome: 'Passwort-Reset über Reboot-Workflow initiieren/ausführen/abbrechen' },
  { module: 'settings', stateName: 'auth', stateVar: 'auth', component: 'authComponent', modernSurface: 'auth-login-reset', userOutcome: 'Token holen, Workflow fortsetzen und Idle-Watch starten' },
  { module: 'settings', stateName: 'splash', stateVar: 'splashScreen', component: 'splashScreenComponent', modernSurface: 'license-gates-splash', userOutcome: 'Splash einmalig anzeigen oder dauerhaft ausblenden' },
  { module: 'settings', stateName: 'expired', stateVar: 'expired', component: 'expiredComponent', modernSurface: 'license-gates-splash', userOutcome: 'Abgelaufene Session zurück in Auth führen' },
  { module: 'advice', stateName: 'app', stateVar: 'app', component: 'adviceComponent', modernSurface: 'advice-overlays', userOutcome: 'Advice-Shell mit Locale/Gerät/Produktinfo auflösen' },
  { module: 'advice', stateName: 'welcome', stateVar: 'welcome', component: 'welcomeComponent', modernSurface: 'advice-overlays', userOutcome: 'Welcome-/Bookmark-Hinweis mit AutoClose und Dashboard-Link zeigen' },
  { module: 'advice', stateName: 'reminder', stateVar: 'reminder', component: 'reminderComponent', modernSurface: 'advice-overlays', userOutcome: 'Lizenz-Erinnerung mit Phasen E0–E4 und Kauf-/Lizenzaktionen zeigen' }
];

export const authEntryFlow = {
  title: 'Auth, Login & Token-Erneuerung',
  steps: [
    { key: 'reuse-stored-context', label: 'Gespeicherten Security-Kontext nutzen' },
    { key: 'request-init-token', label: 'Initiales Token vom Server laden' },
    { key: 'password-required', label: 'Bei Passwortpflicht in Login wechseln' },
    { key: 'continue-workflow', label: 'Workflow-State validieren und fortsetzen' },
    { key: 'start-idle-watch', label: 'Idle/Keepalive-Watch für JWT-Erneuerung starten' }
  ],
  errorStates: ['PASSWORD_INVALID', 'PASSWORD_TOO_FREQ', 'UNKNOWN'],
  endpoints: [
    'GET /api/adminconsole/authentication/token/{context}',
    'POST /api/adminconsole/authentication/login/{context}',
    'GET /api/adminconsole/authentication/wait',
    'GET /api/adminconsole/authentication/renew/{context}'
  ]
};

export const passwordResetFlow = {
  title: 'Admin-Passwort zurücksetzen',
  steps: [
    { key: 'initiate-reset', label: 'Reset-Token und Shutdown-Grace-Period holen' },
    { key: 'persist-workflow-through-reboot', label: 'Workflow-State über Reboot persistent halten' },
    { key: 'execute-or-cancel-reset', label: 'Reset ausführen oder Reset-Token abbrechen' }
  ],
  failureReasons: [
    'ERROR_PASSWORD_RESET_SHUTDOWN_EXPIRED',
    'ERROR_PASSWORD_RESET_UNKNOWN_SERVER_STATE',
    'ERROR_PASSWORD_RESET_NOT_INITIATED',
    'ERROR_PASSWORD_RESET_INVALID_TOKEN',
    'ERROR_PASSWORD_RESET_TOKEN_EXPIRED',
    'ERROR_PASSWORD_RESET_UPDATE_RUNNING'
  ],
  endpoints: [
    'POST /api/adminconsole/authentication/initiateReset',
    'POST /api/adminconsole/authentication/executeReset',
    'POST /api/adminconsole/authentication/cancelReset',
    'GET /api/adminconsole/systemstatus'
  ]
};

export const activationWizardSteps: LifecycleStep[] = [
  { key: 'welcome', label: 'Willkommen', guard: 'immer erlaubt', services: ['LanguageService', 'SetupService.getInfo'], endpoints: ['GET /api/adminconsole/setup/info'] },
  { key: 'tos', label: 'AGB', guard: 'TOS bestätigt oder keine Registrierung möglich bestätigt', services: ['TosService.getTos', 'TosService.getTosHtml'], endpoints: ['GET /api/adminconsole/tos'] },
  { key: 'time-language', label: 'Sprache & Zeitzone', guard: 'AGB/No-Registration bestätigt', services: ['settings.setLocale', 'TimezoneService.getRegions', 'TimezoneService.setRegionAndGetCities'], endpoints: ['GET /api/adminconsole/settings', 'PUT /api/adminconsole/settings', 'GET /api/adminconsole/timezone/continents', 'PUT /api/adminconsole/timezone/continent/countries'] },
  { key: 'device', label: 'Gerät', guard: 'Zeitzone gesetzt', services: ['RegistrationService.register fallback path'], endpoints: ['POST /api/adminconsole/registration'] },
  { key: 'auto-enable-new-devices', label: 'Neue Geräte automatisch aktivieren', guard: 'Zeitzone gesetzt', services: ['DeviceService.setAutoEnableNewDevicesAfterActivation'], endpoints: ['POST /api/adminconsole/devices/autoEnableNewDevicesAfterActivation'] },
  { key: 'license', label: 'Lizenz', guard: 'Auto-Enable-Entscheidung gesetzt', services: ['RegistrationService.register'], endpoints: ['POST /api/adminconsole/registration'] }
];

export const applianceStatePages = [
  {
    legacyState: 'standby',
    title: 'Standby / System Pending',
    executionStates: ['RUNNING', 'OK', 'SHUTTING_DOWN', 'SHUTTING_DOWN_FOR_REBOOT', 'BOOTING', 'RESTARTING', 'ERROR', 'UPDATING', 'DOWN', 'OFF'],
    transitions: ['RUNNING/OK after >3 checks → auth/dashboard/setup', 'SHUTTING_DOWN* → shutdown', 'BOOTING/RESTARTING/ERROR → booting', 'UPDATING → updating', 'DOWN/OFF → reboot/shutdown wait'],
    thresholds: ['shutdown 120s', 'reboot 600s', 'reboot hint countdown 30s'],
    severity: 'warning' as LifecycleSeverity
  },
  {
    legacyState: 'booting',
    title: 'Booting / Restarting',
    executionStates: ['BOOTING', 'RESTARTING', 'ERROR'],
    transitions: ['stable non-BOOTING/RESTARTING/ERROR for >2 checks → standby'],
    thresholds: ['stable-state timeout 2 checks'],
    severity: 'info' as LifecycleSeverity
  },
  {
    legacyState: 'updating',
    title: 'Updating',
    executionStates: ['UPDATING'],
    transitions: ['non-UPDATING for >2 checks → standby with reloadAfterBoot'],
    thresholds: ['stable-state timeout 2 checks'],
    severity: 'success' as LifecycleSeverity
  },
  {
    legacyState: 'shutdown',
    title: 'Shutdown / Reboot shutdown phase',
    executionStates: ['SHUTTING_DOWN', 'SHUTTING_DOWN_FOR_REBOOT'],
    transitions: ['non-SHUTTING_DOWN for >2 checks → standby'],
    thresholds: ['stable-state timeout 2 checks'],
    severity: 'danger' as LifecycleSeverity
  },
  {
    legacyState: 'factoryResetScreen',
    title: 'Factory Reset',
    executionStates: ['DOWN', 'OFF'],
    transitions: ['DOWN/OFF → standby', 'loop threshold → standby'],
    thresholds: ['escape loop after 120 checks'],
    severity: 'danger' as LifecycleSeverity
  }
];

export const adviceOverlayFlows = [
  {
    legacyState: 'app',
    title: 'Advice Shell',
    actions: ['resolve locale', 'resolve device', 'resolve product info', 'loading flag'],
    endpoints: ['GET /api/advice/device', 'GET /api/adminconsole/registration'],
    phaseKeys: []
  },
  {
    legacyState: 'welcome',
    title: 'Welcome Overlay',
    actions: ['AutoClose countdown', 'toggle welcome/bookmark flags', 'go to dashboard', 'close overlay'],
    endpoints: ['GET /api/advice/device', 'PUT /api/advice/device/showWelcomeFlags'],
    phaseKeys: []
  },
  {
    legacyState: 'reminder',
    title: 'License Reminder Overlay',
    actions: ['open license page', 'open purchase URL', 'save remind-later selection', 'postMessage close overlay'],
    endpoints: ['POST /api/advice/reminder', 'GET /api/adminconsole/registration'],
    phaseKeys: ['E0', 'E1', 'E2', 'E3', 'E4']
  }
];

export const lifecycleSurfaceCards = [
  { key: 'auth-login-reset', title: 'Auth, Login & Reset', states: ['auth', 'login', 'resetpassword'], summary: 'Token, Passwortpflicht, Login-Fehler, Reset über Reboot und Idle-Watch.' },
  { key: 'activation-setup', title: 'Aktivierung & Setup', states: ['activation', 'activationfinish'], summary: 'Wizard mit Sprache, AGB, Zeitzone, Gerät, Auto-Enable, Lizenz und Post-Registration.' },
  { key: 'appliance-pending', title: 'Appliance-Statusseiten', states: ['standby', 'booting', 'updating', 'shutdown', 'factoryResetScreen'], summary: 'Polling-Übergänge für Reboot, Shutdown, Update, Factory Reset und Fehler.' },
  { key: 'license-gates-splash', title: 'Lizenz, Splash & Session', states: ['nolicense', 'splash', 'expired'], summary: 'Upsell, Splash-Ausblendung und Session-Expired-Rückkehr zu Auth.' },
  { key: 'advice-overlays', title: 'Advice Overlays', states: ['app', 'welcome', 'reminder'], summary: 'Welcome, Bookmark-Flags, AutoClose und Lizenz-Reminder-Phasen.' }
];

export const lifecycleEndpointMap: LifecycleEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/authentication/token/{context}', modern: '/api/v1/lifecycle/auth/init-token/{context}', purpose: 'initiales JWT für Adminconsole-Kontext' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/login/{context}', modern: '/api/v1/lifecycle/auth/login/{context}', purpose: 'Admin-Passwort gegen Kontext prüfen' },
  { method: 'GET', legacy: '/api/adminconsole/authentication/wait', modern: '/api/v1/lifecycle/auth/login-wait', purpose: 'Login-Countdown nach Fehlversuchen' },
  { method: 'GET', legacy: '/api/adminconsole/authentication/renew/{context}', modern: '/api/v1/lifecycle/auth/renew/{context}', purpose: 'JWT nach Reboot/Idle erneuern' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/initiateReset', modern: '/api/v1/lifecycle/password-reset/initiate', purpose: 'Reset-Token und Shutdown-Grace-Period starten' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/executeReset', modern: '/api/v1/lifecycle/password-reset/execute', purpose: 'Reset-Token ausführen' },
  { method: 'POST', legacy: '/api/adminconsole/authentication/cancelReset', modern: '/api/v1/lifecycle/password-reset/cancel', purpose: 'Reset-Token verwerfen' },
  { method: 'GET', legacy: '/api/adminconsole/settings', modern: '/api/v1/lifecycle/settings/locale', purpose: 'Locale laden' },
  { method: 'PUT', legacy: '/api/adminconsole/settings', modern: '/api/v1/lifecycle/settings/locale', purpose: 'Sprache/Timezone speichern' },
  { method: 'GET', legacy: '/api/adminconsole/timezone/continents', modern: '/api/v1/lifecycle/timezone/continents', purpose: 'Zeitzonen-Regionen laden' },
  { method: 'PUT', legacy: '/api/adminconsole/timezone/continent/countries', modern: '/api/v1/lifecycle/timezone/cities', purpose: 'Städte für Region laden' },
  { method: 'GET', legacy: '/api/adminconsole/setup/info', modern: '/api/v1/lifecycle/setup/info', purpose: 'SetupInfo und TOS-Container laden' },
  { method: 'GET', legacy: '/api/adminconsole/tos', modern: '/api/v1/lifecycle/setup/tos', purpose: 'AGB laden' },
  { method: 'GET', legacy: '/api/adminconsole/registration', modern: '/api/v1/lifecycle/registration', purpose: 'Registrierungsstatus laden' },
  { method: 'POST', legacy: '/api/adminconsole/registration', modern: '/api/v1/lifecycle/registration', purpose: 'Lizenz registrieren' },
  { method: 'DELETE', legacy: '/api/adminconsole/registration', modern: '/api/v1/lifecycle/registration', purpose: 'Lizenz zurücksetzen' },
  { method: 'GET', legacy: '/api/adminconsole/upsellInfo/{feature}', modern: '/api/v1/lifecycle/license-gates/upsell/{feature}', purpose: 'No-License-Upsell laden' },
  { method: 'GET', legacy: '/api/adminconsole/splash', modern: '/api/v1/lifecycle/splash', purpose: 'Splash-Preference laden' },
  { method: 'POST', legacy: '/api/adminconsole/splash', modern: '/api/v1/lifecycle/splash', purpose: 'Splash-Preference speichern' },
  { method: 'GET', legacy: '/api/adminconsole/systemstatus', modern: '/api/v1/lifecycle/appliance/status', purpose: 'ExecutionState pollend laden' },
  { method: 'POST', legacy: '/api/adminconsole/systemstatus/reboot', modern: '/api/v1/lifecycle/appliance/reboot', purpose: 'normaler Reboot' },
  { method: 'POST', legacy: '/api/adminconsole/systemstatus/shutdown', modern: '/api/v1/lifecycle/appliance/shutdown', purpose: 'normaler Shutdown' },
  { method: 'POST', legacy: '/api/adminconsole/systemstatus/reboot/onerror', modern: '/api/v1/lifecycle/appliance/reboot-on-error', purpose: 'Reboot aus Fehlerseite' },
  { method: 'POST', legacy: '/api/adminconsole/systemstatus/shutdown/onerror', modern: '/api/v1/lifecycle/appliance/shutdown-on-error', purpose: 'Shutdown aus Fehlerseite' },
  { method: 'GET', legacy: '/api/adminconsole/updates/status', modern: '/api/v1/lifecycle/updates/status', purpose: 'Update-Status laden' },
  { method: 'POST', legacy: '/api/adminconsole/updates/status', modern: '/api/v1/lifecycle/updates/status', purpose: 'Update starten' },
  { method: 'GET', legacy: '/api/adminconsole/updates/check', modern: '/api/v1/lifecycle/updates/check', purpose: 'nach Updates suchen' },
  { method: 'GET', legacy: '/api/adminconsole/customerInfo', modern: '/api/v1/lifecycle/customer-info', purpose: 'Post-Registration-Hinweis laden' },
  { method: 'POST', legacy: '/api/adminconsole/customerInfo', modern: '/api/v1/lifecycle/customer-info', purpose: 'VPN-Reminder später speichern' },
  { method: 'DELETE', legacy: '/api/adminconsole/customerInfo', modern: '/api/v1/lifecycle/customer-info', purpose: 'VPN-Reminder nie wieder anzeigen' },
  { method: 'GET', legacy: '/api/advice/device', modern: '/api/v1/lifecycle/advice/device', purpose: 'Advice-Gerät laden' },
  { method: 'PUT', legacy: '/api/advice/device/showWelcomeFlags', modern: '/api/v1/lifecycle/advice/device/welcome-flags', purpose: 'Welcome/Bookmark-Flags speichern' },
  { method: 'POST', legacy: '/api/advice/reminder', modern: '/api/v1/lifecycle/advice/reminder', purpose: 'Reminder-Auswahl speichern' },
  { method: 'POST', legacy: '/api/adminconsole/devices/autoEnableNewDevicesAfterActivation', modern: '/api/v1/lifecycle/devices/auto-enable-after-activation', purpose: 'Neue Geräte nach Aktivierung automatisch aktivieren' },
  { method: 'GET', legacy: '/api/adminconsole/console/ip', modern: '/api/v1/lifecycle/console/url', purpose: 'Login/Console-Ziel initialisieren' }
];

export const lifecycleCapabilities = [
  'Login/Auth/Token-Renewal inklusive Idle-Watch und Fehlerzuständen sichtbar abbilden',
  'Admin-Passwort-Reset mit Token, Grace-Period, Reboot-Persistenz und bekannten Fehlern erhalten',
  'Aktivierungswizard mit Sprache, AGB, Zeitzone, Gerät, Auto-Enable und Lizenz erhalten',
  'Standby/Booting/Updating/Shutdown/Factory-Reset als echte Appliance-Statusseiten modellieren',
  'No-License, Splash und Session-Expired als Gate-/Recovery-Seiten sichtbar behalten',
  'Advice Welcome/Reminder inklusive AutoClose, Phasen E0–E4 und Reminder-POST erhalten'
];

export function getLifecycleCenterTotals() {
  return {
    legacyStates: lifecycleLegacyStates.length,
    surfaces: lifecycleSurfaceCards.length,
    activationSteps: activationWizardSteps.length,
    pendingPages: applianceStatePages.length,
    endpoints: lifecycleEndpointMap.length,
    capabilities: lifecycleCapabilities.length
  };
}
