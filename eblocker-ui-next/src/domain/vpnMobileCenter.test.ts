import { describe, expect, it } from 'vitest';
import {
  getVpnMobileCenterTotals,
  mobileDeviceRows,
  remoteDashboardRows,
  vpnClientProfiles,
  vpnHomeStatus,
  vpnMobileCapabilities,
  vpnMobileCenterEndpoints,
  vpnMobileLegacyStates,
  vpnSetupWizardSteps,
  wireGuardMigrationHints
} from './vpnMobileCenter';

describe('vpn mobile center legacy parity', () => {
  it('covers every legacy VPN, mobile and remote state', () => {
    expect(vpnMobileLegacyStates).toEqual([
      'vpnconnect',
      'vpnconnectdetails',
      'vpn-home',
      'vpn-home-wizard',
      'mobile',
      'remote'
    ]);
  });

  it('maps VpnService, VpnHomeService, dashboard mobile and UPnP endpoints to /api/v1/vpn targets', () => {
    expect(vpnMobileCenterEndpoints).toHaveLength(29);
    expect(vpnMobileCenterEndpoints.every((endpoint) => endpoint.modern.startsWith('/api/v1/vpn/'))).toBe(true);
    expect(vpnMobileCenterEndpoints.map((endpoint) => endpoint.legacy)).toEqual(expect.arrayContaining([
      '/api/adminconsole/vpn/profiles',
      '/api/adminconsole/vpn/profile/{id}/config/{option}',
      '/api/adminconsole/vpn/profile/{id}/status/{deviceId}',
      '/api/adminconsole/openvpn/status',
      '/api/adminconsole/openvpn/certificates/generateDownloadUrl/{deviceId}/{os}',
      '/api/adminconsole/openvpn/privateNetworkAccess/{deviceId}',
      '/api/adminconsole/upnpn/{port}',
      '/api/dashboard/openvpn/filename/{deviceId}/{os}',
      '/api/dashboard/openvpn/certificates/generateDownloadUrl/{deviceId}/{os}',
      '/dashboard/remote/{deviceId}'
    ]));
  });

  it('keeps OpenVPN client profiles with config completeness, credentials and connection test state visible', () => {
    expect(vpnClientProfiles).toHaveLength(4);
    expect(vpnClientProfiles.some((profile) => profile.temporary && !profile.configurationComplete)).toBe(true);
    expect(vpnClientProfiles.some((profile) => profile.requiredFilesMissing.length > 0)).toBe(true);
    expect(vpnClientProfiles.some((profile) => profile.connectionTest === 'auth_failed')).toBe(true);
    expect(vpnClientProfiles.some((profile) => profile.keepAliveMode === 'OPENVPN_REMOTE')).toBe(true);
  });

  it('keeps Home VPN server status, certificates, access type, port mapping and connection tests represented', () => {
    expect(vpnHomeStatus.protocol).toBe('OpenVPN');
    expect(vpnHomeStatus.port).toBe(1194);
    expect(vpnHomeStatus.portForwardingMode).toBe('AUTO');
    expect(vpnHomeStatus.certificates).toBeGreaterThan(0);
    expect(vpnHomeStatus.connectionTest.state).toBe('SUCCESS');
    expect(vpnHomeStatus.hostnameTest).toBe('PASS');
    expect(vpnSetupWizardSteps).toHaveLength(7);
    expect(vpnSetupWizardSteps.map((step) => step.key)).toEqual([
      'welcome',
      'choose-access',
      'set-host',
      'choose-port-mapping',
      'map-port',
      'connection-test',
      'finish'
    ]);
  });

  it('keeps mobile devices, OS-specific downloads, private LAN access and remote-dashboard routing visible', () => {
    expect(mobileDeviceRows).toHaveLength(5);
    expect(mobileDeviceRows.map((device) => device.os)).toEqual(expect.arrayContaining(['WINDOWS', 'MAC', 'IOS', 'ANDROID', 'OTHER']));
    expect(mobileDeviceRows.some((device) => device.mobileEnabled && device.privateNetworkAccess)).toBe(true);
    expect(mobileDeviceRows.some((device) => !device.configDownloadEnabled)).toBe(true);
    expect(remoteDashboardRows).toHaveLength(3);
    expect(remoteDashboardRows.some((row) => row.route === '/remote/kinder-tablet')).toBe(true);
  });

  it('documents WireGuard migration hints while preserving OpenVPN behavior', () => {
    expect(wireGuardMigrationHints).toEqual(expect.arrayContaining([
      'OpenVPN bleibt Legacy-kompatibel für bestehende Profile und mobile Zertifikate',
      'WireGuard wird als paralleler Zielpfad ausgewiesen, aber nicht als vorhandener Legacy-Endpunkt behauptet',
      'Home-VPN-Connection-Test, Hostname-Test und Portmapping bleiben vor einer Protokollumstellung sichtbar'
    ]));
  });

  it('summarizes the center and migrated capabilities', () => {
    expect(getVpnMobileCenterTotals()).toEqual({
      legacyStates: 6,
      endpoints: 29,
      clientProfiles: 4,
      wizardSteps: 7,
      mobileDevices: 5,
      remoteDevices: 3
    });
    expect(vpnMobileCapabilities).toEqual(expect.arrayContaining([
      'OpenVPN-Clientprofile mit Config-Upload, Pflichtdateien, Login-Daten, KeepAlive und Verbindungstest verwalten',
      'Home-VPN-Serverstatus, Host/DynDNS, Portmapping, Zertifikate und Reset/Wizard-Aktionen zusammenführen',
      'Mobile-Geräte aktivieren, Zertifikate je Betriebssystem herunterladen und privaten LAN-Zugriff setzen',
      'Dashboard-Mobile-Wizard und Remote-Dashboard-Gerätepfad sichtbar machen',
      'WireGuard-Migration vorbereiten ohne bestehende OpenVPN-Flows zu verstecken'
    ]));
  });
});
