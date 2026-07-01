import { describe, expect, it } from 'vitest';
import { defaultLocale, messages, t } from './messages';

describe('modern UI translations', () => {
  it('uses German as the preview locale for Shedowe testing', () => {
    expect(defaultLocale).toBe('de');
    expect(t('app.title')).toBe('eBlocker Kontrollzentrum');
  });

  it('keeps German and English message keys in sync', () => {
    const deKeys = Object.keys(messages.de).sort();
    const enKeys = Object.keys(messages.en).sort();
    expect(deKeys).toEqual(enKeys);
  });

  it('does not silently fall back to the key for known labels', () => {
    expect(t('nav.dashboard')).not.toBe('nav.dashboard');
    expect(t('section.devices.title')).not.toBe('section.devices.title');
    expect(t('action.openLegacy')).not.toBe('action.openLegacy');
  });
});
