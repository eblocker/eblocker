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
package org.eblocker.server.icap.filter.easylist;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.TestContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class EasyListRuleParserTest {
    private final static Logger log = LoggerFactory.getLogger(EasyListRuleParserTest.class);

    @Test
    public void test() {
        assertBlock("&ad_box_", "http://www.example.com/some/page&ad_box_=foobar");

        assertBlock("||sascdn.com^$third-party", "http://ced.sascdn.com/diff/251/3790622/vw_caddy_teaser_961x120.jpg");
        assertBlock("||sascdn.com^$third-party", "http://www.sascdn.com/diff/251/3790622/vw_caddy_teaser_961x120.jpg");
        assertBlock("||sascdn.com^$third-party", "http://sascdn.com/diff/251/3790622/vw_caddy_teaser_961x120.jpg");
        assertBlock("||sascdn.com^$third-party", "http://www.foobar.sascdn.com/diff/251/3790622/vw_caddy_teaser_961x120.jpg");
    }

    @Test
    public void test2() {
        String definition = "||adserver.com^$domain=example.com|example1.com|~example2.com|example3.com|~example4.com";
        String url = "http://www.adserver.com/foo/bar";
        assertFilter(definition, url, "http://www.example.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example2.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example3.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example4.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example5.com", null, Decision.NO_DECISION);
    }

    @Test
    public void test3() {
        String definition = "@@||adserver.com^$domain=example.com|example1.com|~example2.com|example3.com|~example4.com";
        String url = "http://www.adserver.com/foo/bar";
        assertFilter(definition, url, "http://www.example.com", null, Decision.PASS);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.PASS);
        assertFilter(definition, url, "http://www.example2.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example3.com", null, Decision.PASS);
        assertFilter(definition, url, "http://www.example4.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example5.com", null, Decision.NO_DECISION);
    }

    @Test
    public void test4() {
        String definition = "@@|http://*.de^$image,third-party,domain=gamona.de";
        String url = "http://www.google.de/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CCMQFjAA&url=http%3A%2F%2Fwww.trustcenter.de%2F&ei=ZmjYVMbNLIviywOBioJo&usg=AFQjCNE239d1NkWZcECxqLe9akXtrfgNlA&sig2=V5izHWZtFGWYi7KEI2IYIw&bvm=bv.85464276,d.bGQ";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example2.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example3.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example4.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example5.com", null, Decision.NO_DECISION);
    }

    @Test
    public void test5() {
        String definition = "||example.com^$third-party";
        String url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.BLOCK);

        definition = "@@||example.com^$third-party";
        url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.PASS);

        definition = "||example.com^$~third-party";
        url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.NO_DECISION);

        definition = "@@||example.com^$~third-party";
        url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.PASS);
        assertFilter(definition, url, "http://www.example1.com", null, Decision.NO_DECISION);

    }

    @Test
    public void matchDomain() {
        // Example from docs at https://adblockplus.org/en/filters
        String definition = "||example.com/banner.gif";
        assertFilter(definition, "http://example.com/banner.gif", null, null, Decision.BLOCK);
        assertFilter(definition, "https://example.com/banner.gif", null, null, Decision.BLOCK);
        assertFilter(definition, "http://www.example.com/banner.gif", null, null, Decision.BLOCK);
        assertFilter(definition, "http://badexample.com/banner.gif", null, null, Decision.NO_DECISION);
    }

    @Test
    public void testUnknownOption() {
        String definition = "/$file/analytics.";
        String url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
    }

    @Test
    public void testContentSecurityPoliciesOption() {
        String definition = "||merriam-webster.com^$csp=script-src 'self' * 'unsafe-inline'";
        String url = "https://www.merriam-webster.com/index.html";
        assertFilter(definition, url, "https://www.merriam-webster.com", null, Decision.SET_CSP_HEADER, "script-src 'self' * 'unsafe-inline'");
    }

    @Test
    public void testTypeOptions() {
        testTypeOption("document", "html", "text/html");
        testTypeOption("image", "jpg", "image/jpg");
        testTypeOption("script", "js", "application/javascript");
        testTypeOption("stylesheet", "css", "text/css");
        testTypeOption("subdocument", "html", "text/html");
    }

    private void testTypeOption(String option, String extension, String mimeType) {
        String definition = "||example.com^$" + option;
        String url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.BLOCK);

        url = "http://www.example.com/some/path/to/content." + extension;
        assertFilter(definition, url, "http://www.example.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.BLOCK);

        url = "http://www.example.com/some/path/to/content." + extension + "?rnd=0.235";
        assertFilter(definition, url, "http://www.example.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.BLOCK);

        definition = "||example.com^$~" + option;
        url = "http://www.example.com/some/path/to/content";
        assertFilter(definition, url, "http://www.example.com", null, Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.BLOCK);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.NO_DECISION);

        url = "http://www.example.com/some/path/to/content." + extension;
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.NO_DECISION);

        url = "http://www.example.com/some/path/to/content." + extension + "?rnd=0.235";
        assertFilter(definition, url, "http://www.example.com", null, Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", "*/*", Decision.NO_DECISION);
        assertFilter(definition, url, "http://www.example.com", mimeType, Decision.NO_DECISION);
    }

    @Test
    public void testIgnoringWebsocketRules() {
        Filter rule = new EasyListLineParser().parseLine("$websocket,domain=123movies-proxy.ru|123movies.cz");
        Assert.assertNull(rule);
    }

    @Test
    public void ignoreElementHiding() {
        assertIgnore("ebuddy.com###Button");
        assertIgnore("###AdvContainerTopCenter");
        assertIgnore("##.ad-primary-sidebar");

        // Exception rules
        assertIgnore("superbikeplanet.com#@#.adDiv");

        // Extended CSS selectors (Adblock Plus specific)
        assertIgnore("multiup.eu,multiup.org#?#.trtbl:-abp-has(.warnIp)");
    }

    @Test
    public void endMatchWithOption() {
        String definition = "/ad.php|$popup";
        assertFilter(definition, "https://example.com/path/ad.php?extra", null, null, Decision.NO_DECISION);
        assertFilter(definition, "https://example.com/path/ad.php", null, null, Decision.BLOCK);
    }

    @Test
    public void beginMatch() {
        String definition = "|http://go.$domain=nowvideo.sx";
        String referer = "http://nowvideo.sx";
        assertFilter(definition, "http://go.home.com/", referer, null, Decision.BLOCK);
        assertFilter(definition, "http://nogo.com/?url=http://go.home.com/", referer, null, Decision.NO_DECISION);

        definition = "|http://j.gs/omnigy*.swf";
        assertFilter(definition, "http://j.gs/omnigy123.swf", null, null, Decision.BLOCK);
        assertFilter(definition, "http://j.gs/omnigy123.swf?x=u", null, null, Decision.BLOCK);
        assertFilter(definition, "http://j.gs/omnig.swf", null, null, Decision.NO_DECISION);
    }

    @Test
    public void wildcards() {
        // The '*' is a wildcard for zero or more characters:
        assertFilter("&zoneid=*&direct=", "http://example.com/path?f=bar&zoneid=1234&direct=yes", null, null, Decision.BLOCK);
        assertFilter("&zoneid=*&direct=", "http://example.com/path?f=bar&zoneid=&direct=yes", null, null, Decision.BLOCK);
        assertFilter("&zoneid=*&direct=", "http://example.com/path?f=bar&zoneid&direct", null, null, Decision.NO_DECISION);

        // The '^' is a wildcard for a separator
        assertFilter("^pid=Ads^", "http://example.com/path?pid=Ads", null, null, Decision.BLOCK);
        assertFilter("^pid=Ads^", "http://example.com/path?pid=Ads&foo=bar", null, null, Decision.BLOCK);
        assertFilter("^pid=Ads^", "http://example.com/path?ppid=Adsforall", null, null, Decision.NO_DECISION);

        // The '.' is not a wildcard (only in explicit regular expressions)
        assertFilter("foo.bar", "http://example.com/foodbar", null, null, Decision.NO_DECISION);
    }

    @Test
    public void regularExpressions() {
        String definition = "/\\.filenuke\\.com/.*[a-zA-Z0-9]{4}/"; //$script";
        assertFilter(definition, "https://www.filenuke.com/a/be/ceh/foo7/bar", null, null, Decision.BLOCK);
        assertFilter(definition, "https://www.filenuke.com/a/be/ceh/foo/bar", null, null, Decision.NO_DECISION);
    }

    @Test
    public void regularExpressionWithOptions() {
        // A more complicated example from the EasyList:
        String definition = "/^https?:\\/\\/([0-9]{1,3}\\.){3}[0-9]{1,3}/$image,script,subdocument,domain=o2tvseries.com|readcomiconline.to|speedvid.net";
        assertFilter(definition, "http://123.45.6.78/path", "http://speedvid.net/", "application/javascript", Decision.BLOCK);
        assertFilter(definition, "http://123.45.6.78/path", "http://example.com/", "application/javascript", Decision.NO_DECISION);
        assertFilter(definition, "http://example.com/?url=http://123.45.6.78/", "http://speedvid.net/", "application/javascript", Decision.NO_DECISION);
    }

    private void assertIgnore(String definition) {
        Assert.assertNull(new EasyListLineParser().parseLine(definition));
    }

    private void assertBlock(String definition, String url) {
        assertFilter(definition, url, null, null, Decision.BLOCK);
    }

    private void assertFilter(String definition, String url, String referrer, String accept, Decision decision) {
        assertFilter(definition, url, referrer, accept, decision, null);
    }

    private void assertFilter(String definition, String url, String referrer, String accept, Decision decision, String decisionValue) {
        Filter filter = new EasyListLineParser().parseLine(definition);
        log.info("Created filter {}", filter);
        TransactionContext context = new TestContext(url, referrer, accept);
        FilterResult filterResult = filter.filter(context);
        assertEquals(decision, filterResult.getDecision());
        assertEquals(decisionValue, filterResult.getValue());
    }

}
