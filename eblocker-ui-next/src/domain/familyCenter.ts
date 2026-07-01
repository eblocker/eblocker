export type FamilyEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
export type FamilyUserRole = 'CHILD' | 'PARENT' | 'OTHER';
export type RestrictionMode = 'blacklisting' | 'whitelisting';
export type FilterType = 'blacklist' | 'whitelist';
export type FilterUpdateStatus = 'READY' | 'UPDATING' | 'FAILED';

export interface FamilyEndpoint {
  readonly method: FamilyEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface FamilyUserRow {
  readonly id: number;
  readonly name: string;
  readonly role: FamilyUserRole;
  readonly profileId: number;
  readonly profileName: string;
  readonly age?: number;
  readonly containsPin: boolean;
  readonly standardUser: boolean;
  readonly deletable: boolean;
  readonly online: boolean;
  readonly assignedDevices: readonly string[];
  readonly operatingDevices: readonly string[];
  readonly hasContentRestrictions: boolean;
  readonly hasTimeRestrictions: boolean;
  readonly showSslDnsWarning: boolean;
}

export interface InternetAccessContingent {
  readonly id: number;
  readonly onDay: string;
  readonly fromMinutes: number;
  readonly tillMinutes: number;
  readonly label: string;
}

export interface WeeklyUsageRow {
  readonly day: string;
  readonly minutes: number;
  readonly label: string;
}

export interface FamilyProfileRow {
  readonly id: number;
  readonly name: string;
  readonly description: string;
  readonly builtin: boolean;
  readonly deletable: boolean;
  readonly assignedUsers: readonly string[];
  readonly controlmodeUrls: boolean;
  readonly controlmodeTime: boolean;
  readonly controlmodeMaxUsage: boolean;
  readonly internetAccessRestrictionMode: RestrictionMode;
  readonly accessibleSitesPackages: readonly number[];
  readonly inaccessibleSitesPackages: readonly number[];
  readonly internetAccessContingents: readonly InternetAccessContingent[];
  readonly bonusMinutesToday: number;
  readonly showSslWarningMessage: boolean;
}

export interface FamilyFilterListRow {
  readonly id: number;
  readonly name: string;
  readonly description: string;
  readonly filterType: FilterType;
  readonly providedByEblocker: boolean;
  readonly assignedProfiles: readonly string[];
  readonly lastUpdate: string;
  readonly updateStatus: FilterUpdateStatus;
  readonly format: 'DOMAINS' | 'URLS' | 'EASYLIST';
  readonly domains: number;
  readonly deletable: boolean;
}

export const familyCenterLegacyStates = [
  'parentalcontrol',
  'parentalcontrolstate',
  'users',
  'user-details',
  'userprofiledetails',
  'blacklists',
  'whitelists',
  'blacklistdetails',
  'whitelistdetails'
] as const;

export const familyCenterEndpoints: readonly FamilyEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/users', modern: '/api/v1/family/users', purpose: 'Benutzer laden' },
  { method: 'POST', legacy: '/api/adminconsole/users', modern: '/api/v1/family/users', purpose: 'Benutzer anlegen' },
  { method: 'PUT', legacy: '/api/adminconsole/users', modern: '/api/v1/family/users/{id}', purpose: 'Benutzer aktualisieren' },
  { method: 'DELETE', legacy: '/api/adminconsole/users/{id}', modern: '/api/v1/family/users/{id}', purpose: 'Benutzer löschen' },
  { method: 'POST', legacy: '/api/adminconsole/users/all', modern: '/api/v1/family/users/delete', purpose: 'Benutzer gesammelt löschen' },
  { method: 'GET', legacy: '/api/adminconsole/users/unique?name={name}', modern: '/api/v1/family/users/unique?name={name}', purpose: 'Benutzername prüfen' },
  { method: 'POST', legacy: '/api/adminconsole/users/{id}/pin', modern: '/api/v1/family/users/{id}/pin', purpose: 'PIN setzen' },
  { method: 'DELETE', legacy: '/api/adminconsole/users/{id}/pin', modern: '/api/v1/family/users/{id}/pin', purpose: 'PIN zurücksetzen' },
  { method: 'PUT', legacy: '/api/adminconsole/users/dashboard/update/{id}', modern: '/api/v1/family/users/{id}/dashboard-view', purpose: 'Dashboard-Anzeige aktualisieren' },
  { method: 'PUT', legacy: '/api/adminconsole/users/dashboard/updateall', modern: '/api/v1/family/users/dashboard-view/defaults', purpose: 'Systemnutzer-Dashboard aktualisieren' },
  { method: 'GET', legacy: '/api/adminconsole/userprofiles', modern: '/api/v1/family/profiles', purpose: 'Profile laden' },
  { method: 'POST', legacy: '/api/adminconsole/userprofiles', modern: '/api/v1/family/profiles', purpose: 'Profil anlegen' },
  { method: 'PUT', legacy: '/api/adminconsole/userprofiles', modern: '/api/v1/family/profiles/{id}', purpose: 'Profil speichern' },
  { method: 'DELETE', legacy: '/api/adminconsole/userprofiles/{id}', modern: '/api/v1/family/profiles/{id}', purpose: 'Profil löschen' },
  { method: 'POST', legacy: '/api/adminconsole/userprofiles/all', modern: '/api/v1/family/profiles/delete', purpose: 'Profile gesammelt löschen' },
  { method: 'GET', legacy: '/api/adminconsole/userprofiles/unique?name={name}', modern: '/api/v1/family/profiles/unique?name={name}', purpose: 'Profilname prüfen' },
  { method: 'GET', legacy: '/api/adminconsole/userprofiles/updates', modern: '/api/v1/family/profiles/updates', purpose: 'Profil-Update-Status laden' },
  { method: 'POST', legacy: '/api/adminconsole/userprofile/bonustime/{profileId}', modern: '/api/v1/family/profiles/{profileId}/bonus-time', purpose: 'Bonuszeit hinzufügen' },
  { method: 'DELETE', legacy: '/api/adminconsole/userprofile/bonustime/{profileId}', modern: '/api/v1/family/profiles/{profileId}/bonus-time', purpose: 'Bonuszeit zurücksetzen' },
  { method: 'GET', legacy: '/api/adminconsole/filterlists', modern: '/api/v1/family/filter-lists', purpose: 'Filterlisten laden' },
  { method: 'GET', legacy: '/api/blockers/', modern: '/api/v1/family/filter-lists/blockers', purpose: 'Blocker/Listen nach Typ laden' },
  { method: 'GET', legacy: '/api/blockers/{id}', modern: '/api/v1/family/filter-lists/{id}', purpose: 'Filterliste laden' },
  { method: 'POST', legacy: '/api/blockers/', modern: '/api/v1/family/filter-lists', purpose: 'Filterliste anlegen' },
  { method: 'PUT', legacy: '/api/blockers/{id}', modern: '/api/v1/family/filter-lists/{id}', purpose: 'Filterliste aktualisieren' },
  { method: 'DELETE', legacy: '/api/blockers/{id}', modern: '/api/v1/family/filter-lists/{id}', purpose: 'Filterliste löschen' }
];

