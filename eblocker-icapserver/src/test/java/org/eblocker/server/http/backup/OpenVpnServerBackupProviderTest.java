/*
 * Copyright 2026 eBlocker Open Source GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.http.backup;

import com.google.common.base.Charsets;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.openvpn.OpenVpnServerCaKeys;
import org.eblocker.server.common.data.openvpn.OpenVpnServerDeviceKeys;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.openvpn.server.OpenVpnCa;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.*;

class OpenVpnServerBackupProviderTest extends BackupProviderTestBase {
    private OpenVpnServerBackupProvider provider;
    private OpenVpnServerBackupProvider providerNoPassword;
    private OpenVpnServerService service;
    private OpenVpnCa ca;
    private VpnServerStatus vpnServerStatus;
    private Path caPath;
    private Path serverPath;

    @BeforeEach
    void setUp() throws Exception {
        caPath = Files.createTempDirectory("openvpn-ca");
        serverPath = Files.createTempDirectory("openvpn-server");
        service = Mockito.mock(OpenVpnServerService.class);
        vpnServerStatus = new VpnServerStatus();
        Mockito.when(service.getOpenVpnServerStatus()).thenReturn(vpnServerStatus);
        ca = Mockito.mock(OpenVpnCa.class);
        provider = new OpenVpnServerBackupProvider(service, ca, serverPath.toString(), caPath.toString(), createCryptoService("top secret!!!"));
        providerNoPassword = new OpenVpnServerBackupProvider(service, ca, serverPath.toString(), caPath.toString(), null);
    }

    @Test
    public void noPasswordForExport() throws Exception {
        vpnServerStatus.setFirstStart(true); // never used
        assertTrue(exportBackup(providerNoPassword).length > 0);

        vpnServerStatus.setFirstStart(false);
        assertThrows(EncryptionUnavailableException.class, () -> exportBackup(providerNoPassword));
    }

    @Test
    public void noPasswordForImport() throws Exception {
        byte[] backup = exportBackup(provider);
        importBackup(backup, providerNoPassword);
        assertEquals(List.of(BackupWarning.NO_PASSWORD_OPENVPN_SERVER_NOT_IMPORTED), providerNoPassword.getWarnings());
    }

    @Test
    public void testExportVerifyImport() throws IOException {
        exportVerifyImport(provider);
    }

    @Test
    public void importRemovesFirstRunFlag() throws Exception {
        byte[] backup = exportBackup(provider);
        importBackup(backup, provider);
        Mockito.verify(service).setOpenVpnServerfirstRun(false);
    }

    @Test
    public void testSharedSecret() throws IOException {
        Files.writeString(serverPath.resolve("ta.key"), "shared secret", Charsets.US_ASCII);
        exportVerifyImport(provider);
        String taKey = Files.readString(caPath.resolve("ta.key"), Charsets.US_ASCII);
        assertEquals("shared secret", taKey);
    }

    @Test
    void keysAreEncrypted() throws IOException {
        OpenVpnServerCaKeys keys = new OpenVpnServerCaKeys();
        keys.setCaKey("CA private key");
        keys.setCaCert("CA certificate");
        keys.setServerKey("Server private key");
        keys.setServerCert("Server certificate");
        keys.setCrl("CRL");
        OpenVpnServerDeviceKeys deviceKeys = new OpenVpnServerDeviceKeys();
        deviceKeys.setDeviceId("device:1");
        deviceKeys.setClientCert("Device certificate");
        deviceKeys.setClientKey("Device private key");
        keys.setDeviceKeys(List.of(deviceKeys));
        Mockito.when(ca.exportCertificatesAndKeys()).thenReturn(keys);
        Files.writeString(serverPath.resolve("ta.key"), "shared secret", Charsets.US_ASCII);
        byte[] backup = exportBackup(provider);

        JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(backup));
        jarInputStream.getNextEntry();
        String rawJson = new String(jarInputStream.readAllBytes(), Charsets.UTF_8);
        assertTrue(rawJson.contains("certificate"));
        assertTrue(rawJson.contains("CRL"));

        // No unencrypted keys:
        assertFalse(rawJson.contains("private key"));
        assertFalse(rawJson.contains("shared secret"));
    }

    @Test
    public void upnpFailure() throws Exception {
        vpnServerStatus.setPortForwardingMode(PortForwardingMode.AUTO);
        vpnServerStatus.setRunning(true);
        Mockito.doThrow(new UpnpPortForwardingException("Could not enable port forwarding"))
                .when(service).enablePortForwarding();
        byte[] backup = exportBackup(provider);
        importBackup(backup, provider);
        assertEquals(List.of(BackupWarning.UPNP_PORT_FORWARDING_FAILURE), provider.getWarnings());
    }
}