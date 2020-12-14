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
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CRL;
import java.security.cert.CertStoreException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLSelector;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;

public class CrlCacheCertStoreTest {

    private CrlCache crlCache;

    @Before
    public void setUp() {
        crlCache = Mockito.mock(CrlCache.class);
    }

    @Test
    public void testEngineGetCertificates() throws InvalidAlgorithmParameterException, CertStoreException {
        CrlCacheCertStore certStore = new CrlCacheCertStore(new CrlCacheCertStore.Parameters(crlCache));
        Assert.assertEquals(Collections.emptyList(), certStore.engineGetCertificates(new X509CertSelector()));
    }

    @Test
    public void testEngineGetCRLs() throws InvalidAlgorithmParameterException, IOException, CryptoException, CertStoreException {
        Mockito.when(crlCache.get("http://crl3.digicert.com/DigiCertGlobalRootCA.crl")).thenReturn(Mockito.mock(X509CRL.class));
        Mockito.when(crlCache.get("http://crl4.digicert.com/DigiCertGlobalRootCA.crl")).thenReturn(Mockito.mock(X509CRL.class));

        X509CRLSelector selector = new X509CRLSelector();
        selector.setCertificateChecking(CertificateValidatorTestUtil.loadCertificateResource("sample-certs/DigiCertSHA2SecureServerCA.cert"));

        CrlCacheCertStore certStore = new CrlCacheCertStore(new CrlCacheCertStore.Parameters(crlCache));
        Collection<? extends CRL> crls = certStore.engineGetCRLs(selector);
        Assert.assertEquals(2, crls.size());
        crls.stream().forEach(crl -> Assert.assertTrue(crl instanceof X509CRL));
    }

    @Test
    public void testEngineGetCRLsOneCrlUnavailable() throws InvalidAlgorithmParameterException, IOException, CryptoException, CertStoreException {
        Mockito.when(crlCache.get("http://crl3.digicert.com/DigiCertGlobalRootCA.crl")).thenReturn(Mockito.mock(X509CRL.class));

        X509CRLSelector selector = new X509CRLSelector();
        selector.setCertificateChecking(CertificateValidatorTestUtil.loadCertificateResource("sample-certs/DigiCertSHA2SecureServerCA.cert"));

        CrlCacheCertStore certStore = new CrlCacheCertStore(new CrlCacheCertStore.Parameters(crlCache));
        Collection<? extends CRL> crls = certStore.engineGetCRLs(selector);
        Assert.assertEquals(1, crls.size());
        crls.stream().forEach(crl -> Assert.assertTrue(crl instanceof X509CRL));
    }

    @Test(expected = CertStoreException.class)
    public void testEngineGetCRLsDistributionPointsExtractionFailure() throws InvalidAlgorithmParameterException, CertStoreException, IOException {
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        Mockito.when(certificate.getExtensionValue(Mockito.anyString())).thenReturn(new byte[]{ 2, 1, 0 });

        X509CRLSelector selector = new X509CRLSelector();
        selector.setCertificateChecking(certificate);

        CrlCacheCertStore certStore = new CrlCacheCertStore(new CrlCacheCertStore.Parameters(crlCache));
        certStore.engineGetCRLs(selector);
    }

    @Test(expected = InvalidAlgorithmParameterException.class)
    public void testMissingParameters() throws InvalidAlgorithmParameterException {
        new CrlCacheCertStore(null);
    }

    @Test(expected = InvalidAlgorithmParameterException.class)
    public void testInvalidParameters() throws InvalidAlgorithmParameterException {
        new CrlCacheCertStore(new CollectionCertStoreParameters());
    }
}
