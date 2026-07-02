import { blockPageEndpointMap } from './blockPagesCenter';
import { deviceCenterEndpoints } from './deviceCenter';
import { familyCenterEndpoints } from './familyCenter';
import { httpsCenterEndpoints } from './httpsCenter';
import { lifecycleEndpointMap } from './lifecycleCenter';
import { networkCenterEndpoints } from './networkCenter';
import { protectionCenterEndpoints } from './protectionCenter';
import { systemAdminCenterEndpoints } from './systemAdminCenter';
import { vpnMobileCenterEndpoints } from './vpnMobileCenter';

export const OPEN_API_PUBLIC_PATH = 'public/openapi/eblocker-modern-api.openapi.json';
export const SWAGGER_PUBLIC_PATH = 'public/swagger/index.html';

export type OpenApiHttpMethod = 'get' | 'put' | 'post' | 'delete' | 'patch';

export interface OpenApiParameter {
  readonly name: string;
  readonly in: 'path';
  readonly required: true;
  readonly schema: { readonly type: 'string' };
}

export interface OpenApiOperation {
  readonly tags: readonly string[];
  readonly summary: string;
  readonly operationId: string;
  readonly parameters?: readonly OpenApiParameter[];
  readonly responses: {
    readonly '200': { readonly description: string };
  };
  readonly 'x-legacy-path': string;
  readonly 'x-backend-status': 'live-bridge' | 'contract-only';
}

export interface OpenApiDocument {
  readonly openapi: '3.1.0';
  readonly info: {
    readonly title: string;
    readonly version: string;
    readonly description: string;
  };
  readonly servers: readonly { readonly url: string; readonly description: string }[];
  readonly tags: readonly { readonly name: string; readonly description: string }[];
  readonly paths: Record<string, Partial<Record<OpenApiHttpMethod, OpenApiOperation>>>;
}

interface LegacyModernEndpoint {
  readonly method: string;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

interface LegacyPathModernPathEndpoint {
  readonly method: string;
  readonly legacyPath: string;
  readonly modernPath: string;
  readonly purpose: string;
}

interface SourceOperation {
  readonly groupId: string;
  readonly tag: string;
  readonly method: string;
  readonly legacyPath: string;
  readonly modernPath: string;
  readonly purpose: string;
}

const tagDescriptions: Record<string, string> = {
  'Block pages': 'Redirect, blocker, Squid and browser-facing error pages.',
  Devices: 'Known clients, discovery, details, pause/protection state and device actions.',
  Family: 'Users, profiles, time windows, filter lists and parental-control limits.',
  HTTPS: 'HTTPS inspection, root CA, trusted apps/domains and manual recording.',
  Lifecycle: 'Authentication, activation, setup shell, appliance lifecycle and advice overlays.',
  Network: 'DNS, DHCP, resolver mode, local DNS entries and network setup.',
  Protection: 'Filter lists, analysis, anonymization, Tor, malware/ad/tracker protection and doctor checks.',
  System: 'Registration, updates, diagnostics, tasks, events, backups, locale and system operations.',
  VPN: 'Mobile VPN, VPN home, provider profiles, remote dashboard and connection checks.'
};

function fromLegacyModern(groupId: string, tag: string, endpoints: readonly LegacyModernEndpoint[]): readonly SourceOperation[] {
  return endpoints.map((endpoint) => ({
    groupId,
    tag,
    method: endpoint.method,
    legacyPath: endpoint.legacy,
    modernPath: endpoint.modern,
    purpose: endpoint.purpose
  }));
}

function fromLegacyPathModernPath(groupId: string, tag: string, endpoints: readonly LegacyPathModernPathEndpoint[]): readonly SourceOperation[] {
  return endpoints.map((endpoint) => ({
    groupId,
    tag,
    method: endpoint.method,
    legacyPath: endpoint.legacyPath,
    modernPath: endpoint.modernPath,
    purpose: endpoint.purpose
  }));
}

export function getModernUiApiOperations(): readonly SourceOperation[] {
  return [
    ...fromLegacyModern('block-pages', 'Block pages', blockPageEndpointMap),
    ...fromLegacyPathModernPath('devices', 'Devices', deviceCenterEndpoints),
    ...fromLegacyModern('family', 'Family', familyCenterEndpoints),
    ...fromLegacyModern('https', 'HTTPS', httpsCenterEndpoints),
    ...fromLegacyModern('lifecycle', 'Lifecycle', lifecycleEndpointMap),
    ...fromLegacyModern('network', 'Network', networkCenterEndpoints),
    ...fromLegacyModern('protection', 'Protection', protectionCenterEndpoints),
    ...fromLegacyModern('system', 'System', systemAdminCenterEndpoints),
    ...fromLegacyModern('vpn', 'VPN', vpnMobileCenterEndpoints)
  ];
}

function toHttpMethod(method: string): OpenApiHttpMethod {
  const normalized = method.toLowerCase();
  if (normalized === 'get' || normalized === 'put' || normalized === 'post' || normalized === 'delete' || normalized === 'patch') {
    return normalized;
  }
  throw new Error(`Unsupported OpenAPI method: ${method}`);
}

function toPascalCase(input: string): string {
  return input
    .replace(/\{([^}]+)\}/g, ' $1 ')
    .replace(/[^a-zA-Z0-9]+/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join('');
}

function toOperationId(operation: SourceOperation): string {
  const base = `${operation.method.toLowerCase()}${toPascalCase(operation.groupId)}${toPascalCase(operation.modernPath.replace(/^\/api\/v1\//, ''))}`;
  return base.charAt(0).toLowerCase() + base.slice(1);
}

function getPathParameters(path: string): readonly OpenApiParameter[] | undefined {
  const names = [...path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]);
  if (names.length === 0) {
    return undefined;
  }
  return Array.from(new Set(names)).map((name) => ({
    name,
    in: 'path' as const,
    required: true as const,
    schema: { type: 'string' as const }
  }));
}

