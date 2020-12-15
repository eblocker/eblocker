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

import com.google.common.collect.Sets;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class PinnedCertificatesStoreTest {

    private static final String TRUST_STORE_PASSWORD = "test";

    private static X509Certificate[] CERTIFICATES;
    private Path storePath;
    private PinnedCertificatesStore store;

    @BeforeClass
    public static void beforeClass() throws IOException, CryptoException {
        CERTIFICATES = new X509Certificate[]{
                CertificateValidatorTestUtil.loadCertificateResource("sample-certs/xkcd.org.cert")
        };
    }

    @Before
    public void setUp() throws IOException, CryptoException {
        storePath = Files.createTempFile("pinned", ".jks");
        createKeyStore(CERTIFICATES[0]);
        store = new PinnedCertificatesStore(storePath, TRUST_STORE_PASSWORD);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(storePath);
    }

    @Test
    public void testNoRefresh() {
        Assert.assertEquals(Collections.emptySet(), store.getCertificates());
    }

    @Test
    public void testRefresh() throws IOException, CryptoException {
        Assert.assertEquals(Collections.emptySet(), store.getCertificates());
        store.refresh();
        Assert.assertEquals(Collections.singleton(CERTIFICATES[0]), store.getCertificates());
        createKeyStore(CERTIFICATES);
        store.refresh();
        Assert.assertEquals(Sets.newHashSet(CERTIFICATES), store.getCertificates());
        Files.deleteIfExists(storePath);
        store.refresh();
        Assert.assertEquals(Collections.emptySet(), store.getCertificates());
    }

    @Test
    public void testCorruptStore() throws IOException {
        Files.write(storePath, new byte[4096]);
        store.refresh();
        Assert.assertEquals(Collections.emptySet(), store.getCertificates());
    }

    private void createKeyStore(X509Certificate... certificates) throws IOException, CryptoException {
        String[] aliases = new String[certificates.length];
        for (int i = 0; i < certificates.length; ++i) {
            aliases[i] = PKI.getCN(certificates[i]);
        }
        try (OutputStream out = Files.newOutputStream(storePath)) {
            PKI.generateTrustStore(certificates, aliases, TRUST_STORE_PASSWORD.toCharArray(), out);
        }
    }
}
