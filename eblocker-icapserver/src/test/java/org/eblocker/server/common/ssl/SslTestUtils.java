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
package org.eblocker.server.common.ssl;

import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class SslTestUtils {

    public static final EblockerResource CA_RESOURCE = new SimpleResource("classpath:test-data/unit-test-ca.jks");
    public static final EblockerResource ALTERNATIVE_CA_RESOURCE = new SimpleResource("classpath:test-data/unit-test-alternative-ca.jks");
    public static final EblockerResource EXPIRED_CA_RESOURCE = new SimpleResource("classpath:test-data/unit-test-expired-ca.jks");
    public static final EblockerResource HTTPS_RESOURCE = new SimpleResource("classpath:test-data/unit-test-https-localhost.jks");
    public static final String UNIT_TEST_CA_PASSWORD = "unit-test";

    public static CertificateAndKey loadCertificateAndKey(EblockerResource resource, String keyStorePassword) throws Exception {
        try (InputStream is = ResourceHandler.getInputStream(resource)) {
            KeyStore keyStore = PKI.loadKeyStore(is, keyStorePassword.toCharArray());
            return getFirstEntryFromKeyStore(keyStore, keyStorePassword);
        }
    }

    private static CertificateAndKey getFirstEntryFromKeyStore(KeyStore keyStore, String keyStorePassword) throws GeneralSecurityException {
        String alias = keyStore.aliases().nextElement();
        return new CertificateAndKey((X509Certificate) keyStore.getCertificate(alias),
            (PrivateKey) keyStore.getKey(alias, keyStorePassword.toCharArray()));
    }

}
