/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.openvpn.server;

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.crypto.pki.RevocationInfo;
import org.eblocker.server.common.data.openvpn.OpenVpnServerCaKeys;
import org.eblocker.server.common.data.openvpn.OpenVpnServerDeviceKeys;
import org.eblocker.server.common.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OpenVpnCaTest {
    private Path caPath;

    @BeforeEach
    public void setUp() throws IOException {
        caPath = Files.createTempDirectory("mobile");
        Files.createDirectory(caPath.resolve("clients"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(caPath);
    }

    @Test
    public void testClientLifeCycle() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();

        // Initially there should be an empty CRL:
        assertEquals(0, getCrlEntries(ca).size());

        ca.generateClientCertificate("device:0");

        // Verify client certificate and key:
        X509Certificate device0Cert = PKI.loadCertificate(instream(ca.getClientCertificatePath("device:0")));
        assertEquals("CN=device:0", device0Cert.getSubjectX500Principal().getName());
        PrivateKey device0Key = PKI.loadPrivateKey(instream(ca.getClientKeyPath("device:0")));
        assertNotNull(device0Key);

        // Another client:
        ca.generateClientCertificate("device:1");
        X509Certificate device1Cert = PKI.loadCertificate(instream(ca.getClientCertificatePath("device:1")));

        assertNotEquals(device0Cert.getSerialNumber(), device1Cert.getSerialNumber());

        // Revoke device:0
        ca.revokeClientCertificate("device:0");

        assertEquals(1, getCrlEntries(ca).size());

        // Key and cert are gone:
        assertFalse(ca.getClientCertificatePath("device:0").toFile().exists());
        assertFalse(ca.getClientKeyPath("device:0").toFile().exists());

        // Revoke device:1
        ca.revokeClientCertificate("device:1");

        // Check the CRL again
        assertEquals(2, getCrlEntries(ca).size());
    }

    @Test
    public void testServerCertificate() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        ca.generateServerCertificate();

        // Verify client certificate and key:
        X509Certificate certificate = PKI.loadCertificate(instream(ca.getServerCertificatePath()));
        assertEquals("CN=eBlocker Mobile Server", certificate.getSubjectX500Principal().getName());
        PrivateKey device0Key = PKI.loadPrivateKey(instream(ca.getServerKeyPath()));
        assertNotNull(device0Key);
    }

    @Test
    public void testImportedEasyRsaCrl() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();

        // This CRL made by OpenSSL already contains one entry (but without revocation reason)
        Path crlSrc = Paths.get(ClassLoader.getSystemResource("test-data/easy-rsa/crl.pem").toURI());
        Path crlDst = ca.getCrlPath();
        Files.copy(crlSrc, crlDst, StandardCopyOption.REPLACE_EXISTING);

        // Add another entry in the CRL:
        String clientId = "device:0";
        ca.generateClientCertificate(clientId);
        X509Certificate clientCert = PKI.loadCertificate(instream(ca.getClientCertificatePath(clientId)));
        ca.revokeClientCertificate(clientId);
        assertEquals(2, getCrlEntries(ca).size());
    }

    @Test
    public void testCaTearDown() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        ca.generateServerCertificate();
        String clientId = "device:0";
        ca.generateClientCertificate(clientId);
        ca.tearDown();

        Stream.of(
                ca.getCaCertificatePath(),
                caPath.resolve("ca.key"),
                ca.getServerCertificatePath(),
                ca.getServerKeyPath(),
                ca.getCrlPath(),
                ca.getClientCertificatePath(clientId),
                ca.getClientKeyPath(clientId))

                .forEach(path -> {
                    assertFalse(path.toFile().exists(), path.toString() + " should be gone");
                });
    }

    @Test
    public void testGetActiveClientIds() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        assertEquals(0, ca.getActiveClientIds().size());

        ca.generateClientCertificate("device:0");
        assertEquals(1, ca.getActiveClientIds().size());
        assertTrue(ca.getActiveClientIds().contains("device:0"));

        ca.generateClientCertificate("device:1");
        assertEquals(2, ca.getActiveClientIds().size());
        assertTrue(ca.getActiveClientIds().contains("device:1"));

        ca.revokeClientCertificate("device:1");
        assertEquals(1, ca.getActiveClientIds().size());
        assertTrue(ca.getActiveClientIds().contains("device:0"));

        ca.tearDown();
        assertEquals(0, ca.getActiveClientIds().size());
    }

    @Test
    public void testExportCaNotGeneratedYet() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        assertNull(ca.exportCertificatesAndKeys());
    }

    @Test
    public void testExportImportCertificatesAndKeys() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        ca.generateServerCertificate();
        ca.generateClientCertificate("device:1");
        ca.generateClientCertificate("device:2");
        ca.generateClientCertificate("device:3");

        // Destroy key of device:3
        Files.delete(ca.getClientKeyPath("device:3"));

        OpenVpnServerCaKeys data = ca.exportCertificatesAndKeys();
        assertTrue(data.getCaCert().contains("BEGIN CERTIFICATE"));
        assertTrue(data.getCaKey().contains("BEGIN PRIVATE KEY"));
        assertTrue(data.getServerCert().contains("BEGIN CERTIFICATE"));
        assertTrue(data.getServerKey().contains("BEGIN PRIVATE KEY"));
        assertTrue(data.getCrl().contains("BEGIN X509 CRL"));
        List<OpenVpnServerDeviceKeys> deviceKeysList = data.getDeviceKeys();
        assertEquals(2, deviceKeysList.size());
        for (OpenVpnServerDeviceKeys deviceKeys: deviceKeysList) {
            assertNotEquals("device:3", deviceKeys.getDeviceId()); // device:3 without key is not included in backup
            assertTrue(deviceKeys.getClientCert().contains("BEGIN CERTIFICATE"));
            assertTrue(deviceKeys.getClientKey().contains("BEGIN PRIVATE KEY"));
        }

        ca.tearDown();

        OpenVpnCa restoredCA = new OpenVpnCa(caPath.toString());
        restoredCA.importCertificatesAndKeys(data);

        // restored CA is usable:
        restoredCA.generateClientCertificate("device:3");
        assertEquals(Set.of("device:1", "device:2", "device:3"), restoredCA.getActiveClientIds());
    }

    private Set<RevocationInfo> getCrlEntries(OpenVpnCa ca) throws CryptoException, IOException {
        X509CRL crl = PKI.loadCrl(instream(ca.getCrlPath()));
        return PKI.getRevocationInfoEntries(crl);
    }

    private InputStream instream(Path path) throws FileNotFoundException {
        return new FileInputStream(path.toFile());
    }
}
