/*
 * Copyright 2025 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.icap.transaction.processor.CaptivePortalCheckProcessor;
import org.eblocker.server.icap.transaction.processor.HtmlInjectionProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests some dependencies between processors (e.g. the order)
 */
class TransactionProcessorsModuleTest {
    private TransactionProcessorsModule module;
    private HtmlInjectionProcessor htmlInjection;
    private CaptivePortalCheckProcessor captivePortalCheck;

    @BeforeEach
    void setUp() {
        module = new TransactionProcessorsModule();
        htmlInjection = Mockito.mock(HtmlInjectionProcessor.class);
        captivePortalCheck = Mockito.mock(CaptivePortalCheckProcessor.class);
    }

    @Test
    void captivePortalCheckBeforeHtmlInjection() {
        List<TransactionProcessor> processors = module.getResponseProcessors(null, null, null, null, null,
                htmlInjection,
                null, null, null, null, null, null, null, null, null, null, null, null,
                captivePortalCheck);

        // The captive portal check processor must run before HTML injection:
        assertTrue(processors.indexOf(captivePortalCheck) < processors.indexOf(htmlInjection));
    }
}