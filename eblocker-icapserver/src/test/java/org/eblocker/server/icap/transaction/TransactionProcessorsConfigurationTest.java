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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.icap.transaction.processor.RuntimeLoggingProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

public class TransactionProcessorsConfigurationTest {

    private ProductInfoService productInfoService;
    private List<TransactionProcessor> requestProcessors;
    private List<TransactionProcessor> responseProcessors;

    private TransactionProcessorsConfiguration configuration;

    private Level optimizeLevel;

    @Before
    public void setup() {
        requestProcessors = Arrays.asList(
            new UnrestrictedProcessor(),
            new RestrictedProcessor(),
            new UnrestrictedProcessor());

        responseProcessors = Arrays.asList(
            new RestrictedProcessor(),
            new UnrestrictedProcessor(),
            new RestrictedProcessor());

        productInfoService = Mockito.mock(ProductInfoService.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[0]);

        // save log configuration and overwrite to have consistent default
        Logger logger = Logger.getLogger("OPTIMIZE");
        optimizeLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
    }

    @After
    public void tearDown() {
        // restore previous log configuration
        Logger logger = Logger.getLogger("OPTIMIZE");
        logger.setLevel(optimizeLevel);
    }

    @Test
    public void testUnrestricted() {
        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);
        assertUnrestricted();
    }

    @Test
    public void testRestricted() {
        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[]{ ProductFeature.PRO.name() });
        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);
        assertRestricted();
    }

    @Test
    public void testUnregisteredDevice() {
        Mockito.when(productInfoService.get()).thenReturn(null);
        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);
        assertUnrestricted();
    }

    @Test
    public void testFeatureChange() {
        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);
        assertUnrestricted();

        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[]{ ProductFeature.PRO.name() });
        assertRestricted();

        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[0]);
        assertUnrestricted();
    }

    @Test
    public void testNoFeatureChange() {
        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);
        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[]{ ProductFeature.PRO.name() });
        List<TransactionProcessor> enabledRequestProcessors = configuration.getRequestProcessors();
        List<TransactionProcessor> enabledResponseProcessors = configuration.getResponseProcessors();

        Assert.assertEquals(enabledRequestProcessors, configuration.getRequestProcessors());
        Assert.assertEquals(enabledResponseProcessors, configuration.getResponseProcessors());
    }

    private void assertUnrestricted() {
        Assert.assertNotNull(configuration.getRequestProcessors());
        Assert.assertEquals(2, configuration.getRequestProcessors().size());
        Assert.assertEquals(requestProcessors.get(0), configuration.getRequestProcessors().get(0));
        Assert.assertEquals(requestProcessors.get(2), configuration.getRequestProcessors().get(1));

        Assert.assertNotNull(configuration.getResponseProcessors());
        Assert.assertEquals(1, configuration.getResponseProcessors().size());
        Assert.assertEquals(responseProcessors.get(1), configuration.getResponseProcessors().get(0));
    }

    private void assertRestricted() {
        Assert.assertEquals(requestProcessors, configuration.getRequestProcessors());
        Assert.assertEquals(responseProcessors, configuration.getResponseProcessors());
    }

    @Test
    public void testLoggingProcessors() {
        Mockito.when(productInfoService.get().getProductFeatures()).thenReturn(new String[]{ ProductFeature.PRO.name() });

        Logger logger = Logger.getLogger("OPTIMIZE");
        logger.setLevel(Level.INFO);

        configuration = new TransactionProcessorsConfiguration(productInfoService, requestProcessors, responseProcessors);

        Assert.assertNotNull(configuration.getRequestProcessors());
        Assert.assertEquals(3, configuration.getRequestProcessors().size());
        Assert.assertFalse(configuration.getRequestProcessors().stream().filter(p -> !(p instanceof RuntimeLoggingProcessor)).findAny().isPresent());

        Assert.assertNotNull(configuration.getResponseProcessors());
        Assert.assertEquals(3, configuration.getResponseProcessors().size());
        Assert.assertFalse(configuration.getResponseProcessors().stream().filter(p -> !(p instanceof RuntimeLoggingProcessor)).findAny().isPresent());
    }

    private class UnrestrictedProcessor implements TransactionProcessor {
        @Override
        public boolean process(Transaction transaction) {
            return false;
        }
    }

    @RequireFeature(ProductFeature.PRO)
    private class RestrictedProcessor implements TransactionProcessor {
        @Override
        public boolean process(Transaction transaction) {
            return false;
        }
    }
}
