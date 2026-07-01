import { describe, expect, it } from 'vitest';
import {
  dashboardMetrics,
  deviceRows,
  serviceHealth,
  protectionModules,
  activityEvents,
  getCriticalServiceCount,
  getProtectedDeviceCount,
  getTotalBlockedToday
} from './dashboard';

describe('modern eBlocker dashboard model', () => {
  it('contains real admin-console sections instead of migration placeholders', () => {
    expect(dashboardMetrics.map((metric) => metric.id)).toEqual([
      'devices',
      'blocked',
      'dns',
      'https'
    ]);
    expect(protectionModules.map((module) => module.id)).toEqual([
      'ads-trackers',
      'malware',
      'parental-control',
      'vpn-mobile'
    ]);
  });

  it('summarizes protected devices, blocked requests and service warnings', () => {
    expect(getProtectedDeviceCount()).toBeGreaterThan(0);
    expect(getTotalBlockedToday()).toBeGreaterThan(1000);
    expect(getCriticalServiceCount()).toBe(0);
  });

  it('provides table-ready devices, service health and activity events', () => {
    expect(deviceRows.length).toBeGreaterThanOrEqual(5);
    expect(deviceRows.every((device) => device.ipAddress.startsWith('10.0.'))).toBe(true);
    expect(serviceHealth.length).toBeGreaterThanOrEqual(5);
    expect(activityEvents.length).toBeGreaterThanOrEqual(4);
  });
});
