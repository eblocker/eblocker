export type LegacyParityStage = 'mapped' | 'prototype' | 'api-needed' | 'ready';
export type LegacyParityPriority = 'critical' | 'high' | 'medium' | 'low';

export interface LegacyParityGroup {
  readonly id: string;
  readonly title: string;
  readonly legacyDescription: string;
  readonly modernCoverage: string;
  readonly legacyStateNames: readonly string[];
  readonly legacyModules: readonly string[];
  readonly targetSurfaces: readonly string[];
  readonly priority: LegacyParityPriority;
  readonly stage: LegacyParityStage;
  readonly stageLabel: string;
}

export const LEGACY_ROUTE_FILE_COUNT = 6;
export const LEGACY_UI_STATE_COUNT = 109;

export const legacyParityGroups: readonly LegacyParityGroup[] = [
  {
    id: "dashboard-controlbar",
    title: "Dashboard & Controlbar",
    legacyDescription: "Dashboard-Karten, aktuelles Gerät, Konsolen-Sprung, Controlbar-Status und Remote-Dashboard-Verhalten.",
    modernCoverage: "Dashboard, Gerätefokus, Empfehlungen, Schnellaktionen und Controlbar/Command-Bar sind im modernen Dashboard sichtbar umgesetzt.",
    legacyStateNames: ["home", "reset", "status", "print", "app", "main", "app", "action", "main", "appState", "mainState"],
    legacyModules: ["controlbar", "dashboard", "settings"],
    targetSurfaces: ["#dashboard", "/api/v1/dashboard", "/api/v1/controlbar"],
    priority: "critical" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "devices",
    title: "Geräte & Clients",
    legacyDescription: "Geräteliste, Discovery, Details, Pausen-/Schutz-/Profilzustand und Aktionen pro Gerät.",
    modernCoverage: "Gerätecenter mit Discovery, Detailtabs, Pausen-/Schutz-/Profilzustand, Aktionen und DeviceService-Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["devices", "devicesstate", "deviceslist", "devicesdiscovery", "devicedetails"],
    legacyModules: ["settings"],
    targetSurfaces: ["#devices", "/api/v1/devices"],
    priority: "critical" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "family-parental-control",
    title: "Familie & Kinderschutz",
    legacyDescription: "Benutzer, Profile, Online-Zeitpläne, Black-/Whitelists und profilspezifische Einschränkungen.",
    modernCoverage: "Family-Center mit Benutzern, Profilen, Wochenzeiten, Filterlisten, Rollen und Parental-Control-Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["parentalcontrol", "parentalcontrolstate", "users", "user-details", "userprofiledetails", "blacklists", "whitelists", "blacklistdetails", "whitelistdetails"],
    legacyModules: ["settings"],
    targetSurfaces: ["#family", "/api/v1/parental-control"],
    priority: "high" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "protection-privacy",
    title: "Schutz, Filter & Privatsphäre",
    legacyDescription: "Filteranalyse, Block-/Freigabelisten, Anonymisierung, Tor, Malware-/Werbe-/Tracker-Blocker und erweiterte Schutzeinstellungen.",
    modernCoverage: "Protection-Center mit Filterlisten, Analyse/Doctor, Tor/Anonymisierung, Malware/Werbe/Tracker-Blocker und Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["anonymization", "anonymizationstate", "tor", "filter", "filterstate", "filteroverview", "filter-details", "advancedsettings", "filteranalysis", "analysisdetails", "doctor"],
    legacyModules: ["settings"],
    targetSurfaces: ["#protection", "/api/v1/protection", "/api/v1/dns"],
    priority: "critical" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "https-certificates",
    title: "HTTPS & Zertifikate",
    legacyDescription: "HTTPS-Assistent/-Status, Root-CA, vertrauenswürdige Apps/Domains, SSL-Fehler und manuelle Aufzeichnung.",
    modernCoverage: "HTTPS-Center mit Wizard/Status, Root-CA, Trusted Apps/Domains, SSL-Fails, manueller Aufzeichnung und Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["https", "sslstate", "sslstatus", "sslcertificate", "sslfails", "trustedapps", "trustedappsdetails", "trusteddomains", "manualrecording", "https"],
    legacyModules: ["dashboard", "settings"],
    targetSurfaces: ["#https", "/api/v1/ssl"],
    priority: "high" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "network-dns",
    title: "Netzwerk, DNS & DHCP",
    legacyDescription: "DNS-Status, DNS-Server/lokale Einträge, Netzwerk-IPv4/IPv6-Einstellungen, Setup-Assistent und Resolver-Zustand.",
    modernCoverage: "Network/DNS-Center mit DNS-Status, Servern, lokalen Einträgen, IPv4/IPv6, Wizard, Resolver-Modi und Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["dns", "dnsstate", "dnsstatus", "dnsserver", "dnslocal", "network", "networksettings", "networksettingsip6", "network-wizard"],
    legacyModules: ["settings"],
    targetSurfaces: ["#network", "/api/v1/network", "/api/v1/dns"],
    priority: "critical" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "vpn-mobile",
    title: "VPN, Mobile & Remote",
    legacyDescription: "VPN Home, Mobile-Setup-Assistent, OpenVPN-Kompatibilität, Remote-Dashboard und künftiger WireGuard-Pfad.",
    modernCoverage: "VPN/Mobile/Remote-Center mit OpenVPN-Profilen, Home-VPN, Mobile-Geräten, Remote-Dashboard, WireGuard-Hinweisen und Endpunktmapping ist sichtbar umgesetzt.",
    legacyStateNames: ["vpnconnect", "vpnconnectdetails", "mobile", "vpn-home-wizard", "remote", "mobile"],
    legacyModules: ["dashboard", "settings"],
    targetSurfaces: ["#vpn", "/api/v1/vpn"],
    priority: "high" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "system-admin",
    title: "System, Updates & Administration",
    legacyDescription: "Lizenz, Updates, Info/Rechtliches, Diagnose, Ereignisse, Backup/Reset, Aufgaben, Sprache, Netzwerk-Doctor und Open-Source-Lizenzen.",
    modernCoverage: "System-Admin-Center mit Lizenz, Updates, Diagnose, Events, Backup, Tasks, Sprache/Zeit, Admin-Passwort und Open-Source-Lizenzen ist sichtbar umgesetzt.",
    legacyStateNames: ["default", "license", "update", "about", "legal", "system", "adminpassword", "diagnostics", "events", "backup", "tasks", "timeandlanguage", "systempending", "logout", "open-source-licenses", "open-source-licenses-java", "open-source-licenses-ccpp", "open-source-licenses-javascript", "open-source-licenses-ruby", "open-source-licenses-debian"],
    legacyModules: ["settings"],
    targetSurfaces: ["#system", "/api/v1/system"],
    priority: "high" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "access-onboarding-lifecycle",
    title: "Zugang, Setup & Lifecycle",
    legacyDescription: "Login/Auth, Setup, Aktivierung, Splash-/Boot-/Update-/Shutdown-/Factory-Reset-/Standby-Screens und Hinweise/Erinnerungen.",
    modernCoverage: "Lifecycle-Center mit Login/Auth, Passwort-Reset, Aktivierung, Appliance-Statusseiten, Splash/No-License/Expired und Advice-Overlays ist sichtbar umgesetzt.",
    legacyStateNames: ["nolicense", "standby", "factoryResetScreen", "booting", "updating", "shutdown", "activation", "activationfinish", "login", "resetpassword", "auth", "splash", "expired", "app", "welcome", "reminder"],
    legacyModules: ["advice", "settings"],
    targetSurfaces: ["#lifecycle", "/api/v1/lifecycle"],
    priority: "medium" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
  {
    id: "redirect-block-pages",
    title: "Block-/Fehlerseiten & Redirects",
    legacyDescription: "Blocked-/Access-Denied-/Malware-/Ad-Tracker-/Squid-/Redirect-Options-Seiten, die Nutzer beim Browsen sehen.",
    modernCoverage: "Blockseiten-/Redirect-Center mit Redirect-Optionen, Access-Denied, Malware, Ads/Tracker, Whitelist, SSL-Ausnahme, Squid, Console und Admin-Logout ist sichtbar umgesetzt.",
    legacyStateNames: ["redirect", "redirectOptions", "blockOptions", "blocker", "blockerAccessDenied", "blockerAdsTrackers", "blockerMalware", "blockerWhitelisted", "squidError", "blockerSslWhitelisted", "console", "logoutAdmin"],
    legacyModules: ["dashboard"],
    targetSurfaces: ["#block-pages", "/api/v1/block-pages"],
    priority: "high" as LegacyParityPriority,
    stage: "ready" as LegacyParityStage,
    stageLabel: "Sichtbar umgesetzt"
  },
];

export function getMappedLegacyStateCount(): number {
  return legacyParityGroups.reduce((sum, group) => sum + group.legacyStateNames.length, 0);
}

export function getUniqueMappedLegacyStateNames(): readonly string[] {
  return Array.from(new Set(legacyParityGroups.flatMap((group) => group.legacyStateNames))).sort();
}

export function getLegacyParityCoveragePercent(): number {
  return Math.round((getMappedLegacyStateCount() / LEGACY_UI_STATE_COUNT) * 100);
}

export interface LegacyParityTotals {
  readonly totalStates: number;
  readonly mappedStates: number;
  readonly coveragePercent: number;
  readonly prototypeGroups: number;
  readonly apiNeededGroups: number;
  readonly mappedOnlyGroups: number;
  readonly readyGroups: number;
}

export function getLegacyParityTotals(): LegacyParityTotals {
  return {
    totalStates: LEGACY_UI_STATE_COUNT,
    mappedStates: getMappedLegacyStateCount(),
    coveragePercent: getLegacyParityCoveragePercent(),
    prototypeGroups: getGroupsByStage('prototype').length,
    apiNeededGroups: getGroupsByStage('api-needed').length,
    mappedOnlyGroups: getGroupsByStage('mapped').length,
    readyGroups: getGroupsByStage('ready').length
  };
}

export function getGroupsByStage(stage: LegacyParityStage): readonly LegacyParityGroup[] {
  return legacyParityGroups.filter((group) => group.stage === stage);
}

export function getCriticalParityGroups(): readonly LegacyParityGroup[] {
  return legacyParityGroups.filter((group) => group.priority === 'critical');
}

export function getUnmappedLegacyStates(): readonly string[] {
  const mapped = new Set(getUniqueMappedLegacyStateNames());
  return legacyParityGroups.flatMap((group) => group.legacyStateNames).filter((name) => !mapped.has(name));
}
