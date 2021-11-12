/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.filter.content;

import org.eblocker.server.icap.service.ScriptletService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.stream.Stream;

public class ContentFilterServiceTest {
    private ContentFilterService service;
    private ScriptletService scriptletService;
    private final int cacheSize = 50;

    @Before
    public void setUp() throws Exception {
        scriptletService = Mockito.mock(ScriptletService.class);
        service = new ContentFilterService(scriptletService, "display: none;", cacheSize);

        Stream<String> filters = Stream.of(
                "google.*##.ads",
                "google.*##+js(say, hello)"
        );
        Mockito.when(scriptletService.resolve("say, hello")).thenReturn("alert('hello')\n");
        service.setFilterList(getFilterList(filters));
    }

    @Test
    public void testMatch() {
        String expected = "<style>\n"
                + ".ads {display: none;}\n"
                + "</style>\n"
                + "<script type=\"text/javascript\">\n"
                + "alert('hello')\n"
                + "</script>\n";
        Assert.assertEquals(expected, service.getHtmlToInject("www.google.com"));
    }

    @Test
    public void testNoMatch() {
        Assert.assertEquals("", service.getHtmlToInject("www.example.com"));
    }

    @Test
    public void testResultIsCached() throws IOException {
        service.getHtmlToInject("www.google.com");
        service.getHtmlToInject("www.google.com");
        Mockito.verify(scriptletService, Mockito.atMostOnce()).resolve("say, hello");
    }

    @Test
    public void testResultCacheIsLimited() throws IOException {
        service.getHtmlToInject("www.google.com");
        // fill the cache with other domains
        for (int i = 0; i < cacheSize; i++) {
            service.getHtmlToInject("www" + i + ".example.com");
        }
        service.getHtmlToInject("www.google.com"); // not in the cache any more
        Mockito.verify(scriptletService, Mockito.atLeast(2)).resolve("say, hello");
    }

    @Test
    public void testCacheIsClearedForNewFilters() {
        Assert.assertFalse(service.getHtmlToInject("www.google.com").isEmpty());
        service.setFilterList(getFilterList(Stream.of("example.com##.ads")));
        Assert.assertTrue(service.getHtmlToInject("www.google.com").isEmpty());
    }

    private ContentFilterList getFilterList(Stream<String> filters) {
        return new ContentFilterList(new ContentFilterParser().parse(filters));
    }
}