import { describe, expect, it } from 'vitest';
import {
  deviceCenterCapabilities,
  deviceCenterEndpoints,
  deviceCenterRows,
  deviceDiscoverySettings,
  deviceDetailTabs,
  getCurrentDevice,
  getDeletableDeviceIds,
  getDeviceCenterTotals,
  getLegacyDeviceStateNames,
  getSelectedDeviceDetail
} from './deviceCenter';

describe('modern devices center parity with legacy AngularJS devices UI', () => {
  it('covers every legacy devices state explicitly', () => {
    expect(getLegacyDeviceStateNames()).toEqual([
      'devices',
      'devicesstate',
      'deviceslist',
      'devicesdiscovery',
      'devicedetails'
    ]);
  });

  it('keeps the legacy DeviceService endpoint surface visible for API migration', () => {
    expect(deviceCenterEndpoints).toHaveLength(11);
    expect(deviceCenterEndpoints.map((endpoint) => `${endpoint.method} ${endpoint.legacyPath}`)).toEqual([
      'GET /api/adminconsole/devices',
      'GET /api/adminconsole/devices/{deviceId}',
      'PUT /api/adminconsole/devices/{deviceId}',
      'DELETE /api/adminconsole/devices/{deviceId}',
      'PUT /api/adminconsole/devices/reset/{deviceId}',
      'GET /api/adminconsole/devices/scan',
      'GET /api/adminconsole/devices/scanningInterval',
      'POST /api/adminconsole/devices/scanningInterval',
      'POST /api/adminconsole/devices/scan',
      'GET /api/adminconsole/devices/autoEnableNewDevices',
      'POST /api/adminconsole/devices/autoEnableNewDevices'
    ]);
    expect(deviceCenterEndpoints.every((endpoint) => endpoint.modernPath.startsWith('/api/v1/devices'))).toBe(true);
  });

  it('models list, discovery and details capabilities from the old UI', () => {
    expect(deviceCenterCapabilities).toEqual(expect.arrayContaining([
      'Schutz ein-/ausschalten',
      'Gerät löschen und Offline-/No-IP-Auswahl',
      'Aktuelles Gerät anspringen',
      'Scan-Intervall ändern',
      'Manuellen Netzwerk-Scan starten',
      'Neue Geräte automatisch aktivieren',
      'Name und statische IPv4/IPv6 bearbeiten',
      'Benutzer/Profile zuordnen',
      'HTTPS, Filter, Anonymisierung, Mobile, Icon und Nachrichten konfigurieren'
    ]));
    expect(deviceDetailTabs.map((tab) => tab.id)).toEqual(['device', 'users', 'anon', 'mobile', 'filters', 'https', 'icon', 'messages']);
  });

  it('provides dashboard totals and action-ready rows', () => {
    expect(getDeviceCenterTotals()).toEqual({
      total: 8,
      protected: 5,
      paused: 1,
      learning: 1,
      offline: 2,
      deletable: 2,
      currentDeviceName: 'MacBook Pro'
    });
    expect(getCurrentDevice()?.isCurrentDevice).toBe(true);
    expect(getDeletableDeviceIds()).toEqual(['old-printer', 'guest-phone']);
  });

  it('surfaces selected-device details and discovery settings', () => {
    expect(getSelectedDeviceDetail().name).toBe('MacBook Pro');
    expect(getSelectedDeviceDetail().detailPanels.some((panel) => panel.id === 'static-ip')).toBe(true);
    expect(deviceDiscoverySettings).toMatchObject({
      scanIntervalSeconds: 300,
      scanningAvailable: true,
      autoEnableNewDevices: false
    });
    expect(deviceCenterRows.length).toBeGreaterThanOrEqual(8);
  });
});
