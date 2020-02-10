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
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class PinnedCertificateValidatorTest {

    private static final boolean USE_CONCURRENCY = true;

    private static X509Certificate PINNED_CERTIFICATE;
    private static X509Certificate NOT_PINNED_CERTIFICATE;
    private static X509Certificate EXPIRE_CERTIFICATE;

    private CertificateValidator nextValidator;
    private PinnedCertificateValidator validator;

    @BeforeClass
    public static void beforeClass() throws IOException, CryptoException {
        PINNED_CERTIFICATE = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/xkcd.org.cert");
        NOT_PINNED_CERTIFICATE = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/www.microsoft.com.cert");
        EXPIRE_CERTIFICATE = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/expired.badssl.com.cert");
    }

    @Before
    public void setUp() {
        nextValidator = Mockito.mock(CertificateValidator.class);
        PinnedCertificatesStore pinnedCertificatesStore = Mockito.mock(PinnedCertificatesStore.class);
        Mockito.when(pinnedCertificatesStore.getCertificates()).thenReturn(Collections.singleton(PINNED_CERTIFICATE));
        validator = new PinnedCertificateValidator(pinnedCertificatesStore, nextValidator);
    }

    @Test
    public void testPinnedCertificate() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, null, null, null, new X509Certificate[] { PINNED_CERTIFICATE }, new String[0], new String[0], USE_CONCURRENCY);

        CertificateValidationResponse response = validator.validate(request, USE_CONCURRENCY);

        Assert.assertNotNull(response);
        Assert.assertTrue(response.isSuccess());
        Assert.assertArrayEquals(new String[0], response.getErrorCertId());
        Assert.assertArrayEquals(new String[0], response.getErrorReason());

        Mockito.verifyZeroInteractions(nextValidator);
    }

    @Test
    public void testPinnedCertificateOpenSslError() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, null, null, null, new X509Certificate[] { PINNED_CERTIFICATE }, new String[] { "X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY", "X509_V_ERR_CERT_UNTRUSTED", "X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE" }, new String[] { "cert_0", "cert_0", "cert_0" }, USE_CONCURRENCY);

        CertificateValidationResponse response = validator.validate(request, USE_CONCURRENCY);

        Assert.assertNotNull(response);
        Assert.assertTrue(response.isSuccess());
        Assert.assertArrayEquals(new String[0], response.getErrorCertId());
        Assert.assertArrayEquals(new String[0], response.getErrorReason());

        Mockito.verifyZeroInteractions(nextValidator);
    }

    @Test
    public void testNotPinnedCertificate() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, null, null, null, new X509Certificate[] { NOT_PINNED_CERTIFICATE }, new String[0], new String[0], USE_CONCURRENCY);

        validator.validate(request, USE_CONCURRENCY);

        Mockito.verify(nextValidator).validate(request, USE_CONCURRENCY);
    }

    @Test
    public void testExpiredCertificate() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, null, null, null, new X509Certificate[] { EXPIRE_CERTIFICATE }, new String[0], new String[0], USE_CONCURRENCY);

        CertificateValidationResponse response = validator.validate(request, USE_CONCURRENCY);

        Assert.assertNotNull(response);
        Assert.assertFalse(response.isSuccess());
        Assert.assertEquals(1, response.getErrorReason().length);
        Assert.assertEquals(1, response.getErrorName().length);

        Mockito.verifyZeroInteractions(nextValidator);
    }
}