export const weeklyUsageRows: readonly WeeklyUsageRow[] = [
  { day: 'Montag', minutes: 90, label: 'PARENTAL_CONTROL_DAY_1' },
  { day: 'Dienstag', minutes: 90, label: 'PARENTAL_CONTROL_DAY_2' },
  { day: 'Mittwoch', minutes: 90, label: 'PARENTAL_CONTROL_DAY_3' },
  { day: 'Donnerstag', minutes: 90, label: 'PARENTAL_CONTROL_DAY_4' },
  { day: 'Freitag', minutes: 120, label: 'PARENTAL_CONTROL_DAY_5' },
  { day: 'Samstag', minutes: 180, label: 'PARENTAL_CONTROL_DAY_6' },
  { day: 'Sonntag', minutes: 180, label: 'PARENTAL_CONTROL_DAY_7' }
];

export const familyUsers: readonly FamilyUserRow[] = [
  { id: 1, name: 'Shedowe', role: 'PARENT', profileId: 10, profileName: 'Erwachsene', containsPin: true, standardUser: false, deletable: false, online: true, assignedDevices: ['MacBook Pro'], operatingDevices: ['MacBook Pro'], hasContentRestrictions: false, hasTimeRestrictions: false, showSslDnsWarning: false },
  { id: 2, name: 'Kind', role: 'CHILD', profileId: 11, profileName: 'Kinderprofil', age: 10, containsPin: true, standardUser: false, deletable: true, online: true, assignedDevices: ['Kinder-Tablet'], operatingDevices: ['Kinder-Tablet'], hasContentRestrictions: true, hasTimeRestrictions: true, showSslDnsWarning: true },
  { id: 3, name: 'Gast', role: 'OTHER', profileId: 12, profileName: 'Gäste', containsPin: false, standardUser: false, deletable: true, online: false, assignedDevices: ['Gäste-Handy'], operatingDevices: [], hasContentRestrictions: true, hasTimeRestrictions: false, showSslDnsWarning: false },
  { id: 999, name: 'Standardnutzer', role: 'OTHER', profileId: 13, profileName: 'Standard', containsPin: false, standardUser: true, deletable: false, online: true, assignedDevices: ['Unzugeordnetes Gerät'], operatingDevices: [], hasContentRestrictions: false, hasTimeRestrictions: false, showSslDnsWarning: false }
];

