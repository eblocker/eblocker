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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateEncodingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CachingValidator implements CertificateValidator {

    private static final Logger log = LoggerFactory.getLogger(CachingValidator.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private final CertificateValidator validator;
    private final Cache<byte[], CertificateValidationResponse> cache;

    public CachingValidator(int maxSize, int concurrencyLevel, int ttl, CertificateValidator validator) {
        this.validator = validator;
        this.cache = CacheBuilder.newBuilder()
            .concurrencyLevel(concurrencyLevel)
            .maximumSize(maxSize)
            .expireAfterWrite(ttl, TimeUnit.SECONDS)
            .recordStats()
            .build();
    }

    @Override
    public CertificateValidationResponse validate(CertificateValidationRequest request, boolean useConcurrency) {
        try {
            CertificateValidationResponse response = cache.get(request.getCert()[0].getEncoded(), () -> loadResponse(request, useConcurrency));
            if (response.getId().equals(request.getId())) {
                return response;
            }
            return new CertificateValidationResponse(request.getId(), response.getErrorName(), response.getErrorCertId(), useConcurrency, response.isSuccess(), response.getErrorReason());
        } catch (CertificateEncodingException | ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                // this is expected as CertificateValidator.validate does not define any exceptions
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException("unexpected exception while validating " + validator.getClass(), e);
        }
    }

    public void logStats() {
        CacheStats stats = cache.stats();
        STATUS_LOG.info("size: {} hits: {} loads: {} miss: {} evictions: {}", cache.size(), stats.hitCount(), stats.loadCount(), stats.missCount(), stats.evictionCount());
    }

    private CertificateValidationResponse loadResponse(CertificateValidationRequest request, boolean useConcurrency) {
        log.info("loading entry for {} ... ", request.getCert()[0].getSubjectDN());
        long start = System.currentTimeMillis();
        CertificateValidationResponse response = validator.validate(request, useConcurrency);
        long elapsed = System.currentTimeMillis() - start;
        log.info("loaded entry for {} in {} ms.", request.getCert()[0].getSubjectDN(), elapsed);
        return response;
    }
}
