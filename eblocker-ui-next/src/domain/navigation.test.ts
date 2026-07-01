import { describe, expect, it } from 'vitest';
import {
  countItemsByStatus,
  getAllModernNavigationItems,
  getLegacyFallbackPath,
  modernNavigationSections
} from './navigation';

describe('modern UI navigation inventory', () => {
  it('defines every migration item with a legacy fallback and target API prefix', () => {
    const items = getAllModernNavigationItems();

    expect(items.length).toBeGreaterThan(0);
    for (const item of items) {
      expect(item.id).toMatch(/^[a-z0-9-]+$/);
      expect(item.title.length).toBeGreaterThan(2);
      expect(getLegacyFallbackPath(item)).toMatch(/^\//);
      expect(item.targetApiPrefix).toMatch(/^\/api\/v1\//);
    }
  });

  it('keeps the first shipped UI as a foundation shell, not a fake complete rewrite', () => {
    expect(countItemsByStatus('foundation')).toBeGreaterThanOrEqual(1);
    expect(countItemsByStatus('legacy-bridge')).toBeGreaterThanOrEqual(1);
  });

  it('groups navigation entries into stable product sections', () => {
    expect(modernNavigationSections.map((section) => section.id)).toEqual([
      'overview',
      'protection',
      'family'
    ]);
  });
});