const liveBridgeOperations = new Set([
  'GET /api/v1/devices',
  'GET /api/v1/devices/{id}',
  'PUT /api/v1/devices/{id}',
  'DELETE /api/v1/devices/{id}',
  'PUT /api/v1/devices/{id}/reset',
  'GET /api/v1/devices/discovery/scan-availability',
  'GET /api/v1/devices/discovery/scanning-interval',
  'POST /api/v1/devices/discovery/scanning-interval',
  'POST /api/v1/devices/discovery/scan',
  'GET /api/v1/devices/discovery/auto-enable-new-devices',
  'POST /api/v1/devices/discovery/auto-enable-new-devices',
  'GET /api/v1/lifecycle/appliance/status',
  'POST /api/v1/lifecycle/appliance/reboot',
  'POST /api/v1/lifecycle/appliance/shutdown',
  'POST /api/v1/lifecycle/appliance/reboot-on-error',
  'POST /api/v1/lifecycle/appliance/shutdown-on-error',
  'GET /api/v1/lifecycle/auth/init-token/{context}',
  'POST /api/v1/lifecycle/auth/login/{context}',
  'GET /api/v1/lifecycle/auth/renew/{context}',
  'GET /api/v1/lifecycle/auth/login-wait',
  'POST /api/v1/lifecycle/password-reset/initiate',
  'POST /api/v1/lifecycle/password-reset/execute',
  'POST /api/v1/lifecycle/password-reset/cancel',
  'GET /api/v1/lifecycle/setup/tos',
  'GET /api/v1/lifecycle/registration',
  'POST /api/v1/lifecycle/registration',
  'DELETE /api/v1/lifecycle/registration',
  'GET /api/v1/lifecycle/splash',
  'POST /api/v1/lifecycle/splash'
]);

function getBackendStatus(operation: SourceOperation): OpenApiOperation['x-backend-status'] {
  return liveBridgeOperations.has(`${operation.method.toUpperCase()} ${operation.modernPath}`) ? 'live-bridge' : 'contract-only';
}

function toOpenApiOperation(operation: SourceOperation, usedOperationIds: Set<string>): OpenApiOperation {
  const operationId = toOperationId(operation);
  if (usedOperationIds.has(operationId)) {
    throw new Error(`Duplicate OpenAPI operationId: ${operationId}`);
  }
  usedOperationIds.add(operationId);

  const parameters = getPathParameters(operation.modernPath);
  return {
    tags: [operation.tag],
    summary: operation.purpose,
    operationId,
    ...(parameters ? { parameters } : {}),
    responses: {
      '200': { description: 'Successful response from the modern eBlocker API bridge.' }
    },
    'x-legacy-path': operation.legacyPath,
    'x-backend-status': getBackendStatus(operation)
  };
}

export function buildModernUiOpenApiDocument(): OpenApiDocument {
  const operations = getModernUiApiOperations();
  const usedOperationIds = new Set<string>();
  const paths: OpenApiDocument['paths'] = {};

  for (const operation of operations) {
    if (!operation.modernPath.startsWith('/api/v1/')) {
      throw new Error(`Modern API target must stay under /api/v1: ${operation.modernPath}`);
    }

    const method = toHttpMethod(operation.method);
    paths[operation.modernPath] = paths[operation.modernPath] ?? {};
    if (paths[operation.modernPath][method]) {
      throw new Error(`Duplicate OpenAPI operation for ${operation.method} ${operation.modernPath}`);
    }
    paths[operation.modernPath][method] = toOpenApiOperation(operation, usedOperationIds);
  }

  const tagNames = Array.from(new Set(operations.map((operation) => operation.tag))).sort();

  return {
    openapi: '3.1.0',
    info: {
      title: 'eBlocker Modern UI API',
      version: '1.0.0-preview',
      description: 'OpenAPI contract for the modern eBlocker UI /api/v1 bridge. Operations retain x-legacy-path traceability to the AngularJS-era APIs and x-backend-status to distinguish live backend bridges from contract-only targets.'
    },
    servers: [{ url: '/', description: 'Current eBlocker appliance origin' }],
    tags: tagNames.map((name) => ({ name, description: tagDescriptions[name] ?? `${name} endpoints` })),
    paths
  };
}

export function getOpenApiOperationCount(document: Pick<OpenApiDocument, 'paths'>): number {
  return Object.values(document.paths).reduce((count, pathItem) => count + Object.keys(pathItem).length, 0);
}
