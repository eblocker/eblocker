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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class PinnedCertificatesStore {
    private static final Logger log = LoggerFactory.getLogger(PinnedCertificatesStore.class);

    private final Path trustStorePath;
    private final char[] trustStorePassword;

    private long lastUpdate = 0;
    private Set<X509Certificate> certificates = Collections.emptySet();

    public PinnedCertificatesStore(Path trustStorePath, String trustStorePassword) {
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword.toCharArray();
    }

    public Set<X509Certificate> getCertificates() {
        return certificates;
    }

    public void refresh() {
        try {
            if (!Files.exists(trustStorePath)) {
                log.debug("pinned certificates store {} not found", trustStorePath);
                certificates = Collections.emptySet();
                return;
            }
            long lastModifiedTime = Files.getLastModifiedTime(trustStorePath).toMillis();
            log.debug("last modified: {} last update: {}", lastModifiedTime, lastUpdate);
            if (lastModifiedTime > lastUpdate) {
                log.debug("pinned certificates store modified.");
                updateCertificates();
                lastUpdate = lastModifiedTime;
            } else {
                log.debug("pinned certificates store not modified.");
            }
        } catch (Exception e) {
            log.warn("failed to update pinned certificates", e);
        }
    }

    private void updateCertificates() throws IOException, CryptoException {
        try (InputStream in = Files.newInputStream(trustStorePath)) {
            certificates = Arrays.stream(PKI.loadTrustStore(in, trustStorePassword)).collect(Collectors.toSet());
            log.debug("loaded {} pinned certificates", certificates.size());
            for (X509Certificate certificate : certificates) {
                log.debug("dn: {} serial: {} issuer: {} not before: {} not after: {}", certificate.getSubjectDN(),
                        certificate.getSerialNumber(), certificate.getIssuerDN(), certificate.getNotBefore(),
                        certificate.getNotAfter());
            }
        }
    }
}
