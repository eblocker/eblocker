import { describe, expect, it, vi } from 'vitest';
import { getLiveDeviceCenterTotals, loadLiveModernUiState, mapLiveApplianceStatus, mapLiveDevice } from './liveApi';

describe('modern UI live API adapter', () => {
  it('maps legacy live device DTOs into device center rows', () => {
    const row = mapLiveDevice({
      id: 'dev-1',
      name: 'Live Notebook',
      ipAddresses: [{ address: '10.0.17.42' }, '2a12:de40:39:1::42'],
      hardwareAddress: 'AA:BB:CC:DD:EE:FF',
      vendor: 'Framework',
      assignedUserProfile: { name: 'Erwachsene' },
      operatingUser: { name: 'Shedowe' },
      enabled: true,
      online: true,
      sslEnabled: true,
      isCurrentDevice: true
    }, 0);

    expect(row).toMatchObject({
      id: 'dev-1',
      name: 'Live Notebook',
      ipAddresses: ['10.0.17.42', '2a12:de40:39:1::42'],
      macAddress: 'AA:BB:CC:DD:EE:FF',
      assignedProfile: 'Erwachsene',
      assignedUser: 'Shedowe',
      protectionMode: 'protected',
      connectionState: 'online',
      isCurrentDevice: true,
      sslEnabled: true
    });
  });

  it('computes totals from live device rows', () => {
    const rows = [
      mapLiveDevice({ id: 'one', enabled: true, online: true, isCurrentDevice: true, name: 'One' }, 0),
      mapLiveDevice({ id: 'two', enabled: false, online: false, deletable: true, name: 'Two' }, 1),
      mapLiveDevice({ id: 'three', paused: true, online: true, name: 'Three' }, 2)
    ];

    expect(getLiveDeviceCenterTotals(rows)).toEqual({
      total: 3,
      protected: 1,
      paused: 1,
      learning: 0,
      offline: 1,
      deletable: 1,
      currentDeviceName: 'One'
    });
  });

  it('maps appliance status tone from execution state', () => {
    expect(mapLiveApplianceStatus({ executionState: 'OK' })).toMatchObject({ executionState: 'OK', tone: 'green' });
    expect(mapLiveApplianceStatus({ executionState: 'UPDATING' })).toMatchObject({ executionState: 'UPDATING', tone: 'amber' });
    expect(mapLiveApplianceStatus({ executionState: 'ERROR' })).toMatchObject({ executionState: 'ERROR', tone: 'red' });
  });

  it('loads devices and appliance status from /api/v1 live endpoints', async () => {
    const fetcher = vi.fn(async (url: string) => ({
      ok: true,
      status: 200,
      json: async () => url === '/api/v1/devices'
        ? [{ id: 'dev-live', name: 'Live Device', ipAddress: '10.0.17.9' }]
        : { executionState: 'RUNNING', detail: 'runtime' }
    }));

    await expect(loadLiveModernUiState(fetcher)).resolves.toMatchObject({
      devices: [{ id: 'dev-live', name: 'Live Device' }],
      applianceStatus: { executionState: 'RUNNING', tone: 'green' }
    });
    expect(fetcher).toHaveBeenCalledWith('/api/v1/devices', { headers: { Accept: 'application/json' } });
    expect(fetcher).toHaveBeenCalledWith('/api/v1/lifecycle/appliance/status', { headers: { Accept: 'application/json' } });
  });
});
