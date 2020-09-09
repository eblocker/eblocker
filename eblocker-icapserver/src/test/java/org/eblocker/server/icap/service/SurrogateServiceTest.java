package org.eblocker.server.icap.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurrogateServiceTest {

    SurrogateService surrogateService = new SurrogateService();

    @Test
    public void testReadSurrogates() {
        Map<String, String> urlToSurrogate = surrogateService.getUrlToSurrogate();
        assertTrue(urlToSurrogate.size() > 5);
        assertEquals("amazon_ads.js", urlToSurrogate.get("amazon-adsystem.com/aax2/amzn_ads.js"));
    }

    @Test
    public void testNoMatch() {
        assertFalse(surrogateService.surrogateForBlockedUrl("foo.com").isPresent());
    }

    @Test
    public void testExactMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("amazon-adsystem.com/aax2/amzn_ads.js").isPresent());
        assertTrue(surrogateService.surrogateForBlockedUrl("scorecardresearch.com/beacon.js").isPresent());
    }

    @Test
    public void testSubdomainMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("something.amazon-adsystem.com/aax2/amzn_ads.js").isPresent());
    }

    @Test
    public void testAll() {
        surrogateService.getUrlToSurrogate().entrySet().forEach(e -> {
            assertTrue(surrogateService.surrogateForBlockedUrl(e.getKey()).isPresent());
        });
    }
}
