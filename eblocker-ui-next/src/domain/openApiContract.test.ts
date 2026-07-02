import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  buildModernUiOpenApiDocument,
  getOpenApiOperationCount,
  OPEN_API_PUBLIC_PATH,
  SWAGGER_PUBLIC_PATH
} from './openApiContract';

describe('modern eBlocker OpenAPI / Swagger contract', () => {
  it('builds an OpenAPI 3.1 document from every modern API mapping', () => {
    const document = buildModernUiOpenApiDocument();

    expect(document.openapi).toBe('3.1.0');
    expect(document.info.title).toBe('eBlocker Modern UI API');
    expect(document.servers).toEqual([{ url: '/', description: 'Current eBlocker appliance origin' }]);
    expect(getOpenApiOperationCount(document)).toBe(210);
  });

  it('keeps every operation unique, tagged and bound to /api/v1 targets with legacy traceability', () => {
    const document = buildModernUiOpenApiDocument();
    const operationIds = new Set<string>();
    const methods = new Set(['get', 'put', 'post', 'delete', 'patch']);

    for (const [path, pathItem] of Object.entries(document.paths)) {
      expect(path.startsWith('/api/v1/')).toBe(true);
      for (const [method, operation] of Object.entries(pathItem)) {
        expect(methods.has(method)).toBe(true);
        expect(operation.operationId).toMatch(/^[a-z][a-zA-Z0-9]*$/);
        expect(operationIds.has(operation.operationId)).toBe(false);
        operationIds.add(operation.operationId);
        expect(operation.tags.length).toBeGreaterThan(0);
        expect(operation.summary.length).toBeGreaterThan(0);
        expect(operation['x-legacy-path']).toMatch(/^\//);
        expect(['live-bridge', 'contract-only']).toContain((operation as unknown as { readonly 'x-backend-status': string })['x-backend-status']);
        expect(operation.responses).toHaveProperty('200');
      }
    }

    expect(operationIds.size).toBe(210);
  });

  it('marks the currently implemented backend bridge operations explicitly', () => {
    const document = buildModernUiOpenApiDocument();
    const statuses = Object.values(document.paths).flatMap((pathItem) => Object.values(pathItem).map((operation) => operation?.['x-backend-status']));

    expect(statuses.filter((status) => status === 'live-bridge')).toHaveLength(29);
    expect(document.paths['/api/v1/devices']?.get?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/devices/{id}']?.get?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/devices/{id}']?.put?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/devices/{id}']?.delete?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/devices/{id}/reset']?.put?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/lifecycle/appliance/status']?.get?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/lifecycle/appliance/reboot']?.post?.['x-backend-status']).toBe('live-bridge');
    expect(document.paths['/api/v1/lifecycle/appliance/shutdown']?.post?.['x-backend-status']).toBe('live-bridge');
  });

  it('ships the OpenAPI JSON and Swagger HTML as static public artifacts', () => {
    const openApiPath = resolve(process.cwd(), OPEN_API_PUBLIC_PATH);
    const swaggerPath = resolve(process.cwd(), SWAGGER_PUBLIC_PATH);

    expect(existsSync(openApiPath)).toBe(true);
    expect(existsSync(swaggerPath)).toBe(true);

    const spec = JSON.parse(readFileSync(openApiPath, 'utf8'));
    expect(getOpenApiOperationCount(spec)).toBe(210);
    expect(readFileSync(swaggerPath, 'utf8')).toContain('/openapi/eblocker-modern-api.openapi.json');
  });
});
