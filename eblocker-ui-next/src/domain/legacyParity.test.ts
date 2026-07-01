import { describe, expect, it } from 'vitest';
import {
  LEGACY_ROUTE_FILE_COUNT,
  LEGACY_UI_STATE_COUNT,
  getCriticalParityGroups,
  getGroupsByStage,
  getLegacyParityCoveragePercent,
  getLegacyParityTotals,
  getMappedLegacyStateCount,
  legacyParityGroups
} from './legacyParity';

describe('legacy AngularJS to modern UI parity matrix', () => {
  it('is generated from the known legacy route surface', () => {
    expect(LEGACY_ROUTE_FILE_COUNT).toBe(6);
    expect(LEGACY_UI_STATE_COUNT).toBeGreaterThan(100);
    expect(legacyParityGroups.length).toBeGreaterThanOrEqual(10);
  });

  it('maps every legacy AngularJS state to a modern UI target group', () => {
    expect(getMappedLegacyStateCount()).toBe(LEGACY_UI_STATE_COUNT);
    expect(getLegacyParityCoveragePercent()).toBe(100);
    expect(legacyParityGroups.every((group) => group.targetSurfaces.length > 0)).toBe(true);
  });

  it('keeps the old critical product areas visible while the new UI replaces them', () => {
    expect(getCriticalParityGroups().map((group) => group.id)).toEqual([
      'dashboard-controlbar',
      'devices',
      'protection-privacy',
      'network-dns'
    ]);
    expect(getGroupsByStage('api-needed').length).toBeGreaterThanOrEqual(4);
  });

  it('provides summary totals for the migration dashboard', () => {
    expect(getLegacyParityTotals()).toEqual({
      totalStates: LEGACY_UI_STATE_COUNT,
      mappedStates: LEGACY_UI_STATE_COUNT,
      coveragePercent: 100,
      prototypeGroups: 3,
      apiNeededGroups: 4,
      mappedOnlyGroups: 3
    });
  });
});
