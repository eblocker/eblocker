import { describe, expect, it } from 'vitest';
import {
  activationWizardSteps,
  adviceOverlayFlows,
  applianceStatePages,
  authEntryFlow,
  getLifecycleCenterTotals,
  lifecycleCapabilities,
  lifecycleEndpointMap,
  lifecycleLegacyStates,
  lifecycleSurfaceCards,
  passwordResetFlow
} from './lifecycleCenter';

describe('lifecycle center legacy parity model', () => {
  it('covers all 16 legacy Zugang/Setup/Lifecycle states from settings and advice apps', () => {
    const expectedStates = [
      'nolicense',
      'standby',
      'factoryResetScreen',
      'booting',
      'updating',
      'shutdown',
      'activation',
      'activationfinish',
      'login',
      'resetpassword',
      'auth',
      'splash',
      'expired',
      'app',
      'welcome',
      'reminder'
    ];

    expect(lifecycleLegacyStates).toHaveLength(expectedStates.length);
    expect(lifecycleLegacyStates.map((state) => state.stateName)).toEqual(expectedStates);
    expect(new Set(lifecycleLegacyStates.map((state) => state.stateName)).size).toBe(expectedStates.length);
    expect(lifecycleLegacyStates.every((state) => state.component && state.modernSurface && state.userOutcome)).toBe(true);
    expect(lifecycleLegacyStates.filter((state) => state.module === 'advice')).toHaveLength(3);
  });

  it('preserves auth, login throttling, token renewal and reset-password reboot workflow', () => {
    expect(authEntryFlow.steps.map((step) => step.key)).toEqual([
      'reuse-stored-context',
      'request-init-token',
      'password-required',
      'continue-workflow',
      'start-idle-watch'
    ]);
    expect(authEntryFlow.errorStates).toEqual(['PASSWORD_INVALID', 'PASSWORD_TOO_FREQ', 'UNKNOWN']);
    expect(authEntryFlow.endpoints).toEqual([
      'GET /api/adminconsole/authentication/token/{context}',
      'POST /api/adminconsole/authentication/login/{context}',
      'GET /api/adminconsole/authentication/wait',
      'GET /api/adminconsole/authentication/renew/{context}'
    ]);

    expect(passwordResetFlow.steps.map((step) => step.key)).toEqual([
      'initiate-reset',
      'persist-workflow-through-reboot',
      'execute-or-cancel-reset'
    ]);
    expect(passwordResetFlow.failureReasons).toEqual([
      'ERROR_PASSWORD_RESET_SHUTDOWN_EXPIRED',
      'ERROR_PASSWORD_RESET_UNKNOWN_SERVER_STATE',
      'ERROR_PASSWORD_RESET_NOT_INITIATED',
      'ERROR_PASSWORD_RESET_INVALID_TOKEN',
      'ERROR_PASSWORD_RESET_TOKEN_EXPIRED',
      'ERROR_PASSWORD_RESET_UPDATE_RUNNING'
    ]);
    expect(passwordResetFlow.endpoints).toEqual([
      'POST /api/adminconsole/authentication/initiateReset',
      'POST /api/adminconsole/authentication/executeReset',
      'POST /api/adminconsole/authentication/cancelReset',
      'GET /api/adminconsole/systemstatus'
    ]);
  });

  it('keeps the full activation wizard order and its old guards/services visible', () => {
    expect(activationWizardSteps.map((step) => step.label)).toEqual([
      'Willkommen',
      'AGB',
      'Sprache & Zeitzone',
      'Gerät',
      'Neue Geräte automatisch aktivieren',
      'Lizenz'
    ]);
    expect(activationWizardSteps).toHaveLength(6);
    expect(activationWizardSteps[1].guard).toBe('TOS bestätigt oder keine Registrierung möglich bestätigt');
    expect(activationWizardSteps[2].services).toContain('TimezoneService.setRegionAndGetCities');
    expect(activationWizardSteps[4].endpoints).toContain('POST /api/adminconsole/devices/autoEnableNewDevicesAfterActivation');
    expect(activationWizardSteps[5].endpoints).toContain('POST /api/adminconsole/registration');
  });

  it('models appliance pending states, transitions and legacy timeout thresholds', () => {
    const byState = Object.fromEntries(applianceStatePages.map((page) => [page.legacyState, page]));

    expect(Object.keys(byState)).toEqual(['standby', 'booting', 'updating', 'shutdown', 'factoryResetScreen']);
    expect(byState.standby.transitions).toContain('RUNNING/OK after >3 checks → auth/dashboard/setup');
    expect(byState.standby.thresholds).toEqual(['shutdown 120s', 'reboot 600s', 'reboot hint countdown 30s']);
    expect(byState.booting.transitions).toContain('stable non-BOOTING/RESTARTING/ERROR for >2 checks → standby');
    expect(byState.updating.transitions).toContain('non-UPDATING for >2 checks → standby with reloadAfterBoot');
    expect(byState.shutdown.transitions).toContain('non-SHUTTING_DOWN for >2 checks → standby');
    expect(byState.factoryResetScreen.thresholds).toContain('escape loop after 120 checks');
  });

  it('preserves Advice welcome/reminder overlays and license phase behavior', () => {
    expect(adviceOverlayFlows.map((flow) => flow.legacyState)).toEqual(['app', 'welcome', 'reminder']);
    expect(adviceOverlayFlows.find((flow) => flow.legacyState === 'welcome')?.actions).toEqual([
      'AutoClose countdown',
      'toggle welcome/bookmark flags',
      'go to dashboard',
      'close overlay'
    ]);
    expect(adviceOverlayFlows.find((flow) => flow.legacyState === 'reminder')?.phaseKeys).toEqual(['E0', 'E1', 'E2', 'E3', 'E4']);
    expect(adviceOverlayFlows.find((flow) => flow.legacyState === 'reminder')?.endpoints).toContain('POST /api/advice/reminder');
  });

  it('maps every legacy lifecycle endpoint to a modern lifecycle API target', () => {
    expect(lifecycleEndpointMap).toHaveLength(35);
    expect(lifecycleEndpointMap.every((endpoint) => endpoint.modern.startsWith('/api/v1/lifecycle/'))).toBe(true);
    expect(lifecycleEndpointMap.map((endpoint) => `${endpoint.method} ${endpoint.legacy}`)).toEqual(expect.arrayContaining([
      'GET /api/adminconsole/authentication/token/{context}',
      'POST /api/adminconsole/authentication/login/{context}',
      'GET /api/adminconsole/setup/info',
      'GET /api/adminconsole/tos',
      'PUT /api/adminconsole/timezone/continent/countries',
      'POST /api/adminconsole/devices/autoEnableNewDevicesAfterActivation',
      'GET /api/adminconsole/systemstatus',
      'POST /api/adminconsole/systemstatus/reboot/onerror',
      'GET /api/advice/device',
      'PUT /api/advice/device/showWelcomeFlags',
      'POST /api/advice/reminder'
    ]));
  });

  it('reports center totals and visible parity capabilities', () => {
    expect(getLifecycleCenterTotals()).toEqual({
      legacyStates: 16,
      surfaces: lifecycleSurfaceCards.length,
      activationSteps: 6,
      pendingPages: 5,
      endpoints: 35,
      capabilities: lifecycleCapabilities.length
    });
    expect(lifecycleSurfaceCards.map((card) => card.key)).toEqual([
      'auth-login-reset',
      'activation-setup',
      'appliance-pending',
      'license-gates-splash',
      'advice-overlays'
    ]);
    expect(lifecycleCapabilities).toEqual(expect.arrayContaining([
      'Login/Auth/Token-Renewal inklusive Idle-Watch und Fehlerzuständen sichtbar abbilden',
      'Aktivierungswizard mit Sprache, AGB, Zeitzone, Gerät, Auto-Enable und Lizenz erhalten',
      'Standby/Booting/Updating/Shutdown/Factory-Reset als echte Appliance-Statusseiten modellieren',
      'Advice Welcome/Reminder inklusive AutoClose, Phasen E0–E4 und Reminder-POST erhalten'
    ]));
  });
});
