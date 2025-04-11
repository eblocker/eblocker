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