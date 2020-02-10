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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

public class PinnedCertificateValidator implements CertificateValidator {

    private static final Logger log = LoggerFactory.getLogger(PinnedCertificateValidator.class);

    private final PinnedCertificatesStore store;
    private final CertificateValidator nextValidator;

    public PinnedCertificateValidator(PinnedCertificatesStore store, CertificateValidator nextValidator) {
        this.store = store;
        this.nextValidator = nextValidator;
    }

    @Override
    public CertificateValidationResponse validate(CertificateValidationRequest request, boolean useConcurrency) {
        X509Certificate certificate = request.getCert()[0];
        log.debug("validating request {} for {}", request.getId(), certificate.getSubjectDN());

        if (!checkValidity(certificate)) {
            log.info("request {} certificate {} is invalid", request.getId(), certificate.getSubjectDN());
            return new CertificateValidationResponse(request, useConcurrency, false);
        }

        if (store.getCertificates().contains(certificate)) {
            log.info("request {} certificate {} is pinned", request.getId(), certificate.getSubjectDN());
            return new CertificateValidationResponse(request.getId(), new String[0], new String[0], useConcurrency, true, new String[0]);
        }

        log.debug("request {} certificate {} is not pinned, continuing with next validator {}", request.getId(), certificate.getSubjectDN(), nextValidator.getClass().getName());
        return nextValidator.validate(request, useConcurrency);
    }

    private boolean checkValidity(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            return true;
        } catch (CertificateExpiredException e) {
            log.warn("certificate {} has expired", e);
        } catch (CertificateNotYetValidException e) {
            log.warn("certificate {} not yet valid", e);
        }
        return false;
    }

}