export const familyProfilesModern: readonly FamilyProfileRow[] = [
  { id: 10, name: 'Erwachsene', description: 'Keine Kinderschutz-Einschränkung', builtin: true, deletable: false, assignedUsers: ['Shedowe'], controlmodeUrls: false, controlmodeTime: false, controlmodeMaxUsage: false, internetAccessRestrictionMode: 'blacklisting', accessibleSitesPackages: [], inaccessibleSitesPackages: [], internetAccessContingents: [], bonusMinutesToday: 0, showSslWarningMessage: false },
  { id: 11, name: 'Kinderprofil', description: 'Blacklists, Zeitfenster und Tageslimit', builtin: false, deletable: false, assignedUsers: ['Kind'], controlmodeUrls: true, controlmodeTime: true, controlmodeMaxUsage: true, internetAccessRestrictionMode: 'blacklisting', accessibleSitesPackages: [203], inaccessibleSitesPackages: [201, 202], internetAccessContingents: [
    { id: 0, onDay: 'Montag-Freitag', fromMinutes: 900, tillMinutes: 1200, label: 'PARENTAL_CONTROL_DAY_8' },
    { id: 1, onDay: 'Samstag', fromMinutes: 600, tillMinutes: 1320, label: 'PARENTAL_CONTROL_DAY_6' },
    { id: 2, onDay: 'Sonntag', fromMinutes: 600, tillMinutes: 1260, label: 'PARENTAL_CONTROL_DAY_7' }
  ], bonusMinutesToday: 30, showSslWarningMessage: true },
  { id: 12, name: 'Gäste', description: 'Nur erlaubte Seiten', builtin: false, deletable: false, assignedUsers: ['Gast'], controlmodeUrls: true, controlmodeTime: true, controlmodeMaxUsage: false, internetAccessRestrictionMode: 'whitelisting', accessibleSitesPackages: [203, 204], inaccessibleSitesPackages: [], internetAccessContingents: [
    { id: 0, onDay: 'Samstag-Sonntag', fromMinutes: 480, tillMinutes: 1320, label: 'PARENTAL_CONTROL_DAY_9' },
    { id: 1, onDay: 'Montag-Freitag', fromMinutes: 960, tillMinutes: 1200, label: 'PARENTAL_CONTROL_DAY_8' }
  ], bonusMinutesToday: 0, showSslWarningMessage: false },
  { id: 13, name: 'Standard', description: 'Fallback für unzugeordnete Geräte', builtin: true, deletable: false, assignedUsers: ['Standardnutzer'], controlmodeUrls: false, controlmodeTime: false, controlmodeMaxUsage: false, internetAccessRestrictionMode: 'blacklisting', accessibleSitesPackages: [], inaccessibleSitesPackages: [], internetAccessContingents: [], bonusMinutesToday: 0, showSslWarningMessage: false }
];

export const familyFilterLists: readonly FamilyFilterListRow[] = [
  { id: 201, name: 'Jugendschutz Basis', description: 'Gewalt, Erwachsenen-Inhalte und Malware', filterType: 'blacklist', providedByEblocker: true, assignedProfiles: ['Kinderprofil'], lastUpdate: 'heute 04:12', updateStatus: 'READY', format: 'DOMAINS', domains: 98214, deletable: false },
  { id: 202, name: 'Social Media Zeitfresser', description: 'Ablenkende Netzwerke', filterType: 'blacklist', providedByEblocker: false, assignedProfiles: ['Kinderprofil'], lastUpdate: 'gestern 21:00', updateStatus: 'UPDATING', format: 'DOMAINS', domains: 128, deletable: false },
  { id: 203, name: 'Schule erlaubt', description: 'Lernplattformen und Recherche', filterType: 'whitelist', providedByEblocker: false, assignedProfiles: ['Kinderprofil', 'Gäste'], lastUpdate: 'heute 08:02', updateStatus: 'READY', format: 'DOMAINS', domains: 42, deletable: false },
  { id: 204, name: 'Gäste erlaubt', description: 'Captive-Portal und lokale Dienste', filterType: 'whitelist', providedByEblocker: false, assignedProfiles: ['Gäste'], lastUpdate: 'heute 08:30', updateStatus: 'READY', format: 'URLS', domains: 12, deletable: false },
  { id: 205, name: 'Experiment leer', description: 'Nicht zugeordnet, löschbar', filterType: 'whitelist', providedByEblocker: false, assignedProfiles: [], lastUpdate: 'nie', updateStatus: 'READY', format: 'DOMAINS', domains: 0, deletable: true }
];

export const familyCapabilities = [
  'Benutzer mit Rolle, PIN, Geburtstag, Online-Status und Gerätezuordnung verwalten',
  'Profile mit URL-Restriktionen, Zeitfenstern, Tageslimits und Bonuszeit abbilden',
  'Blacklists und Whitelists mit Domain-/URL-Format, Update-Status und Profilzuordnung anzeigen',
  'DNS/HTTPS-Warnungen bei Profilen und Benutzern sichtbar machen',
  'Legacy-Dialoge für Zeitfenster, Tagesnutzung und Zugriffslisten als moderne Inline-Workflows ersetzen'
] as const;

export function getFamilyCenterTotals(): {
  legacyStates: number;
  endpoints: number;
  users: number;
  profiles: number;
  filterLists: number;
  activeTimeWindows: number;
} {
  return {
    legacyStates: familyCenterLegacyStates.length,
    endpoints: familyCenterEndpoints.length,
    users: familyUsers.length,
    profiles: familyProfilesModern.length,
    filterLists: familyFilterLists.length,
    activeTimeWindows: familyProfilesModern.reduce((sum, profile) => sum + profile.internetAccessContingents.length, 0)
  };
}
