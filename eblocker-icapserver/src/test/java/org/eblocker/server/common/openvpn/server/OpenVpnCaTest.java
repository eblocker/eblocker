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
import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
import java.util.Set;
import java.util.stream.Stream;

public class OpenVpnCaTest {
    private Path caPath;

    @Before
    public void setUp() throws IOException {
        caPath = Files.createTempDirectory("mobile");
        Files.createDirectory(caPath.resolve("clients"));
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(caPath);
    }

    @Test
    public void testClientLifeCycle() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();

        // Initially there should be an empty CRL:
        Assert.assertEquals(0, getCrlEntries(ca).size());

        ca.generateClientCertificate("device:0");

        // Verify client certificate and key:
        X509Certificate device0Cert = PKI.loadCertificate(instream(ca.getClientCertificatePath("device:0")));
        Assert.assertEquals("CN=device:0", device0Cert.getSubjectX500Principal().getName());
        PrivateKey device0Key = PKI.loadPrivateKey(instream(ca.getClientKeyPath("device:0")));
        Assert.assertNotNull(device0Key);

        // Another client:
        ca.generateClientCertificate("device:1");
        X509Certificate device1Cert = PKI.loadCertificate(instream(ca.getClientCertificatePath("device:1")));

        Assert.assertNotEquals(device0Cert.getSerialNumber(), device1Cert.getSerialNumber());

        // Revoke device:0
        ca.revokeClientCertificate("device:0");

        Assert.assertEquals(1, getCrlEntries(ca).size());

        // Key and cert are gone:
        Assert.assertFalse(ca.getClientCertificatePath("device:0").toFile().exists());
        Assert.assertFalse(ca.getClientKeyPath("device:0").toFile().exists());

        // Revoke device:1
        ca.revokeClientCertificate("device:1");

        // Check the CRL again
        Assert.assertEquals(2, getCrlEntries(ca).size());
    }

    @Test
    public void testServerCertificate() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        ca.generateServerCertificate();

        // Verify client certificate and key:
        X509Certificate certificate = PKI.loadCertificate(instream(ca.getServerCertificatePath()));
        Assert.assertEquals("CN=eBlocker Mobile Server", certificate.getSubjectX500Principal().getName());
        PrivateKey device0Key = PKI.loadPrivateKey(instream(ca.getServerKeyPath()));
        Assert.assertNotNull(device0Key);
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
        Assert.assertEquals(2, getCrlEntries(ca).size());
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
                    Assert.assertFalse(path.toString() + " should be gone", path.toFile().exists());
                });
    }

    @Test
    public void testGetActiveClientIds() throws Exception {
        OpenVpnCa ca = new OpenVpnCa(caPath.toString());
        ca.generateCa();
        Assert.assertEquals(0, ca.getActiveClientIds().size());

        ca.generateClientCertificate("device:0");
        Assert.assertEquals(1, ca.getActiveClientIds().size());
        Assert.assertTrue(ca.getActiveClientIds().contains("device:0"));

        ca.generateClientCertificate("device:1");
        Assert.assertEquals(2, ca.getActiveClientIds().size());
        Assert.assertTrue(ca.getActiveClientIds().contains("device:1"));

        ca.revokeClientCertificate("device:1");
        Assert.assertEquals(1, ca.getActiveClientIds().size());
        Assert.assertTrue(ca.getActiveClientIds().contains("device:0"));

        ca.tearDown();
        Assert.assertEquals(0, ca.getActiveClientIds().size());
    }

    private Set<RevocationInfo> getCrlEntries(OpenVpnCa ca) throws CryptoException, IOException {
        X509CRL crl = PKI.loadCrl(instream(ca.getCrlPath()));
        return PKI.getRevocationInfoEntries(crl);
    }

    private InputStream instream(Path path) throws FileNotFoundException {
        return new FileInputStream(path.toFile());
    }
}
