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
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicLong;


public class CertificateValidatorTestUtil {

    private static AtomicLong nextRequestId = new AtomicLong();

    public static CertificateValidationRequest createValidRequest(boolean useConcurrency) throws IOException, CryptoException {
        return createRequest(useConcurrency, "www.microsoft.com", "www.microsoft.com.cert", "MicrosoftITTLSCA5.cert");
    }

    public static CertificateValidationRequest createInvalidRequest(boolean useConcurrency) throws IOException, CryptoException {
        return createRequest(useConcurrency, "www.microsoft.com", "Verisign_Revoked.cert", "Verisign_Class_3_EV_SSL_CA.cert");
    }

    public static CertificateValidationRequest createRevokedRequest(boolean useConcurrency) throws IOException, CryptoException {
        return createRequest(useConcurrency, "revoked.badssl.com", "revoked.badssl.com.cert", "DigiCertSHA2SecureServerCA.cert");
    }

    public static CertificateValidationRequest createInvalidRequestWithErrors(boolean useConcurrency) throws IOException, CryptoException {
        CertificateValidationRequest request = createValidRequest(useConcurrency);
        String[] errorNames = new String[]{"SQUID_X509_V_ERR_DOMAIN_MISMATCH"};
        String[] errorCerts = new String[]{"cert_0"};

        CertificateValidationRequest requestWithErrors = new CertificateValidationRequest(getNextId(useConcurrency), null, null, request.getHost(), request.getCert(), errorNames, errorCerts, useConcurrency);
        return requestWithErrors;
    }

    public static CertificateValidationRequest createRequest(boolean useConcurrency, String host, String... resources) throws IOException, CryptoException {
        X509Certificate[] certificates = new X509Certificate[resources.length];
        for (int i = 0; i < resources.length; i++) {
            certificates[i] = loadCertificateResource("sample-certs/" + resources[i]);
        }
        return new CertificateValidationRequest(getNextId(useConcurrency), null, null, host, certificates, null, null, useConcurrency);
    }

    public static X509Certificate loadCertificateResource(String resource) throws IOException, CryptoException {
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(resource);
        return PKI.loadCertificate(in);
    }

    public static byte[] loadResource(String resource) throws IOException {
        return ByteStreams.toByteArray(ClassLoader.getSystemClassLoader().getResourceAsStream(resource));
    }

    private static Long getNextId(boolean useConcurrency) {
        return useConcurrency ? nextRequestId.getAndIncrement() : null;
    }
}
