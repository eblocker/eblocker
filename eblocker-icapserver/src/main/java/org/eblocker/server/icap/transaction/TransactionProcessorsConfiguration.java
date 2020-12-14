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
package org.eblocker.server.icap.transaction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.icap.transaction.processor.RuntimeLoggingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class TransactionProcessorsConfiguration {
    private static final Logger log = LoggerFactory.getLogger(TransactionProcessorsConfiguration.class);

    private static final String[] NO_FEATURES = new String[0];

    private final ProductInfoService productInfoService;
    private final boolean optimizeLogEnabled;

    private final List<TransactionProcessor> availableRequestProcessors;
    private final List<TransactionProcessor> availableResponseProcessors;

    private List<TransactionProcessor> enabledRequestProcessors;
    private List<TransactionProcessor> enabledResponseProcessors;
    private String[] productFeatures;

    @Inject
    public TransactionProcessorsConfiguration(
            ProductInfoService productInfoService,
            @Named("requestProcessors") List<TransactionProcessor> requestProcessors,
            @Named("responseProcessors") List<TransactionProcessor> responseProcessors
    ) {
        this.productInfoService = productInfoService;
        this.optimizeLogEnabled = LoggerFactory.getLogger("OPTIMIZE").isInfoEnabled();
        this.availableRequestProcessors = requestProcessors;
        this.availableResponseProcessors = responseProcessors;
        refreshEnabledProcessors();
    }

    public List<TransactionProcessor> getRequestProcessors() {
        if (needsEnabledProcessorRefresh()) {
            refreshEnabledProcessors();
        }
        return enabledRequestProcessors;
    }

    public List<TransactionProcessor> getResponseProcessors() {
        if (needsEnabledProcessorRefresh()) {
            refreshEnabledProcessors();
        }
        return enabledResponseProcessors;
    }

    private boolean needsEnabledProcessorRefresh() {
        return !Arrays.equals(productFeatures, getEnabledFeatures());
    }

    private String[] getEnabledFeatures() {
        ProductInfo productInfo = productInfoService.get();
        if (productInfo == null) {
            return NO_FEATURES;
        }
        return productInfoService.get().getProductFeatures();
    }

    private void refreshEnabledProcessors() {
        productFeatures = getEnabledFeatures();

        enabledRequestProcessors = availableRequestProcessors.stream().filter(this::isEnabled).map(this::wrapLogProcessor).collect(Collectors.toList());
        log.debug("enabled request processors: ");
        enabledRequestProcessors.stream().forEach(p -> log.info("  {}", p.getClass()));

        enabledResponseProcessors = availableResponseProcessors.stream().filter(this::isEnabled).map(this::wrapLogProcessor).collect(Collectors.toList());
        log.debug("enabled response processors: ");
        enabledResponseProcessors.stream().forEach(p -> log.info("  {}", p.getClass()));
    }

    private boolean isEnabled(TransactionProcessor processor) {
        RequireFeature requireFeature = processor.getClass().getAnnotation(RequireFeature.class);
        if (requireFeature == null) {
            return true;
        }

        for (String featureName : productFeatures) {
            if (featureName.equals(requireFeature.value().name())) {
                return true;
            }
        }

        return false;
    }

    private TransactionProcessor wrapLogProcessor(TransactionProcessor processor) {
        if (!optimizeLogEnabled) {
            return processor;
        }

        return new RuntimeLoggingProcessor(processor);
    }
}
