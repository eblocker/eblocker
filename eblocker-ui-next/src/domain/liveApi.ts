import { type DeviceCenterRow, type DeviceCenterTotals } from './deviceCenter';
import { type SystemStatusCard } from './systemAdminCenter';

export interface LiveApplianceStatus {
  readonly executionState: string;
  readonly detail: string;
  readonly tone: SystemStatusCard['tone'];
}

export interface LiveModernUiState {
  readonly devices: readonly DeviceCenterRow[];
  readonly applianceStatus: LiveApplianceStatus;
}

export type ModernUiLiveStatus = 'loading' | 'live' | 'fallback';

type JsonRecord = Record<string, unknown>;
type FetchLike = (input: string, init?: RequestInit) => Promise<{ readonly ok: boolean; readonly status: number; json(): Promise<unknown> }>;

function asRecord(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {};
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
}

function asBoolean(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

function nestedString(record: JsonRecord, ...keys: readonly string[]): string | undefined {
  let current: unknown = record;
  for (const key of keys) {
    current = asRecord(current)[key];
  }
  return asString(current);
}

function stringList(value: unknown): readonly string[] {
  if (Array.isArray(value)) {
    return value
      .flatMap((entry) => {
        if (typeof entry === 'string') return [entry];
        const record = asRecord(entry);
        return [record.ipAddress, record.address, record.value].flatMap((candidate) => asString(candidate) ?? []);
      })
      .filter(Boolean);
  }
  return asString(value) ? [String(value)] : [];
}

function firstString(record: JsonRecord, keys: readonly string[], fallback: string): string {
  for (const key of keys) {
    const value = asString(record[key]);
    if (value) return value;
  }
  return fallback;
}

function protectionMode(record: JsonRecord): DeviceCenterRow['protectionMode'] {
  const enabled = asBoolean(record.enabled ?? record.enabledByDefault ?? record.filteringEnabled);
  const paused = asBoolean(record.paused ?? record.pausedByUser);
  const learning = asBoolean(record.learningMode ?? record.learning);
  if (enabled === false) return 'disabled';
  if (paused) return 'paused';
  if (learning) return 'learning';
  return 'protected';
}

function connectionState(record: JsonRecord, ipAddresses: readonly string[]): DeviceCenterRow['connectionState'] {
  const online = asBoolean(record.online ?? record.active ?? record.isOnline);
  if (online !== undefined) return online ? 'online' : 'offline';
  return ipAddresses.length > 0 ? 'online' : 'offline';
}

export function mapLiveDevice(rawDevice: unknown, index: number): DeviceCenterRow {
  const record = asRecord(rawDevice);
  const ipAddresses = [
    ...stringList(record.ipAddresses),
    ...stringList(record.ips),
    ...stringList(record.ipAddress),
    ...stringList(record.ipv4Address),
    ...stringList(record.ipv6Address)
  ].filter((value, position, values) => values.indexOf(value) === position);
  const id = firstString(record, ['id', 'deviceId', 'hardwareAddress', 'macAddress'], `live-device-${index + 1}`);
  const name = firstString(record, ['name', 'displayName', 'hostname', 'fullName'], id);

  return {
    id,
    name,
    kind: 'guest',
    ipAddresses,
    macAddress: firstString(record, ['macAddress', 'hardwareAddress'], 'unbekannt'),
    vendor: firstString(record, ['vendor', 'manufacturer'], 'Live-Gerät'),
    assignedProfile: nestedString(record, 'assignedUserProfile', 'name') ?? nestedString(record, 'userProfile', 'name') ?? firstString(record, ['assignedProfile', 'profileName'], 'Nicht zugeordnet'),
    assignedUser: nestedString(record, 'assignedUser', 'name') ?? nestedString(record, 'operatingUser', 'name') ?? firstString(record, ['assignedUserName', 'userName'], 'Nicht zugeordnet'),
    protectionMode: protectionMode(record),
    connectionState: connectionState(record, ipAddresses),
    blockedToday: Number(record.blockedToday ?? record.blockedRequestsToday ?? 0),
    isCurrentDevice: Boolean(record.isCurrentDevice ?? record.currentDevice),
    isEblocker: Boolean(record.isEblocker ?? record.eblocker),
    isGateway: Boolean(record.isGateway ?? record.gateway),
    lastSeen: firstString(record, ['lastSeen', 'lastSeenText'], ipAddresses.length > 0 ? 'live' : 'offline'),
    sslEnabled: Boolean(record.sslEnabled ?? record.sslActive),
    mobileEnabled: Boolean(record.mobileEnabled ?? record.isVpnClient),
    vpnActive: Boolean(record.vpnActive ?? record.useVPN),
    torActive: Boolean(record.torActive ?? record.useTor),
    iconMode: firstString(record, ['iconMode', 'controlBarIconMode'], 'Live'),
    deletable: Boolean(record.deletable ?? record.isDeletable)
  };
}

export function getLiveDeviceCenterTotals(devices: readonly DeviceCenterRow[]): DeviceCenterTotals {
  const currentDevice = devices.find((device) => device.isCurrentDevice);
  return {
    total: devices.length,
    protected: devices.filter((device) => device.protectionMode === 'protected').length,
    paused: devices.filter((device) => device.protectionMode === 'paused').length,
    learning: devices.filter((device) => device.protectionMode === 'learning').length,
    offline: devices.filter((device) => device.connectionState === 'offline').length,
    deletable: devices.filter((device) => device.deletable).length,
    currentDeviceName: currentDevice?.name ?? devices[0]?.name ?? 'Unbekannt'
  };
}

export function mapLiveApplianceStatus(rawStatus: unknown): LiveApplianceStatus {
  const record = asRecord(rawStatus);
  const executionState = firstString(record, ['executionState', 'state', 'status'], 'UNKNOWN');
  const detail = firstString(record, ['message', 'description', 'detail'], 'Live über /api/v1/lifecycle/appliance/status');
  const tone: SystemStatusCard['tone'] = ['OK', 'RUNNING'].includes(executionState) ? 'green' : ['BOOTING', 'UPDATING', 'RESTARTING'].includes(executionState) ? 'amber' : executionState === 'UNKNOWN' ? 'blue' : 'red';
  return { executionState, detail, tone };
}

async function fetchJson(fetcher: FetchLike, url: string): Promise<unknown> {
  const response = await fetcher(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }
  return response.json();
}

export async function loadLiveModernUiState(fetcher: FetchLike = fetch): Promise<LiveModernUiState> {
  const [devicesJson, statusJson] = await Promise.all([
    fetchJson(fetcher, '/api/v1/devices'),
    fetchJson(fetcher, '/api/v1/lifecycle/appliance/status')
  ]);
  const rawDevices = Array.isArray(devicesJson) ? devicesJson : [];
  return {
    devices: rawDevices.map(mapLiveDevice),
    applianceStatus: mapLiveApplianceStatus(statusJson)
  };
}
