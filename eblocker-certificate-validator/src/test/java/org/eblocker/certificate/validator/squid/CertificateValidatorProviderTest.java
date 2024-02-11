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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertStore;

class CertificateValidatorProviderTest {

    private Provider provider;

    @BeforeEach
    void setUp() {
        provider = new CertificateValidatorProvider();
        Security.addProvider(provider);
    }

    @AfterEach
    void tearDown() {
        Security.removeProvider(provider.getName());
    }

    @Test
    void testProvider() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        CertStore certStore = CertStore.getInstance("CrlCacheStore", new CrlCacheCertStore.Parameters(Mockito.mock(CrlCache.class)));
        Assertions.assertNotNull(certStore);
        Assertions.assertEquals(provider, certStore.getProvider());
        Assertions.assertEquals("CrlCacheStore", certStore.getType());
    }

}
