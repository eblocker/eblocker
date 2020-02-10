/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.certificate.validator.squid;

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

public class IntermediateCertificatesStoreTest {

    private Path certificateFilePath;
    private IntermediateCertificatesStore store;

    private X509Certificate[] certificates;

    @Before
    public void setUp() throws IOException, CryptoException {
        certificates = PKI.loadCertificates(ClassLoader.getSystemResourceAsStream("intermediate-certificates.pem"));

        certificateFilePath = Files.createTempFile("certificates", ".pem");
        store = new IntermediateCertificatesStore(certificateFilePath);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(certificateFilePath);
    }

    @Test
    public void get() throws Exception {
        writeToCertificateFile(certificates);
        store.refresh();

        Assert.assertEquals(Collections.singletonList(certificates[0]),
            store.get(certificates[0].getSubjectX500Principal(), null, null));
        Assert.assertEquals(
            Collections.singletonList(certificates[0]),
            store.get(certificates[0].getSubjectX500Principal(), null, PKI.getSubjectKeyIdentifier(certificates[0])));
        Assert.assertEquals(
            Collections.singletonList(certificates[0]),
            store.get(certificates[0].getSubjectX500Principal(), certificates[0].getSerialNumber(), null));
        Assert.assertEquals(
            Collections.singletonList(certificates[0]),
            store.get(certificates[0].getSubjectX500Principal(), certificates[0].getSerialNumber(),
                PKI.getSubjectKeyIdentifier(certificates[0])));

        Assert.assertEquals(Arrays.asList(certificates[1], certificates[2], certificates[3]),
            store.get(certificates[1].getSubjectX500Principal(), null, null));
        Assert.assertEquals(Arrays.asList(certificates[1], certificates[2], certificates[3]),
            store.get(certificates[1].getSubjectX500Principal(), null, PKI.getSubjectKeyIdentifier(certificates[1])));
        for (int i = 1; i < 4; ++i) {
            Assert.assertEquals(
                Collections.singletonList(certificates[i]),
                store.get(certificates[i].getSubjectX500Principal(), certificates[i].getSerialNumber(), null));
            Assert.assertEquals(
                Collections.singletonList(certificates[i]),
                store.get(certificates[i].getSubjectX500Principal(), certificates[i].getSerialNumber(), PKI.getSubjectKeyIdentifier(certificates[i])));
        }
    }

    @Test
    public void refresh() throws Exception {
        writeToCertificateFile(certificates[0]);
        Files.setLastModifiedTime(certificateFilePath, FileTime.fromMillis(1527775020000L));
        store.refresh();
        Assert.assertEquals(Collections.singletonList(certificates[0]), store.get(certificates[0].getSubjectX500Principal(), null, null));

        writeToCertificateFile(certificates[0], certificates[1]);
        Files.setLastModifiedTime(certificateFilePath, FileTime.fromMillis(1527775040000L));
        store.refresh();
        Assert.assertEquals(Collections.singletonList(certificates[0]), store.get(certificates[0].getSubjectX500Principal(), null, null));
        Assert.assertEquals(Collections.singletonList(certificates[1]), store.get(certificates[1].getSubjectX500Principal(), null, null));

        Files.delete(certificateFilePath);
        store.refresh();
        Assert.assertEquals(Collections.singletonList(certificates[0]), store.get(certificates[0].getSubjectX500Principal(), null, null));
        Assert.assertEquals(Collections.singletonList(certificates[1]), store.get(certificates[1].getSubjectX500Principal(), null, null));

        writeToCertificateFile(certificates[0]);
        Files.setLastModifiedTime(certificateFilePath, FileTime.fromMillis(1527775060000L));
        store.refresh();
        Assert.assertEquals(Collections.singletonList(certificates[0]), store.get(certificates[0].getSubjectX500Principal(), null, null));
        Assert.assertEquals(Collections.emptyList(), store.get(certificates[1].getSubjectX500Principal(), null, null));

        writeToCertificateFile(certificates[0], certificates[1]);
        Files.setLastModifiedTime(certificateFilePath, FileTime.fromMillis(1527775060000L));
        store.refresh();
        Assert.assertEquals(Collections.singletonList(certificates[0]), store.get(certificates[0].getSubjectX500Principal(), null, null));
        Assert.assertEquals(Collections.emptyList(), store.get(certificates[1].getSubjectX500Principal(), null, null));
    }

    private void writeToCertificateFile(X509Certificate... certificates) throws IOException, CryptoException {
        try (OutputStream out = Files.newOutputStream(certificateFilePath)) {
            PKI.storeCertificates(certificates, out);
        }
    }
}
