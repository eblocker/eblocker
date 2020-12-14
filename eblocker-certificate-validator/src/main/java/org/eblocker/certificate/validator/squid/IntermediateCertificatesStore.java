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

import javax.security.auth.x500.X500Principal;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntermediateCertificatesStore {
    private static final Logger log = LoggerFactory.getLogger(IntermediateCertificatesStore.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private final Path certificatesFilePath;

    private long lastUpdate = 0;
    private volatile Map<X500Principal, List<X509Certificate>> intermediateCertificatesBySubject = new HashMap<>();

    public IntermediateCertificatesStore(Path certificatesFilePath) {
        this.certificatesFilePath = certificatesFilePath;
    }

    public List<X509Certificate> get(X500Principal subject, BigInteger serialNumber, byte[] keyId) {
        return intermediateCertificatesBySubject.getOrDefault(subject, Collections.emptyList())
                .stream()
                .filter(c -> serialNumber == null || serialNumber.equals(c.getSerialNumber()))
                .filter(c -> keyId == null || Arrays.equals(keyId, PKI.getSubjectKeyIdentifier(c)))
                .collect(Collectors.toList());
    }

    public void refresh() {
        try {
            if (!Files.exists(certificatesFilePath)) {
                STATUS_LOG.warn("intermediate certificates file {} not found", certificatesFilePath);
                return;
            }
            long lastModifiedTime = Files.getLastModifiedTime(certificatesFilePath).toMillis();
            log.debug("last modified: {} last update: {}", lastModifiedTime, lastUpdate);
            if (lastModifiedTime > lastUpdate) {
                log.debug("certificate file modified.");
                updateCertificates();
                lastUpdate = lastModifiedTime;
            } else {
                log.debug("certificate file not modified.");
            }
        } catch (Exception e) {
            log.warn("failed to update intermediate certificates", e);
        }
    }

    private void updateCertificates() throws IOException, CryptoException {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(certificatesFilePath))) {
            X509Certificate[] certificates = PKI.loadCertificates(in);
            intermediateCertificatesBySubject = Stream.of(certificates).collect(Collectors.groupingBy(X509Certificate::getSubjectX500Principal));
            STATUS_LOG.info("loaded {} intermediate certificates.", certificates.length);
        }
    }
}
