import { describe, expect, it } from 'vitest';
import {
  familyCapabilities,
  familyCenterEndpoints,
  familyCenterLegacyStates,
  familyFilterLists,
  familyProfilesModern,
  familyUsers,
  getFamilyCenterTotals,
  weeklyUsageRows
} from './familyCenter';

describe('family center legacy parity', () => {
  it('covers every legacy parental-control state', () => {
    expect(familyCenterLegacyStates).toEqual([
      'parentalcontrol',
      'parentalcontrolstate',
      'users',
      'user-details',
      'userprofiledetails',
      'blacklists',
      'whitelists',
      'blacklistdetails',
      'whitelistdetails'
    ]);
  });

  it('maps UserService, UserProfileService, FilterService and BlockerService endpoints to family /api/v1 targets', () => {
    expect(familyCenterEndpoints).toHaveLength(25);
    expect(familyCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/family/'))).toBe(true);
    expect(familyCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/api/adminconsole/users',
      '/api/adminconsole/users/{id}/pin',
      '/api/adminconsole/users/dashboard/update/{id}',
      '/api/adminconsole/userprofiles',
      '/api/adminconsole/userprofiles/updates',
      '/api/adminconsole/userprofile/bonustime/{profileId}',
      '/api/adminconsole/filterlists',
      '/api/blockers/',
      '/api/blockers/{id}'
    ]));
  });

  it('keeps users, roles, assigned devices, PIN and DNS/SSL warnings visible', () => {
    expect(familyUsers).toHaveLength(4);
    expect(familyUsers.some((user) => user.role === 'CHILD' && user.containsPin)).toBe(true);
    expect(familyUsers.some((user) => user.standardUser && !user.deletable)).toBe(true);
    expect(familyUsers.some((user) => user.showSslDnsWarning)).toBe(true);
    expect(familyUsers.map((user) => user.assignedDevices).flat()).toContain('Kinder-Tablet');
  });

  it('keeps profile restrictions, time windows, max-usage days, bonus time and lists represented', () => {
    expect(familyProfilesModern).toHaveLength(4);
    expect(familyProfilesModern.some((profile) => profile.controlmodeUrls && profile.internetAccessRestrictionMode === 'blacklisting')).toBe(true);
    expect(familyProfilesModern.some((profile) => profile.controlmodeTime && profile.internetAccessContingents.length > 0)).toBe(true);
    expect(weeklyUsageRows).toHaveLength(7);
    expect(weeklyUsageRows.every((row) => row.minutes > 0)).toBe(true);
    expect(familyProfilesModern.some((profile) => profile.bonusMinutesToday > 0)).toBe(true);
  });

  it('keeps blacklists and whitelists with assignment/deletable/update-state metadata', () => {
    expect(familyFilterLists).toHaveLength(5);
    expect(familyFilterLists.some((list) => list.filterType === 'blacklist' && list.assignedProfiles.length > 0)).toBe(true);
    expect(familyFilterLists.some((list) => list.filterType === 'whitelist' && list.deletable)).toBe(true);
    expect(familyFilterLists.map((list) => list.updateStatus)).toEqual(expect.arrayContaining(['READY', 'UPDATING']));
  });

  it('summarizes the center and documents migrated capabilities', () => {
    expect(getFamilyCenterTotals()).toEqual({
      legacyStates: 9,
      endpoints: 25,
      users: 4,
      profiles: 4,
      filterLists: 5,
      activeTimeWindows: 5
    });
    expect(familyCapabilities).toEqual(expect.arrayContaining([
      'Benutzer mit Rolle, PIN, Geburtstag, Online-Status und Gerätezuordnung verwalten',
      'Profile mit URL-Restriktionen, Zeitfenstern, Tageslimits und Bonuszeit abbilden',
      'Blacklists und Whitelists mit Domain-/URL-Format, Update-Status und Profilzuordnung anzeigen',
      'DNS/HTTPS-Warnungen bei Profilen und Benutzern sichtbar machen',
      'Legacy-Dialoge für Zeitfenster, Tagesnutzung und Zugriffslisten als moderne Inline-Workflows ersetzen'
    ]));
  });
});
