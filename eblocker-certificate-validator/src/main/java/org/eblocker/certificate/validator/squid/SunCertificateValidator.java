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
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathChecker;
import java.security.cert.CertSelector;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class SunCertificateValidator implements CertificateValidator {

    private static final Logger LOG = LoggerFactory.getLogger(SunCertificateValidator.class);

    private static final String DEFAULT_CERT_PATH_BUILDER_TYPE = "PKIX";

    private final OcspCache ocspCache;
    private final ExecutorService executorService;
    private final KeyStore trustStore;
    private final boolean preferCrls;
    private final ThreadLocal<CertPathBuilder> certPathBuilder = ThreadLocal.withInitial(this::createCertPathBuilder);
    private final ThreadLocal<CertPathChecker> revocationChecker = ThreadLocal.withInitial(this::createRevocationChecker);
    private final CertStore crlStore;

    public SunCertificateValidator(Path certificatesFilePath, boolean preferCrls, CertStore crlStore, OcspCache ocspCache, ExecutorService executorService) {
        this.preferCrls = preferCrls;
        this.crlStore = crlStore;
        this.ocspCache = ocspCache;
        this.executorService = executorService;
        trustStore = createTrustStore(certificatesFilePath);
    }

    public CertificateValidationResponse validate(CertificateValidationRequest request, boolean useConcurrency) {
        if (request == null)
            return null;

        if (request.getCert() == null || request.getCert().length == 0) {
            LOG.error("Cannot validate: Array of certs is empty");
            return new CertificateValidationResponse(request, useConcurrency, false);
        }

        try {
            LOG.info("validating request {} for {}", request.getId(), request.getCert()[0].getSubjectDN());

            PKIXRevocationChecker rc = (PKIXRevocationChecker) revocationChecker.get();
            if (ocspCache != null) {
                Map<X509Certificate, byte[]> ocspResponses = getOcspResponses(request.getCert());
                rc.setOcspResponses(ocspResponses);
            }

            CertStore certStore = createCertStore(request.getCert());

            PKIXBuilderParameters builderParameters = new PKIXBuilderParameters(trustStore, getCertSelector(request.getCert()[0]));
            builderParameters.setRevocationEnabled(true);
            builderParameters.setCertPathCheckers(Collections.singletonList(rc));
            builderParameters.addCertStore(certStore);
            builderParameters.addCertStore(crlStore);
            certPathBuilder.get().build(builderParameters);

            boolean success = hasErrors(request); //If we got errors passed from OpenSSL validation, we must not say we trust this certificate!

            return new CertificateValidationResponse(request.getId(), request.getErrorName(), request.getErrorCertId(), useConcurrency, success, null);

        } catch (CertPathBuilderException e) { //NOSONAR: this exception is expected to be thrown in case of a non-valid certificate, no need to log it
            LOG.error("Certificate is not valid - " + e.getMessage());
            return new CertificateValidationResponse(request, useConcurrency, request.getErrorName());

        } catch (KeyStoreException | InvalidAlgorithmParameterException e) {
            String msg = "Cannot validate certificate";
            LOG.error(msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }

    private boolean hasErrors(CertificateValidationRequest request) {
        if (request != null) {
            if (request.getErrorName() != null && request.getErrorCertId() != null) {
                return (request.getErrorName().length == 0) && (request.getErrorCertId().length == 0);
            } else {
                return true;
            }
        }
        return false;
    }

    private Map<X509Certificate, byte[]> getOcspResponses(X509Certificate[] certs) {
        Future<byte[]>[] responseFutures = new Future[certs.length - 1];
        for (int i = 0; i < certs.length - 1; ++i) {
            X509Certificate certificate = certs[i];
            X509Certificate issuerCertificate = certs[i + 1];
            responseFutures[i] = executorService.submit(() -> ocspCache.getOcspResponse(certificate, issuerCertificate));
        }

        Map<X509Certificate, byte[]> ocspResponses = new HashMap<>();
        for (int i = 0; i < certs.length - 1; ++i) {
            try {
                byte[] cachedResponse = responseFutures[i].get();
                if (cachedResponse != null) {
                    ocspResponses.put(certs[i], cachedResponse);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                LOG.warn("unexpected error", e);
            }
        }
        return ocspResponses;
    }

    private CertPathBuilder createCertPathBuilder() {
        try {
            return CertPathBuilder.getInstance(DEFAULT_CERT_PATH_BUILDER_TYPE);

        } catch (NoSuchAlgorithmException e) {
            String msg = "Cannot create cert path builder " + DEFAULT_CERT_PATH_BUILDER_TYPE;
            LOG.error(msg, e);
            throw new IllegalArgumentException(msg, e);
        }

    }

    private CertPathChecker createRevocationChecker() {
        PKIXRevocationChecker revocationChecker = (PKIXRevocationChecker) certPathBuilder.get().getRevocationChecker();
        if (preferCrls) {
            revocationChecker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.PREFER_CRLS));
        }
        return revocationChecker;
    }

    private KeyStore createTrustStore(Path certificatesFilePath) {
        try (InputStream in = Files.newInputStream(certificatesFilePath)) {
            X509Certificate[] certificates = PKI.loadCertificates(in);
            return PKI.generateTrustStore(certificates);
        } catch (CryptoException | IOException e) {
            LOG.error("failed to load certificates", e);
            throw new IllegalArgumentException("failed to load certificates", e);
        }
    }

    private CertSelector getCertSelector(X509Certificate certificate) {
        X509CertSelector selector = new X509CertSelector();
        selector.setCertificate(certificate);
        return selector;
    }

    private CertStore createCertStore(X509Certificate[] certificates) {
        try {
            CertStoreParameters params = new CollectionCertStoreParameters(Arrays.asList(certificates));
            return CertStore.getInstance("Collection", params);

        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            String msg = "Cannot create cert store";
            LOG.error(msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }

}
