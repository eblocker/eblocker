export type VpnEndpointMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
export type VpnProfileConnectionTest = 'success' | 'failed' | 'auth_failed' | 'timeout' | 'not_tested';
export type VpnOsType = 'WINDOWS' | 'MAC' | 'IOS' | 'ANDROID' | 'OTHER';
export type PortForwardingMode = 'AUTO' | 'MANUAL';
export type ExternalAddressType = 'FIXED_IP' | 'DYN_DNS' | 'EBLOCKER_DYN_DNS';

export interface VpnEndpoint {
  readonly method: VpnEndpointMethod;
  readonly legacy: string;
  readonly modern: string;
  readonly purpose: string;
}

export interface VpnClientProfile {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly enabled: boolean;
  readonly temporary: boolean;
  readonly configurationComplete: boolean;
  readonly configurationFileVersion: string;
  readonly username: string;
  readonly passwordConfigured: boolean;
  readonly keepAliveMode: 'OPENVPN_REMOTE' | 'DISABLED';
  readonly keepAlivePingTarget: string;
  readonly nameServersEnabled: boolean;
  readonly requiredFilesMissing: readonly string[];
  readonly validationErrors: readonly string[];
  readonly connected: boolean;
  readonly connectionTest: VpnProfileConnectionTest;
}

export interface VpnHomeConnectionTest {
  readonly state: 'SUCCESS' | 'ERROR' | 'FAILURE' | 'PENDING_RESULTS';
  readonly udpMessagesSent: number;
  readonly udpMessagesReceived: number;
  readonly tcpMessagesSent: number;
  readonly tcpMessagesReceived: number;
}

export interface VpnHomeStatus {
  readonly protocol: 'OpenVPN';
  readonly isRunning: boolean;
  readonly isFirstStart: boolean;
  readonly externalAddressType: ExternalAddressType;
  readonly host: string;
  readonly port: number;
  readonly mappedPort: number;
  readonly portForwardingMode: PortForwardingMode;
  readonly certificates: number;
  readonly enabledDevices: number;
  readonly privateNetworkDevices: number;
  readonly connectionTest: VpnHomeConnectionTest;
  readonly hostnameTest: 'PASS' | 'FAIL' | 'SKIPPED';
}

export interface VpnWizardStep {
  readonly key: string;
  readonly title: string;
  readonly legacyTemplate: string;
  readonly actions: readonly string[];
}

export interface MobileDeviceRow {
  readonly id: string;
  readonly name: string;
  readonly os: VpnOsType;
  readonly mobileEnabled: boolean;
  readonly privateNetworkAccess: boolean;
  readonly configDownloadEnabled: boolean;
  readonly downloadEndpoint: string;
  readonly dashboardWizard: boolean;
}

export interface RemoteDashboardRow {
  readonly deviceId: string;
  readonly deviceName: string;
  readonly route: string;
  readonly warningShown: boolean;
  readonly selectedByDeviceSelector: boolean;
}

export const vpnMobileLegacyStates = [
  'vpnconnect',
  'vpnconnectdetails',
  'vpn-home',
  'vpn-home-wizard',
  'mobile',
  'remote'
] as const;

