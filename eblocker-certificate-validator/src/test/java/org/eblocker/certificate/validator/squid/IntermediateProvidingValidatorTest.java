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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class IntermediateProvidingValidatorTest {

    private X509Certificate[] chain;
    private IntermediateCertificatesStore intermediateCertificatesStore;

    private CertificateValidator nextValidator;
    private IntermediateProvidingValidator validator;

    @BeforeEach
    void setUp() {
        chain = createCertificateChain("www.site.com", "level 3 ca", "level 2 ca", "level 1 ca", "root ca");

        intermediateCertificatesStore = Mockito.mock(IntermediateCertificatesStore.class);
        Mockito.when(intermediateCertificatesStore.get(chain[1].getSubjectX500Principal(), null, null)).thenReturn(Collections.singletonList(chain[1]));
        Mockito.when(intermediateCertificatesStore.get(chain[2].getSubjectX500Principal(), null, null)).thenReturn(Collections.singletonList(chain[2]));
        Mockito.when(intermediateCertificatesStore.get(chain[3].getSubjectX500Principal(), null, null)).thenReturn(Collections.singletonList(chain[3]));

        nextValidator = Mockito.mock(CertificateValidator.class);
        validator = new IntermediateProvidingValidator(nextValidator, intermediateCertificatesStore);
    }

    @Test
    void testCompletingChainOnlyLeafCertificate() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, "protoVersion", "cipher", "host", new X509Certificate[]{ chain[0] }, new String[]{ "X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY" }, new String[]{ "cert_0" }, true);
        validator.validate(request, true);

        ArgumentCaptor<CertificateValidationRequest> captor = ArgumentCaptor.forClass(CertificateValidationRequest.class);
        Mockito.verify(nextValidator).validate(captor.capture(), Mockito.eq(true));

        Assertions.assertNotSame(request, captor.getValue());
        Assertions.assertEquals(request.getId(), captor.getValue().getId());
        Assertions.assertEquals(request.getProtoVersion(), captor.getValue().getProtoVersion());
        Assertions.assertEquals(request.getCipher(), captor.getValue().getCipher());
        Assertions.assertEquals(request.getHost(), captor.getValue().getHost());
        Assertions.assertArrayEquals(chain, captor.getValue().getCert());
        Assertions.assertEquals(0, captor.getValue().getErrorCertId().length);
        Assertions.assertEquals(0, captor.getValue().getErrorName().length);
    }

    @Test
    void testCompletingIncompleteChain() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, "protoVersion", "cipher", "host", new X509Certificate[]{ chain[0], chain[1], chain[3] }, new String[]{ "X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY" },
                new String[]{ "cert_0" }, true);
        validator.validate(request, true);

        ArgumentCaptor<CertificateValidationRequest> captor = ArgumentCaptor.forClass(CertificateValidationRequest.class);
        Mockito.verify(nextValidator).validate(captor.capture(), Mockito.eq(true));

        Assertions.assertNotSame(request, captor.getValue());
        Assertions.assertEquals(request.getId(), captor.getValue().getId());
        Assertions.assertEquals(request.getProtoVersion(), captor.getValue().getProtoVersion());
        Assertions.assertEquals(request.getCipher(), captor.getValue().getCipher());
        Assertions.assertEquals(request.getHost(), captor.getValue().getHost());
        Assertions.assertArrayEquals(chain, captor.getValue().getCert());
        Assertions.assertEquals(0, captor.getValue().getErrorCertId().length);
        Assertions.assertEquals(0, captor.getValue().getErrorName().length);
    }

    @Test
    void testCompletingAmbigiousChain() {
        X509Certificate level2reIssueCertificate = createMockCertificate(chain[2].getSubjectX500Principal(), chain[3].getSubjectX500Principal());
        Mockito.when(intermediateCertificatesStore.get(chain[2].getSubjectX500Principal(), null, null)).thenReturn(Arrays.asList(chain[2], level2reIssueCertificate));

        CertificateValidationRequest request = new CertificateValidationRequest(0L, "protoVersion", "cipher", "host", new X509Certificate[]{ chain[0] }, new String[]{ "X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY" }, new String[]{ "cert_0" }, true);
        validator.validate(request, true);

        ArgumentCaptor<CertificateValidationRequest> captor = ArgumentCaptor.forClass(CertificateValidationRequest.class);
        Mockito.verify(nextValidator).validate(captor.capture(), Mockito.eq(true));

        Assertions.assertNotSame(request, captor.getValue());
        Assertions.assertEquals(request.getId(), captor.getValue().getId());
        Assertions.assertEquals(request.getProtoVersion(), captor.getValue().getProtoVersion());
        Assertions.assertEquals(request.getCipher(), captor.getValue().getCipher());
        Assertions.assertEquals(request.getHost(), captor.getValue().getHost());
        Assertions.assertEquals(Sets.newHashSet(chain[0], chain[1], chain[2], chain[3], level2reIssueCertificate), Sets.newHashSet(Arrays.asList(captor.getValue().getCert())));
        Assertions.assertEquals(0, captor.getValue().getErrorCertId().length);
        Assertions.assertEquals(0, captor.getValue().getErrorName().length);
    }

    @Test
    void testNoError() {
        CertificateValidationRequest request = new CertificateValidationRequest(0L, "protoVersion", "cipher", "host", new X509Certificate[]{ chain[0] }, new String[0], new String[0], true);
        validator.validate(request, true);

        ArgumentCaptor<CertificateValidationRequest> captor = ArgumentCaptor.forClass(CertificateValidationRequest.class);
        Mockito.verify(nextValidator).validate(captor.capture(), Mockito.eq(true));
        Assertions.assertSame(request, captor.getValue());
    }

    private X509Certificate[] createCertificateChain(String... subjects) {
        List<X500Principal> principals = new ArrayList<>();
        for (String subject : subjects) {
            principals.add(new X500Principal("cn=" + subject));
        }
        List<X509Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < subjects.length - 1; ++i) {
            certificates.add(createMockCertificate(principals.get(i), principals.get(i + 1)));
        }
        return certificates.toArray(new X509Certificate[0]);
    }

    private X509Certificate createMockCertificate(X500Principal subject, X500Principal issuer) {
        X509Certificate certificate = Mockito.mock(X509Certificate.class, "s:" + subject.getName() + " i:" + issuer.getName());
        Mockito.when(certificate.getSubjectX500Principal()).thenReturn(subject);
        Mockito.when(certificate.getIssuerX500Principal()).thenReturn(issuer);
        return certificate;
    }

}
