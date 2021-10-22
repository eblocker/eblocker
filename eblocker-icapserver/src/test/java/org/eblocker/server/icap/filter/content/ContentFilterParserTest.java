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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Stream;

public class ContentFilterParserTest {
    private final ContentFilterParser parser = new ContentFilterParser();

    @Test
    public void testElementHiding() {
        testParse(
                Stream.of(
                        "google.*##.ads-ad"
                ),
                List.of(
                        new ElementHidingFilter(List.of(new DomainEntity("google.*")), ContentAction.ADD, ".ads-ad")
                ));
    }

    @Test
    public void testScriptlet() {
        testParse(
                Stream.of(
                        "t-online.de##+js(set, abp, false)"
                ),
                List.of(
                        new ScriptletFilter(List.of(new Domain("t-online.de")), ContentAction.ADD, "set, abp, false")
                ));
    }

    @Test
    public void testException() {
        testParse(
                Stream.of(
                        "t-online.de##+js(acis, Number.isNaN)",
                        "email.t-online.de#@#+js(acis, Number.isNaN)"
                ),
                List.of(
                        new ScriptletFilter(List.of(new Domain("t-online.de")), ContentAction.ADD, "acis, Number.isNaN"),
                        new ScriptletFilter(List.of(new Domain("email.t-online.de")), ContentAction.REMOVE, "acis, Number.isNaN")
                ));
    }

    @Test
    public void testConditionalsAreIgnored() {
        testParse(
                Stream.of(
                        "!#if env_chromium",
                        "t-online.de##+js(acis, Number.isNaN)",
                        "!#endif"
                ),
                List.of());
    }

    @Test
    public void testGenericFiltersAreIgnored() {
        testParse(
                Stream.of(
                        "*##.selector",
                        "##.selector"
                ),
                List.of());
    }

    @Test
    public void testEmpty() {
        testParse(Stream.of(), List.of());
        testParse(Stream.of(""), List.of());
        testParse(Stream.of("##"), List.of());
        testParse(Stream.of("/other/filter"), List.of());
        testParse(Stream.of(" ! google.com##.ads-ad"), List.of());
    }

    private void testParse(Stream<String> input, List<Object> expected) {
        List<ContentFilter> result = parser.parse(input);
        Assert.assertEquals(expected, result);
    }

}