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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class CachingValidatorTest {

    private final int ttl = 3;

    private CertificateValidator mockValidator;
    private CachingValidator cachingValidator;

    @BeforeEach
    void setup() {
        mockValidator = Mockito.mock(CertificateValidator.class);
        cachingValidator = new CachingValidator(3, 1, ttl, mockValidator);
    }

    @Test
    void testCaching() throws CertificateEncodingException {
        // setup mocks
        List<X509Certificate> certificates = new ArrayList<>();
        List<CertificateValidationRequest> requests = new ArrayList<>();
        List<CertificateValidationResponse> responses = new ArrayList<>();
        int size = 3;
        for (int i = 0; i <= size; ++i) {
            certificates.add(createCertificate(new byte[i]));
            requests.add(createRequest(i, certificates.get(i)));
            responses.add(createResponse(requests.get(i)));
        }
        Mockito.when(mockValidator.validate(Mockito.any(CertificateValidationRequest.class), Mockito.anyBoolean()))
                .thenAnswer(im -> responses.get(((CertificateValidationRequest) im.getArgument(0)).getId().intValue()));

        InOrder validatorInOrder = Mockito.inOrder(mockValidator);

        // query validator to fill cache
        for (int i = 0; i < size; ++i) {
            CertificateValidationResponse response = cachingValidator.validate(requests.get(i), false);
            assertSame(response, responses.get(i));
            validatorInOrder.verify(mockValidator).validate(requests.get(i), false);
        }

        // query validator again verifying cached results are returned
        for (int i = 0; i < size; ++i) {
            CertificateValidationResponse response = cachingValidator.validate(requests.get(i), false);
            assertSame(response, responses.get(i));
            validatorInOrder.verifyNoMoreInteractions();
        }

        // issue a new query which should evict the first entry
        CertificateValidationResponse nonCachedResponse = cachingValidator.validate(requests.get(size), false);
        assertSame(nonCachedResponse, responses.get(size));
        validatorInOrder.verify(mockValidator).validate(requests.get(size), false);

        // check entries 2 .. size are cached
        for (int i = 1; i <= size; ++i) {
            CertificateValidationResponse response = cachingValidator.validate(requests.get(i), false);
            assertSame(response, responses.get(i));
            validatorInOrder.verifyNoMoreInteractions();
        }

        // check first entry is not anymore in cache
        CertificateValidationResponse evictedResponse = cachingValidator.validate(requests.get(0), false);
        assertSame(evictedResponse, responses.get(0));
        validatorInOrder.verify(mockValidator).validate(requests.get(0), false);
    }

    @Test
    void testExpiration() throws InterruptedException, CertificateEncodingException {
        CertificateValidationRequest request = createRequest(0, createCertificate(new byte[0]));
        CertificateValidationResponse response = createResponse(request);
        Mockito.when(mockValidator.validate(request, false)).thenReturn(response);

        InOrder validatorInOrder = Mockito.inOrder(mockValidator);

        // issue query which will be cached
        cachingValidator.validate(request, false);
        validatorInOrder.verify(mockValidator).validate(request, false);

        // wait until expiration
        Thread.sleep(ttl * 1000);

        // issue query which must be refreshed
        cachingValidator.validate(request, false);
        validatorInOrder.verify(mockValidator).validate(request, false);
    }

    private CertificateValidationRequest createRequest(long id, X509Certificate certificate) {
        return new CertificateValidationRequest(id, null, null, null, new X509Certificate[]{ certificate }, null, null, false);
    }

    private CertificateValidationResponse createResponse(CertificateValidationRequest request) {
        return new CertificateValidationResponse(request.getId(), null, null, false, false, null);
    }

    private X509Certificate createCertificate(byte[] encoded) throws CertificateEncodingException {
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        Mockito.when(certificate.getEncoded()).thenReturn(encoded);
        return certificate;
    }
}