export const vpnMobileCenterEndpoints: readonly VpnEndpoint[] = [
  { method: 'GET', legacy: '/api/adminconsole/vpn/profiles', modern: '/api/v1/vpn/client-profiles', purpose: 'OpenVPN-Profile laden' },
  { method: 'POST', legacy: '/api/adminconsole/vpn/profile', modern: '/api/v1/vpn/client-profiles', purpose: 'Temporäres Profil anlegen' },
  { method: 'GET', legacy: '/api/adminconsole/vpn/profile/{id}', modern: '/api/v1/vpn/client-profiles/{id}', purpose: 'Profil laden' },
  { method: 'PUT', legacy: '/api/adminconsole/vpn/profile/{id}', modern: '/api/v1/vpn/client-profiles/{id}', purpose: 'Profil speichern' },
  { method: 'DELETE', legacy: '/api/adminconsole/vpn/profile/{id}', modern: '/api/v1/vpn/client-profiles/{id}', purpose: 'Profil löschen' },
  { method: 'GET', legacy: '/api/adminconsole/vpn/profile/{id}/config', modern: '/api/v1/vpn/client-profiles/{id}/config', purpose: 'Config/Parsed Options laden' },
  { method: 'PUT', legacy: '/api/adminconsole/vpn/profile/{id}/config', modern: '/api/v1/vpn/client-profiles/{id}/config', purpose: 'OpenVPN-Konfiguration hochladen' },
  { method: 'PUT', legacy: '/api/adminconsole/vpn/profile/{id}/config/{option}', modern: '/api/v1/vpn/client-profiles/{id}/config-options/{option}', purpose: 'Pflichtdatei/Inline-Option hochladen' },
  { method: 'GET', legacy: '/api/adminconsole/vpn/profile/{id}/status', modern: '/api/v1/vpn/client-profiles/{id}/status', purpose: 'Profilstatus laden' },
  { method: 'PUT', legacy: '/api/adminconsole/vpn/profile/{id}/status', modern: '/api/v1/vpn/client-profiles/{id}/status', purpose: 'Profil aktivieren/testen' },
  { method: 'GET', legacy: '/api/adminconsole/vpn/profile/{id}/status/{deviceId}', modern: '/api/v1/vpn/client-profiles/{id}/devices/{deviceId}/status', purpose: 'Gerätestatus laden' },
  { method: 'PUT', legacy: '/api/adminconsole/vpn/profile/{id}/status/{deviceId}', modern: '/api/v1/vpn/client-profiles/{id}/devices/{deviceId}/status', purpose: 'Gerätestatus setzen' },
  { method: 'GET', legacy: '/api/adminconsole/vpn/profile/status/{deviceId}', modern: '/api/v1/vpn/devices/{deviceId}/client-status', purpose: 'Profilstatus per Gerät laden' },
  { method: 'GET', legacy: '/api/adminconsole/openvpn/status', modern: '/api/v1/vpn/home/status', purpose: 'Home-VPN-Serverstatus laden' },
  { method: 'POST', legacy: '/api/adminconsole/openvpn/status', modern: '/api/v1/vpn/home/status', purpose: 'Home-VPN-Serverstatus setzen/starten' },
  { method: 'DELETE', legacy: '/api/adminconsole/openvpn/status', modern: '/api/v1/vpn/home/status', purpose: 'Home-VPN zurücksetzen' },
  { method: 'GET', legacy: '/api/adminconsole/openvpn/certificates', modern: '/api/v1/vpn/home/certificates', purpose: 'Mobile-Zertifikate laden' },
  { method: 'GET', legacy: '/api/adminconsole/openvpn/certificates/generateDownloadUrl/{deviceId}/{os}', modern: '/api/v1/vpn/home/devices/{deviceId}/downloads/{os}', purpose: 'AdminConsole-Konfigdownload erzeugen' },
  { method: 'POST', legacy: '/api/adminconsole/openvpn/enable/{deviceId}', modern: '/api/v1/vpn/home/devices/{deviceId}/enable', purpose: 'Mobile für Gerät aktivieren' },
  { method: 'POST', legacy: '/api/adminconsole/openvpn/disable/{deviceId}', modern: '/api/v1/vpn/home/devices/{deviceId}/disable', purpose: 'Mobile für Gerät deaktivieren' },
  { method: 'PUT', legacy: '/api/adminconsole/openvpn/privateNetworkAccess/{deviceId}', modern: '/api/v1/vpn/home/devices/{deviceId}/private-network-access', purpose: 'Privaten LAN-Zugriff setzen' },
  { method: 'POST', legacy: '/api/adminconsole/openvpn/test', modern: '/api/v1/vpn/home/connection-test', purpose: 'Home-Verbindungstest starten' },
  { method: 'GET', legacy: '/api/adminconsole/openvpn/test', modern: '/api/v1/vpn/home/connection-test', purpose: 'Home-Verbindungsteststatus laden' },
  { method: 'DELETE', legacy: '/api/adminconsole/openvpn/test', modern: '/api/v1/vpn/home/connection-test', purpose: 'Home-Verbindungstest abbrechen' },
  { method: 'POST', legacy: '/api/adminconsole/openvpn/dns', modern: '/api/v1/vpn/home/hostname-test', purpose: 'Hostname/DNS testen' },
  { method: 'PUT', legacy: '/api/adminconsole/upnpn/{port}', modern: '/api/v1/vpn/home/port-mapping/{port}', purpose: 'UPnP-Portmapping setzen' },
  { method: 'GET', legacy: '/api/dashboard/openvpn/filename/{deviceId}/{os}', modern: '/api/v1/vpn/mobile/devices/{deviceId}/filename/{os}', purpose: 'Dashboard-Mobile-Dateiname laden' },
  { method: 'GET', legacy: '/api/dashboard/openvpn/certificates/generateDownloadUrl/{deviceId}/{os}', modern: '/api/v1/vpn/mobile/devices/{deviceId}/downloads/{os}', purpose: 'Dashboard-Mobile-Konfigdownload erzeugen' },
  { method: 'GET', legacy: '/dashboard/remote/{deviceId}', modern: '/api/v1/vpn/remote-dashboard/{deviceId}', purpose: 'Remote-Dashboard-Gerätepfad' }
];

