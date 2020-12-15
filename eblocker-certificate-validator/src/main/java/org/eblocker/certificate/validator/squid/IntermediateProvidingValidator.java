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

import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.eblocker.crypto.pki.PKI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntermediateProvidingValidator implements CertificateValidator {
    private static final Logger log = LoggerFactory.getLogger(IntermediateProvidingValidator.class);

    private final CertificateValidator nextValidator;
    private final IntermediateCertificatesStore intermediateCertificatesStore;

    public IntermediateProvidingValidator(CertificateValidator nextValidator, IntermediateCertificatesStore intermediateCertificatesStore) {
        this.nextValidator = nextValidator;
        this.intermediateCertificatesStore = intermediateCertificatesStore;
    }

    @Override
    public CertificateValidationResponse validate(CertificateValidationRequest request, boolean useConcurrency) {
        if (!possibleMissesIntermediateCertificate(request)) {
            log.debug("passing on request {} for {}", request.getId(), request.getCert()[0].getSubjectDN());
            return nextValidator.validate(request, useConcurrency);
        }

        log.debug("trying to fill in intermediate certificates to request {} for {}", request.getId(), request.getCert()[0].getSubjectDN());
        X509Certificate[] chain = addIntermediateCertifcates(request.getCert());
        CertificateValidationRequest completedRequest = new CertificateValidationRequest(request.getId(),
                request.getProtoVersion(), request.getCipher(), request.getHost(), chain, new String[0], new String[0],
                useConcurrency);
        return nextValidator.validate(completedRequest, useConcurrency);
    }

    private boolean possibleMissesIntermediateCertificate(CertificateValidationRequest request) {
        for (String errorName : request.getErrorName()) {
            if ("X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY".equals(errorName)) {
                return true;
            }
        }
        return false;
    }

    private X509Certificate[] addIntermediateCertifcates(X509Certificate[] certificates) {
        Deque<X509Certificate> checkCertificates = new ArrayDeque<>();
        Map<X500Principal, X509Certificate> certificatesBySubject = new HashMap<>();
        for (X509Certificate certificate : certificates) {
            checkCertificates.add(certificate);
            addEntry(certificatesBySubject, certificate);
        }

        List<X509Certificate> completeChain = new ArrayList<>();
        while (!checkCertificates.isEmpty()) {
            X509Certificate certificate = checkCertificates.pop();
            completeChain.add(certificate);

            X500Principal issuer = certificate.getIssuerX500Principal();
            AuthorityKeyIdentifier authorityKeyIdentifier = PKI.getAuthorityKeyIdentifier(certificate);
            BigInteger issuerSerialNumber = authorityKeyIdentifier != null ? authorityKeyIdentifier.getAuthorityCertSerialNumber() : null;
            byte[] issuerKeyId = authorityKeyIdentifier != null ? authorityKeyIdentifier.getKeyIdentifier() : null;
            if (!certificatesBySubject.containsKey(issuer)) {
                List<X509Certificate> intermediateCertificates = intermediateCertificatesStore.get(issuer, issuerSerialNumber, issuerKeyId);
                for (X509Certificate intermediateCertificate : intermediateCertificates) {
                    checkCertificates.push(intermediateCertificate);
                    addEntry(certificatesBySubject, intermediateCertificate);
                }
            }
        }

        X509Certificate[] completeChainArray = completeChain.toArray(new X509Certificate[0]);
        if (log.isDebugEnabled()) {
            logChain("original", certificates);
            logChain("complete", completeChainArray);
        }

        return completeChainArray;
    }

    private void addEntry(Map<X500Principal, X509Certificate> certificatesBySubject, X509Certificate certificate) {
        X500Principal subject = certificate.getSubjectX500Principal();
        if (subject != null) {
            certificatesBySubject.put(subject, certificate);
        }
    }

    private void logChain(String name, X509Certificate[] certificates) {
        log.debug("-- {} -------", name);
        for (X509Certificate certificate : certificates) {
            log.debug("s: {}", certificate.getSubjectX500Principal().getName());
            log.debug("i: {}", certificate.getIssuerX500Principal().getName());
        }
        log.debug("-------------", name);
    }
}
