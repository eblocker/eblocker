import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { createRequire } from 'node:module';

const projectRoot = resolve(dirname(new URL(import.meta.url).pathname), '..');
const domainRoot = join(projectRoot, 'src', 'domain');
const publicRoot = join(projectRoot, 'public');
const openApiDir = join(publicRoot, 'openapi');
const swaggerDir = join(publicRoot, 'swagger');
const swaggerAssetDir = join(publicRoot, 'swagger-ui');

const centerFiles = [
  { file: 'blockPagesCenter.ts', groupId: 'block-pages', tag: 'Block pages' },
  { file: 'deviceCenter.ts', groupId: 'devices', tag: 'Devices' },
  { file: 'familyCenter.ts', groupId: 'family', tag: 'Family' },
  { file: 'httpsCenter.ts', groupId: 'https', tag: 'HTTPS' },
  { file: 'lifecycleCenter.ts', groupId: 'lifecycle', tag: 'Lifecycle' },
  { file: 'networkCenter.ts', groupId: 'network', tag: 'Network' },
  { file: 'protectionCenter.ts', groupId: 'protection', tag: 'Protection' },
  { file: 'systemAdminCenter.ts', groupId: 'system', tag: 'System' },
  { file: 'vpnMobileCenter.ts', groupId: 'vpn', tag: 'VPN' }
];

const tagDescriptions = {
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

function extractStringField(body, names) {
  for (const name of names) {
    const match = body.match(new RegExp(`${name}: '((?:\\\\'|[^'])*)'`));
    if (match) {
      return match[1].replace(/\\'/g, "'");
    }
  }
  return undefined;
}

function extractOperationsFromFile(config) {
  const source = readFileSync(join(domainRoot, config.file), 'utf8');
  const objects = [...source.matchAll(/\{[\s\S]*?method: '[^']+'[\s\S]*?purpose: '(?:\\'|[^'])*'[\s\S]*?\}/g)].map((match) => match[0]);
  return objects
    .map((body) => ({
      groupId: config.groupId,
      tag: config.tag,
      method: extractStringField(body, ['method']),
      legacyPath: extractStringField(body, ['legacyPath', 'legacy']),
      modernPath: extractStringField(body, ['modernPath', 'modern']),
      purpose: extractStringField(body, ['purpose'])
    }))
    .filter((operation) => operation.method && operation.legacyPath && operation.modernPath && operation.purpose);
}

function toPascalCase(input) {
  return input
    .replace(/\{([^}]+)\}/g, ' $1 ')
    .replace(/[^a-zA-Z0-9]+/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join('');
}

function toOperationId(operation) {
  const base = `${operation.method.toLowerCase()}${toPascalCase(operation.groupId)}${toPascalCase(operation.modernPath.replace(/^\/api\/v1\//, ''))}`;
  return base.charAt(0).toLowerCase() + base.slice(1);
}

function getPathParameters(path) {
  const names = [...path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]);
  if (names.length === 0) {
    return undefined;
  }
  return Array.from(new Set(names)).map((name) => ({
    name,
    in: 'path',
    required: true,
    schema: { type: 'string' }
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
  'POST /api/v1/lifecycle/appliance/shutdown'
]);

function getBackendStatus(operation) {
  return liveBridgeOperations.has(`${operation.method.toUpperCase()} ${operation.modernPath}`) ? 'live-bridge' : 'contract-only';
}

function buildDocument() {
  const operations = centerFiles.flatMap(extractOperationsFromFile);
  const usedOperationIds = new Set();
  const paths = {};

  for (const operation of operations) {
    if (!operation.modernPath.startsWith('/api/v1/')) {
      throw new Error(`Modern API target must stay under /api/v1: ${operation.modernPath}`);
    }
    const method = operation.method.toLowerCase();
    const operationId = toOperationId(operation);
    if (usedOperationIds.has(operationId)) {
      throw new Error(`Duplicate operationId ${operationId}`);
    }
    usedOperationIds.add(operationId);
    paths[operation.modernPath] = paths[operation.modernPath] ?? {};
    if (paths[operation.modernPath][method]) {
      throw new Error(`Duplicate operation ${operation.method} ${operation.modernPath}`);
    }
    const parameters = getPathParameters(operation.modernPath);
    paths[operation.modernPath][method] = {
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

  const tagNames = [...new Set(operations.map((operation) => operation.tag))].sort();
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

function writeSwaggerPage() {
  writeFileSync(join(swaggerDir, 'index.html'), `<!doctype html>
<html lang="de">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>eBlocker Modern API Swagger</title>
    <link rel="stylesheet" href="/swagger-ui/swagger-ui.css" />
    <style>
      body { margin: 0; background: #0b0f14; }
      .topbar { padding: 12px 18px; background: #111827; color: #f9fafb; font-family: system-ui, sans-serif; display: flex; gap: 16px; align-items: center; }
      .topbar a { color: #93c5fd; text-decoration: none; }
      #swagger-ui { background: #fff; }
    </style>
  </head>
  <body>
    <div class="topbar"><strong>eBlocker Modern API</strong><a href="/">Modern UI</a><a href="/openapi/eblocker-modern-api.openapi.json">OpenAPI JSON</a></div>
    <div id="swagger-ui"></div>
    <script src="/swagger-ui/swagger-ui-bundle.js"></script>
    <script src="/swagger-ui/swagger-ui-standalone-preset.js"></script>
    <script>
      window.ui = SwaggerUIBundle({
        url: '/openapi/eblocker-modern-api.openapi.json',
        dom_id: '#swagger-ui',
        deepLinking: true,
        presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
        layout: 'StandaloneLayout'
      });
    </script>
  </body>
</html>
`);
}

function copySwaggerAssets() {
  const require = createRequire(import.meta.url);
  const swaggerPackage = require.resolve('swagger-ui-dist/package.json');
  const sourceDir = dirname(swaggerPackage);
  for (const file of ['swagger-ui.css', 'swagger-ui-bundle.js', 'swagger-ui-standalone-preset.js']) {
    copyFileSync(join(sourceDir, file), join(swaggerAssetDir, file));
  }
}

mkdirSync(openApiDir, { recursive: true });
mkdirSync(swaggerDir, { recursive: true });
mkdirSync(swaggerAssetDir, { recursive: true });

const document = buildDocument();
writeFileSync(join(openApiDir, 'eblocker-modern-api.openapi.json'), `${JSON.stringify(document, null, 2)}\n`);
writeSwaggerPage();
copySwaggerAssets();

const operationCount = Object.values(document.paths).reduce((count, pathItem) => count + Object.keys(pathItem).length, 0);
console.log(`Generated OpenAPI contract with ${operationCount} operations and local Swagger UI assets.`);
if (operationCount !== 210) {
  throw new Error(`Expected 210 OpenAPI operations, got ${operationCount}`);
}
