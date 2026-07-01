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
    modernCoverage: "Das neue Dashboard übernimmt Monitoring, Gerätefokus, Empfehlungen und Schnellaktionen; Controlbar-spezifika werden als kompakte Command-Bar/Quick-Actions weitergeführt.",
    legacyStateNames: ["home", "reset", "status", "print", "app", "main", "app", "action", "main", "appState", "mainState"],
    legacyModules: ["controlbar", "dashboard", "settings"],
    targetSurfaces: ["#dashboard", "/api/v1/dashboard", "/api/v1/controlbar"],
    priority: "critical" as LegacyParityPriority,
    stage: "prototype" as LegacyParityStage,
    stageLabel: "Prototyp sichtbar"
  },
  {
    id: "devices",
    title: "Geräte & Clients",
    legacyDescription: "Geräteliste, Discovery, Details, Pausen-/Schutz-/Profilzustand und Aktionen pro Gerät.",
    modernCoverage: "Gerätetabelle und Schnellaktionen sind sichtbar; als nächstes echte DeviceService-Daten und Detailseiten anbinden.",
    legacyStateNames: ["devices", "devicesstate", "deviceslist", "devicesdiscovery", "devicedetails"],
    legacyModules: ["settings"],
    targetSurfaces: ["#devices", "/api/v1/devices"],
    priority: "critical" as LegacyParityPriority,
    stage: "prototype" as LegacyParityStage,
    stageLabel: "Prototyp sichtbar"
  },
  {
    id: "family-parental-control",
    title: "Familie & Kinderschutz",
    legacyDescription: "Benutzer, Profile, Online-Zeitpläne, Black-/Whitelists und profilspezifische Einschränkungen.",
    modernCoverage: "Familienprofile, Schutzmodule und Empfehlungen sind im Dashboard sichtbar; Detail-CRUD und Listen-Migration folgen.",
    legacyStateNames: ["parentalcontrol", "parentalcontrolstate", "users", "user-details", "userprofiledetails", "blacklists", "whitelists", "blacklistdetails", "whitelistdetails"],
    legacyModules: ["settings"],
    targetSurfaces: ["#family", "/api/v1/parental-control"],
    priority: "high" as LegacyParityPriority,
    stage: "prototype" as LegacyParityStage,
    stageLabel: "Prototyp sichtbar"
  },
  {
    id: "protection-privacy",
    title: "Schutz, Filter & Privatsphäre",
    legacyDescription: "Filteranalyse, Block-/Freigabelisten, Anonymisierung, Tor, Malware-/Werbe-/Tracker-Blocker und erweiterte Schutzeinstellungen.",
    modernCoverage: "Schutzmodule, Blockrate, Top-Domains und Empfehlungen sind angelegt; tiefe Filteranalyse wird als eigener Screen migriert.",
    legacyStateNames: ["anonymization", "anonymizationstate", "tor", "filter", "filterstate", "filteroverview", "filter-details", "advancedsettings", "filteranalysis", "analysisdetails", "doctor"],
    legacyModules: ["settings"],
    targetSurfaces: ["#protection", "/api/v1/protection", "/api/v1/dns"],
    priority: "critical" as LegacyParityPriority,
    stage: "api-needed" as LegacyParityStage,
    stageLabel: "API-Anbindung nötig"
  },
  {
    id: "https-certificates",
    title: "HTTPS & Zertifikate",
    legacyDescription: "HTTPS-Assistent/-Status, Root-CA, vertrauenswürdige Apps/Domains, SSL-Fehler und manuelle Aufzeichnung.",
    modernCoverage: "HTTPS-Metrik, Root-CA Schnellaktion und Warnungen sind sichtbar; Zertifikats-/Trusted-App-Details brauchen echte API-Anbindung.",
    legacyStateNames: ["https", "sslstate", "sslstatus", "sslcertificate", "sslfails", "trustedapps", "trustedappsdetails", "trusteddomains", "manualrecording", "https"],
    legacyModules: ["dashboard", "settings"],
    targetSurfaces: ["#https", "/api/v1/ssl"],
    priority: "high" as LegacyParityPriority,
    stage: "api-needed" as LegacyParityStage,
    stageLabel: "API-Anbindung nötig"
  },
  {
    id: "network-dns",
    title: "Netzwerk, DNS & DHCP",
    legacyDescription: "DNS-Status, DNS-Server/lokale Einträge, Netzwerk-IPv4/IPv6-Einstellungen, Setup-Assistent und Resolver-Zustand.",
    modernCoverage: "Netzwerkfluss, Resolver-Karten und DNS-Telemetrie sind sichtbar; Network/DNS-Editoren werden als nächstes ersetzt.",
    legacyStateNames: ["dns", "dnsstate", "dnsstatus", "dnsserver", "dnslocal", "network", "networksettings", "networksettingsip6", "network-wizard"],
    legacyModules: ["settings"],
    targetSurfaces: ["#network", "/api/v1/network", "/api/v1/dns"],
    priority: "critical" as LegacyParityPriority,
    stage: "api-needed" as LegacyParityStage,
    stageLabel: "API-Anbindung nötig"
  },
  {
    id: "vpn-mobile",
    title: "VPN, Mobile & Remote",
    legacyDescription: "VPN Home, Mobile-Setup-Assistent, OpenVPN-Kompatibilität, Remote-Dashboard und künftiger WireGuard-Pfad.",
    modernCoverage: "VPN/Mobile-Modul und WireGuard-Empfehlung sind sichtbar; echte OpenVPN/WireGuard-Side-by-side UI folgt.",
    legacyStateNames: ["vpnconnect", "vpnconnectdetails", "mobile", "vpn-home-wizard", "remote", "mobile"],
    legacyModules: ["dashboard", "settings"],
    targetSurfaces: ["#vpn", "/api/v1/vpn"],
    priority: "high" as LegacyParityPriority,
    stage: "api-needed" as LegacyParityStage,
    stageLabel: "API-Anbindung nötig"
  },
  {
    id: "system-admin",
    title: "System, Updates & Administration",
    legacyDescription: "Lizenz, Updates, Info/Rechtliches, Diagnose, Ereignisse, Backup/Reset, Aufgaben, Sprache, Netzwerk-Doctor und Open-Source-Lizenzen.",
    modernCoverage: "Systemzustand, Update-Kanal, Diagnose und Backup-Hinweise sind sichtbar; Admin-Detailseiten werden in neue System-Sektion migriert.",
    legacyStateNames: ["default", "license", "update", "about", "legal", "system", "adminpassword", "diagnostics", "events", "backup", "tasks", "timeandlanguage", "systempending", "logout", "open-source-licenses", "open-source-licenses-java", "open-source-licenses-ccpp", "open-source-licenses-javascript", "open-source-licenses-ruby", "open-source-licenses-debian"],
    legacyModules: ["settings"],
    targetSurfaces: ["#system", "/api/v1/system"],
    priority: "high" as LegacyParityPriority,
    stage: "mapped" as LegacyParityStage,
    stageLabel: "Gemappt, noch nicht neu gebaut"
  },
  {
    id: "access-onboarding-lifecycle",
    title: "Zugang, Setup & Lifecycle",
    legacyDescription: "Login/Auth, Setup, Aktivierung, Splash-/Boot-/Update-/Shutdown-/Factory-Reset-/Standby-Screens und Hinweise/Erinnerungen.",
    modernCoverage: "Lifecycle-Screens sind vollständig inventarisiert und bekommen eigene Modern-UI-Flows vor Abschaltung des alten UI.",
    legacyStateNames: ["nolicense", "standby", "factoryResetScreen", "booting", "updating", "shutdown", "activation", "activationfinish", "login", "resetpassword", "auth", "splash", "expired", "app", "welcome", "reminder"],
    legacyModules: ["advice", "settings"],
    targetSurfaces: ["#setup", "/api/v1/setup", "/api/v1/auth"],
    priority: "medium" as LegacyParityPriority,
    stage: "mapped" as LegacyParityStage,
    stageLabel: "Gemappt, noch nicht neu gebaut"
  },
  {
    id: "redirect-block-pages",
    title: "Block-/Fehlerseiten & Redirects",
    legacyDescription: "Blocked-/Access-Denied-/Malware-/Ad-Tracker-/Squid-/Redirect-Options-Seiten, die Nutzer beim Browsen sehen.",
    modernCoverage: "Blockseiten sind als eigener Migrationsbereich erfasst; sie dürfen nicht hinter Admin-Dashboard-Arbeit vergessen werden.",
    legacyStateNames: ["redirect", "redirectOptions", "blockOptions", "blocker", "blockerAccessDenied", "blockerAdsTrackers", "blockerMalware", "blockerWhitelisted", "squidError", "blockerSslWhitelisted", "console", "logoutAdmin"],
    legacyModules: ["dashboard"],
    targetSurfaces: ["#block-pages", "/api/v1/block-pages"],
    priority: "high" as LegacyParityPriority,
    stage: "mapped" as LegacyParityStage,
    stageLabel: "Gemappt, noch nicht neu gebaut"
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
}

export function getLegacyParityTotals(): LegacyParityTotals {
  return {
    totalStates: LEGACY_UI_STATE_COUNT,
    mappedStates: getMappedLegacyStateCount(),
    coveragePercent: getLegacyParityCoveragePercent(),
    prototypeGroups: getGroupsByStage('prototype').length,
    apiNeededGroups: getGroupsByStage('api-needed').length,
    mappedOnlyGroups: getGroupsByStage('mapped').length
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