export const vpnClientProfiles: readonly VpnClientProfile[] = [
  {
    id: 'work-vpn',
    name: 'Work VPN',
    description: 'OpenVPN Profil mit DNS und KeepAlive',
    enabled: true,
    temporary: false,
    configurationComplete: true,
    configurationFileVersion: '2.5',
    username: 'shedowe',
    passwordConfigured: true,
    keepAliveMode: 'OPENVPN_REMOTE',
    keepAlivePingTarget: '10.8.0.1',
    nameServersEnabled: true,
    requiredFilesMissing: [],
    validationErrors: [],
    connected: true,
    connectionTest: 'success'
  },
  {
    id: 'provider-import',
    name: 'Provider Import',
    description: 'Config wartet auf CA-Datei',
    enabled: true,
    temporary: true,
    configurationComplete: false,
    configurationFileVersion: 'unknown',
    username: '',
    passwordConfigured: false,
    keepAliveMode: 'DISABLED',
    keepAlivePingTarget: '',
    nameServersEnabled: false,
    requiredFilesMissing: ['ca.crt'],
    validationErrors: ['requiredFiles'],
    connected: false,
    connectionTest: 'not_tested'
  },
  {
    id: 'legacy-auth',
    name: 'Legacy Auth',
    description: 'Profil mit Auth-Fehler im Testdialog',
    enabled: false,
    temporary: false,
    configurationComplete: true,
    configurationFileVersion: '2.4',
    username: 'legacy-user',
    passwordConfigured: true,
    keepAliveMode: 'DISABLED',
    keepAlivePingTarget: '',
    nameServersEnabled: false,
    requiredFilesMissing: [],
    validationErrors: [],
    connected: false,
    connectionTest: 'auth_failed'
  },
  {
    id: 'travel',
    name: 'Travel',
    description: 'Mobile Fallback-Konfiguration',
    enabled: true,
    temporary: false,
    configurationComplete: true,
    configurationFileVersion: '2.5',
    username: 'travel',
    passwordConfigured: false,
    keepAliveMode: 'DISABLED',
    keepAlivePingTarget: '',
    nameServersEnabled: true,
    requiredFilesMissing: [],
    validationErrors: [],
    connected: false,
    connectionTest: 'timeout'
  }
];

export const vpnHomeStatus: VpnHomeStatus = {
  protocol: 'OpenVPN',
  isRunning: true,
  isFirstStart: false,
  externalAddressType: 'DYN_DNS',
  host: 'shedowe-mobile.example.net',
  port: 1194,
  mappedPort: 1194,
  portForwardingMode: 'AUTO',
  certificates: 5,
  enabledDevices: 4,
  privateNetworkDevices: 2,
  connectionTest: {
    state: 'SUCCESS',
    udpMessagesSent: 4,
    udpMessagesReceived: 4,
    tcpMessagesSent: 2,
    tcpMessagesReceived: 2
  },
  hostnameTest: 'PASS'
};

export const vpnSetupWizardSteps: readonly VpnWizardStep[] = [
  { key: 'welcome', title: 'Willkommen', legacyTemplate: 'step-1-welcome.template.html', actions: ['start'] },
  { key: 'choose-access', title: 'Zugang wählen', legacyTemplate: 'step-2-choose-access.template.html', actions: ['FIXED_IP', 'DYN_DNS', 'EBLOCKER_DYN_DNS'] },
  { key: 'set-host', title: 'Host/IP setzen', legacyTemplate: 'step-3-set-ip.template.html', actions: ['host required', 'eBlocker DynDNS bestätigen'] },
  { key: 'choose-port-mapping', title: 'Portmapping wählen', legacyTemplate: 'step-4-choose-port-mapping.template.html', actions: ['AUTO', 'MANUAL'] },
  { key: 'map-port', title: 'Port 1194 mappen', legacyTemplate: 'step-5-do-port-mapping.template.html', actions: ['UPnP mapVpnPorts', 'manuell bestätigen'] },
  { key: 'connection-test', title: 'Verbindung & Hostname testen', legacyTemplate: 'step-6-connection-test.template.html', actions: ['connection test', 'hostname test', 'cancel'] },
  { key: 'finish', title: 'Server starten', legacyTemplate: 'step-7-finish.template.html', actions: ['launch server'] }
];

