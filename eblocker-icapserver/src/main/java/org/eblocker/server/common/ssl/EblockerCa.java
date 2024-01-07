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

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

public class EblockerCa {
    /*
    Server certificates should have a maximum lifetime of 825 days:
    https://cabforum.org/2017/03/17/ballot-193-825-day-certificate-lifetimes/
     */
    public static final int MAX_VALIDITY_SERVER_IN_DAYS = 825;

    private final CertificateAndKey certificateAndKey;

    public EblockerCa(CertificateAndKey certificateAndKey) {
        this.certificateAndKey = certificateAndKey;
    }

    public X509Certificate getCertificate() {
        return certificateAndKey.getCertificate();
    }

    public PrivateKey getKey() {
        return certificateAndKey.getKey();
    }

    public CertificateAndKey generateServerCertificate(String commonName, Date notValidAfter, List<String> subjectAlternativeNames) throws CryptoException, IOException {
        CertificateAndKey requestAndKey = PKI.generateSelfSignedCertificateRequest(commonName, 2048, subjectAlternativeNames);
        return generateServerCertificate(requestAndKey, notValidAfter);
    }

    public CertificateAndKey generateServerCertificate(String commonName, KeyPair keyPair, Date notValidAfter, List<String> subjectAlternativeNames) throws CryptoException, IOException {
        CertificateAndKey requestAndKey = PKI.generateSelfSignedCertificateRequest(commonName, keyPair, subjectAlternativeNames);
        return generateServerCertificate(requestAndKey, notValidAfter);
    }

    private CertificateAndKey generateServerCertificate(CertificateAndKey requestAndKey, Date notValidAfter) throws CryptoException {
        X509Certificate certificate = PKI.generateTLSServerCertificate(requestAndKey.getCertificate(),  PKI.getStartDate(), notValidAfter, certificateAndKey);
        return new CertificateAndKey(certificate, requestAndKey.getKey());
    }

    /**
     * Returns the end of the maximum lifetime of a server certificate.
     * A server certificate can not be valid longer than the CA.
     * In addition its lifetime can not be longer than 825 days.
     *
     * @return
     */
    public Date getServerNotValidAfter() {
        Instant caNotAfter = certificateAndKey.getCertificate().getNotAfter().toInstant();
        Instant maxNotAfter = Instant.now().plus(MAX_VALIDITY_SERVER_IN_DAYS, ChronoUnit.DAYS);
        Instant result = maxNotAfter.isBefore(caNotAfter) ? maxNotAfter : caNotAfter;
        return Date.from(result);
    }

    public void writeToKeyStore(String alias, Path keyStorePath, char[] keyStorePassword) throws IOException, CryptoException {
        try (OutputStream keyStoreStream = Files.newOutputStream(keyStorePath)) {
            PKI.generateKeyStore(certificateAndKey, alias, keyStorePassword, keyStoreStream);
        }
    }

    public static EblockerCa loadFromKeyStore(Path keyStorePath, char[] keyStorePassword) throws PkiException {
        if (!Files.exists(keyStorePath)) {
            return null;
        }

        try (InputStream keyStoreStream = Files.newInputStream(keyStorePath)) {
            KeyStore keyStore = PKI.loadKeyStore(keyStoreStream, keyStorePassword);
            String alias = keyStore.aliases().nextElement();
            return new EblockerCa(new CertificateAndKey((X509Certificate) keyStore.getCertificate(alias),
                    (PrivateKey) keyStore.getKey(alias, keyStorePassword)));
        } catch (CryptoException | IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new PkiException("failed to load ca keystore", e);
        }
    }

    public static EblockerCa generateRootCa(String commonName, Date notBefore, Date notAfter, int caKeySize) throws CryptoException {
        CertificateAndKey certificateAndKey = PKI.generateRoot(null, commonName, notBefore, notAfter, caKeySize);
        return new EblockerCa(certificateAndKey);
    }
}
