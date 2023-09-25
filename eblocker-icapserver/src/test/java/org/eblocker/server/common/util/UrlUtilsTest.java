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
package org.eblocker.server.common.util;

import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UrlUtilsTest {

    @Test
    public void testGetHostname() {
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com/"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com:80"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com:80/"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com:80/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://www.example.com//index.html"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com/"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com:80"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com:80/"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com:80/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("http://user:password@www.example.com//index.html"));

        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com/"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com:80"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com:80/"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com:80/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://www.example.com//index.html"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com/"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com:80"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com:80/"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com:80/hello?foo=bar"));
        assertEquals("www.example.com", UrlUtils.getHostname("https://user:password@www.example.com//index.html"));
    }

    @Test
    public void testIsUkStyleDomain() {
        assertTrue(UrlUtils.isUkStyleTdl("www.guardian.co.uk"));
        assertTrue(UrlUtils.isUkStyleTdl("foobar.guardian.co.uk"));
        assertTrue(UrlUtils.isUkStyleTdl("guardian.co.uk"));
        assertTrue(UrlUtils.isUkStyleTdl("guardian.com.au"));

        assertFalse(UrlUtils.isUkStyleTdl("www.brightmammoth.com"));
        assertFalse(UrlUtils.isUkStyleTdl("www.brightmammoth.de"));
        assertFalse(UrlUtils.isUkStyleTdl("www.brightmammoth.bmb.de"));
        assertFalse(UrlUtils.isUkStyleTdl("www.cox.de"));
    }

    @Test
    public void getDomain() {
        assertEquals("example.com", UrlUtils.getDomain("www.example.com"));
        assertEquals("example.co.uk", UrlUtils.getDomain("www.example.co.uk"));
        assertEquals("example.com", UrlUtils.getDomain("example.com"));
        assertEquals("example.co.uk", UrlUtils.getDomain("example.co.uk"));

        assertNull(UrlUtils.getDomain("example.com.")); // invalid hostname with trailing dot
        assertNull(UrlUtils.getDomain("com")); // no hostname

        assertEquals("co.uk", UrlUtils.getDomain("co.uk")); // not ideal, but what do you expect for an invalid FQDN...
    }

    @Test(expected = EblockerException.class)
    public void testInvalidHostname() {
        UrlUtils.getHostname("foo.bar");
    }

    @Test
    public void testIsUrl() {
        assertFalse(UrlUtils.isUrl(null));
        assertFalse(UrlUtils.isUrl(""));
        assertFalse(UrlUtils.isUrl("localhost"));
        assertFalse(UrlUtils.isUrl("foo/bar"));
        assertFalse(UrlUtils.isUrl("1234567890abcdef"));

        assertFalse(UrlUtils.isUrl("foo.bar"));
        assertFalse(UrlUtils.isUrl("foo.bar.baz"));
        assertFalse(UrlUtils.isUrl("foo.bar.baz/path/to/file.ext?query=blubb"));

        assertTrue(UrlUtils.isUrl("http://foo.bar.baz/path/to/file.ext?query=blubb"));
        assertTrue(UrlUtils.isUrl("https://foo.bar.baz/path/to/file.ext?query=blubb"));
        assertTrue(UrlUtils.isUrl("https://foo.bar.baz"));
    }

    @Test
    public void testIsUrlWithoutProtocol() {
        assertFalse(UrlUtils.isUrlWithoutProtocol(null));
        assertFalse(UrlUtils.isUrlWithoutProtocol(""));
        assertFalse(UrlUtils.isUrlWithoutProtocol("localhost"));
        assertFalse(UrlUtils.isUrlWithoutProtocol("foo/bar"));
        assertFalse(UrlUtils.isUrlWithoutProtocol("1234567890abcdef"));

        assertTrue(UrlUtils.isUrlWithoutProtocol("foo.bar"));
        assertTrue(UrlUtils.isUrlWithoutProtocol("foo.bar.baz"));
        assertTrue(UrlUtils.isUrlWithoutProtocol("foo.bar.baz/path/to/file.ext?query=blubb"));

        assertFalse(UrlUtils.isUrlWithoutProtocol("http://foo.bar.baz/path/to/file.ext?query=blubb"));
    }

    @Test
    public void testFindUrlParameter_simple() throws UnsupportedEncodingException {
        String targetUrl1 = "http://www.example.com";
        String targetUrl2 = "https://www.example.com?aaa=123&bbb=&bbb=456&bbb=%3f+%3f";
        String targetUrl3 = "//www.example.com";
        String targetUrl4 = "www.example.com";

        // simple url, single param
        String requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // complex URL, single param
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // two URL params
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8") + "&blipp=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, null)); // find the first one
        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // single multi-valued URL params
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8") + "&blupp=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // no URL params
        requestUrl = "http://foo.bar/quark?blubb=quark&blipp=other";

        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // target URL without "http" --> Only found, when correct param is specified!
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl3, "UTF-8");

        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // target URL without "http://" --> Only found, when correct param is specified!
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl4, "UTF-8");

        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "blipp"));

        // url with illegal encodings
        requestUrl = "https://www.example.com/?abc=%u2018%20&x=%FGhello%gg&url=" + URLEncoder.encode(targetUrl1, "UTF-8");
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "url"));

        // url parameter without value
        requestUrl = "https://www.example.com/?url";
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "url"));

        // duplicate url parameter, once without value
        requestUrl = "https://www.example.com/?url&url=" + URLEncoder.encode(targetUrl1, "UTF-8");
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, null));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "url"));
    }

    @Test
    public void testFindUrlParameter_inQueryParameter() throws UnsupportedEncodingException {
        String targetUrl1 = "http://www.example.com";
        String targetUrl2 = "https://www.example.com?aaa=123&bbb=&bbb=456&bbb=%3f+%3f";
        String targetUrl3 = "//www.example.com";
        String targetUrl4 = "www.example.com";

        // simple url, single param
        String requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // complex URL, single param
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // two URL params
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8") + "&blipp=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // single multi-valued URL params
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl2, "UTF-8") + "&blupp=" + URLEncoder.encode(targetUrl1, "UTF-8");

        assertEquals(targetUrl2, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // no URL params
        requestUrl = "http://foo.bar/quark?blubb=quark&blipp=other";

        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // target URL without "http" --> Only found, when correct param is specified!
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl3, "UTF-8");

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // target URL without "http://" --> Only found, when correct param is specified!
        requestUrl = "http://foo.bar/quark?blubb=" + URLEncoder.encode(targetUrl4, "UTF-8");

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blubb"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&blipp"));

        // url with illegal encodings
        requestUrl = "https://www.example.com/?abc=%u2018%20&x=%FGhello%gg&url=" + URLEncoder.encode(targetUrl1, "UTF-8");
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&url"));

        // url parameter without value
        requestUrl = "https://www.example.com/?abc&url=" + URLEncoder.encode(targetUrl1, "UTF-8");
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&url"));

        // url parameter without value
        requestUrl = "https://www.example.com/?url";
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&url"));

        // duplicate url parameter, once without value
        requestUrl = "https://www.example.com/?url&url=" + URLEncoder.encode(targetUrl1, "UTF-8");
        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&url"));
    }

    @Test
    public void testFindUrlParameter_atEndOfUrl() throws UnsupportedEncodingException {
        String marker = "%3f";
        String targetUrl1 = "http://www.example.com";

        // simple url, single param
        String requestUrl = "http://foo.bar/quark;blubb;blibb&a=b&c=d;" + marker + targetUrl1;

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "END_OF_URL&%3f"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&foobar"));

        // simple url, single param
        requestUrl = "http://foo.bar/quark;blubb;blibb&a=b&c=d;" + marker + targetUrl1;

        assertEquals(targetUrl1, UrlUtils.findUrlParameter(requestUrl, "END_OF_URL&%3f"));
        assertEquals(null, UrlUtils.findUrlParameter(requestUrl, "QUERY_PARAMETER&foobar"));

    }

    @Test
    public void testIsInvalidDomain() {
        // valid domains
        assertFalse(UrlUtils.isInvalidDomain("bla.de"));
        assertFalse(UrlUtils.isInvalidDomain("3com.com"));
        assertFalse(UrlUtils.isInvalidDomain("http://user:pass@bla.de/blubb"));
        assertFalse(UrlUtils.isInvalidDomain("http://www.bla.de"));
        assertFalse(UrlUtils.isInvalidDomain("http://www.bla.de/"));
        assertFalse(UrlUtils.isInvalidDomain("http://www.bla.de/blubb.html#fasel"));
        assertFalse(UrlUtils.isInvalidDomain("https://user:pass@bla.de/blubb.php?param1=foo&param2=bar"));

        // invalid domains
        assertTrue(UrlUtils.isInvalidDomain("."));
        assertTrue(UrlUtils.isInvalidDomain(".."));
        assertTrue(UrlUtils.isInvalidDomain("bla.de/blubb"));
        assertTrue(UrlUtils.isInvalidDomain("de"));
        assertTrue(UrlUtils.isInvalidDomain("http://1.2.3:80/path"));
        assertTrue(UrlUtils.isInvalidDomain("http://www.bla..de/"));
    }

    @Test
    public void testFindDomainInString() {
        assertEquals("www.foo.bar", UrlUtils.findDomainInString("http://www.foo.bar/fourty.seven?eleven=true", true));
        assertEquals("members.paysite.com", UrlUtils.findDomainInString("https://username:password@members.paysite.com/services/purchase.php?item=47&variation=11", true));
        assertEquals("teatime.uk", UrlUtils.findDomainInString("http://teatime.uk/serve.php&milk=true", true));
        assertEquals("bla.de", UrlUtils.findDomainInString("bla.de", true));
        assertEquals("3com.com", UrlUtils.findDomainInString("3com.com", true));
        assertEquals(null, UrlUtils.findDomainInString(".", true));
        assertEquals(null, UrlUtils.findDomainInString("..", true));
        assertEquals(null, UrlUtils.findDomainInString("de", true));
        assertEquals("www.bla.de", UrlUtils.findDomainInString("http://www.bla.de", true));
        assertEquals("www.bla.de", UrlUtils.findDomainInString("http://www.bla.de/", true));
        assertEquals(null, UrlUtils.findDomainInString("http://www.bla..de/", true));
        assertEquals(null, UrlUtils.findDomainInString("bla.de/blubb", true));
        assertEquals("www.bla.de", UrlUtils.findDomainInString("http://www.bla.de/blubb.html#fasel", true));
        assertEquals("bla.de", UrlUtils.findDomainInString("http://user:pass@bla.de/blubb", true));
        assertEquals("bla.de", UrlUtils.findDomainInString("https://user:pass@bla.de/blubb.php?param1=foo&param2=bar", true));
        assertEquals(null, UrlUtils.findDomainInString("http://1.2.3.4:80/path", false));
        assertEquals(null, UrlUtils.findDomainInString("1.2.3.4", false));
        assertEquals("1.2.3.4", UrlUtils.findDomainInString("http://1.2.3.4:80/path", true));
        assertEquals("1.2.3.4", UrlUtils.findDomainInString("1.2.3.4", true));

        // JavaScript is different here, it interprets 1.2.3 as an abbreviation of 1.2.0.3
        assertEquals(null, UrlUtils.findDomainInString("http://1.2.3:80/path", true));
    }

    @Test
    public void testIsSameDomain() {
        assertTrue(UrlUtils.isSameDomain("facebook.com", "www.facebook.com"));
        assertTrue(UrlUtils.isSameDomain("facebook.com", "facebook.com"));
        assertFalse(UrlUtils.isSameDomain("bad-facebook.com", "www.facebook.com"));
    }

    @Test
    public void testGetOrigin() {
        assertEquals("http://www.example.com/", UrlUtils.getOrigin("http://www.example.com"));
        assertEquals("https://www.example.com/", UrlUtils.getOrigin("https://www.example.com/"));
        assertEquals("https://www.example.com/", UrlUtils.getOrigin("https://www.example.com/some/path"));
        assertEquals("https://www.example.com:8080/", UrlUtils.getOrigin("https://www.example.com:8080/some/path"));
        assertEquals("https://www.example.com/", UrlUtils.getOrigin("https://user:password@www.example.com/hello?foo=bar"));
        assertEquals("ftp://example.com/", UrlUtils.getOrigin("ftp://example.com/some/file"));
        assertNull(UrlUtils.getOrigin("whatever"));
        assertNull(UrlUtils.getOrigin(null));
        assertNull(UrlUtils.getOrigin(""));
    }
}
