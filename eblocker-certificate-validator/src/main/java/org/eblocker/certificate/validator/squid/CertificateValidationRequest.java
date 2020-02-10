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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

public class CertificateValidationRequest extends CertificateValidationMessage {

    public static final String CERTIFICATE_VAIDATION_REQUEST_MESSAGE = "cert_validate";

    private final String protoVersion;

    private final String cipher;

    private final String host;

    private final X509Certificate[] cert;

    public CertificateValidationRequest(Long id, String protoVersion, String cipher, String host, X509Certificate[] cert, String[] errorName, String[] errorCertId,boolean useConcurrency) {
        super(id, errorName, errorCertId,useConcurrency);
        this.protoVersion = protoVersion;
        this.cipher = cipher;
        this.host = host;
        this.cert = cert;
    }

    public String getProtoVersion() {
        return protoVersion;
    }

    public String getCipher() {
        return cipher;
    }

    public String getHost() {
        return host;
    }

    public X509Certificate[] getCert() {
        return cert;
    }

    @Override
    protected String getMessage() {
        return CERTIFICATE_VAIDATION_REQUEST_MESSAGE;
    }

    @Override
    protected String getContent() {
        StringBuilder s = new StringBuilder();

        s.append("host=").append(host).append("\n");
        s.append("proto_version=").append(protoVersion).append("\n");
        s.append("cipher=").append(cipher).append("\n");

        for (int i = 0; i < cert.length; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                PKI.storeCertificate(cert[i], out);
                s.append("cert_")
                    .append(i)
                    .append("=")
                    .append(new String(out.toByteArray(), StandardCharsets.UTF_8));
                //PEM encoded certificate already contains a terminating \n

            } catch (IOException | CryptoException e) {
                throw new IllegalStateException("failed to encode certificate as PEM", e);
            }
        }

        appendContent(s);

        String content = s.toString();
        // squid does not include newline as body payload
        if (content.endsWith("\n")) {
            content = content.substring(0, content.length() - 1);
        }

        return content;
    }

}