export const mobileDeviceRows: readonly MobileDeviceRow[] = [
  { id: 'macbook', name: 'MacBook Pro', os: 'MAC', mobileEnabled: true, privateNetworkAccess: true, configDownloadEnabled: true, downloadEndpoint: '/api/adminconsole/openvpn/certificates/generateDownloadUrl/macbook/MAC', dashboardWizard: true },
  { id: 'iphone', name: 'iPhone', os: 'IOS', mobileEnabled: true, privateNetworkAccess: false, configDownloadEnabled: true, downloadEndpoint: '/api/dashboard/openvpn/certificates/generateDownloadUrl/iphone/IOS', dashboardWizard: true },
  { id: 'android', name: 'Android Tablet', os: 'ANDROID', mobileEnabled: true, privateNetworkAccess: true, configDownloadEnabled: true, downloadEndpoint: '/api/dashboard/openvpn/certificates/generateDownloadUrl/android/ANDROID', dashboardWizard: true },
  { id: 'windows', name: 'Windows Laptop', os: 'WINDOWS', mobileEnabled: false, privateNetworkAccess: false, configDownloadEnabled: false, downloadEndpoint: '/api/adminconsole/openvpn/certificates/generateDownloadUrl/windows/WINDOWS', dashboardWizard: true },
  { id: 'other', name: 'Sonstiges Gerät', os: 'OTHER', mobileEnabled: false, privateNetworkAccess: false, configDownloadEnabled: false, downloadEndpoint: '/api/adminconsole/openvpn/certificates/generateDownloadUrl/other/OTHER', dashboardWizard: false }
];

export const remoteDashboardRows: readonly RemoteDashboardRow[] = [
  { deviceId: 'kinder-tablet', deviceName: 'Kinder-Tablet', route: '/remote/kinder-tablet', warningShown: true, selectedByDeviceSelector: true },
  { deviceId: 'iphone', deviceName: 'iPhone', route: '/remote/iphone', warningShown: true, selectedByDeviceSelector: true },
  { deviceId: 'android', deviceName: 'Android Tablet', route: '/remote/android', warningShown: true, selectedByDeviceSelector: true }
];

export const wireGuardMigrationHints = [
  'OpenVPN bleibt Legacy-kompatibel für bestehende Profile und mobile Zertifikate',
  'WireGuard wird als paralleler Zielpfad ausgewiesen, aber nicht als vorhandener Legacy-Endpunkt behauptet',
  'Home-VPN-Connection-Test, Hostname-Test und Portmapping bleiben vor einer Protokollumstellung sichtbar'
] as const;

export const vpnMobileCapabilities = [
  'OpenVPN-Clientprofile mit Config-Upload, Pflichtdateien, Login-Daten, KeepAlive und Verbindungstest verwalten',
  'Home-VPN-Serverstatus, Host/DynDNS, Portmapping, Zertifikate und Reset/Wizard-Aktionen zusammenführen',
  'Mobile-Geräte aktivieren, Zertifikate je Betriebssystem herunterladen und privaten LAN-Zugriff setzen',
  'Dashboard-Mobile-Wizard und Remote-Dashboard-Gerätepfad sichtbar machen',
  'WireGuard-Migration vorbereiten ohne bestehende OpenVPN-Flows zu verstecken'
] as const;

export function getVpnMobileCenterTotals(): {
  legacyStates: number;
  endpoints: number;
  clientProfiles: number;
  wizardSteps: number;
  mobileDevices: number;
  remoteDevices: number;
} {
  return {
    legacyStates: vpnMobileLegacyStates.length,
    endpoints: vpnMobileCenterEndpoints.length,
    clientProfiles: vpnClientProfiles.length,
    wizardSteps: vpnSetupWizardSteps.length,
    mobileDevices: mobileDeviceRows.length,
    remoteDevices: remoteDashboardRows.length
  };
}
