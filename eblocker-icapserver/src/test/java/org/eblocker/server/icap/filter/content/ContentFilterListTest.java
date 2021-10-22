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
import java.util.Objects;
import java.util.stream.Collectors;

public class ContentFilterListTest {
    @Test
    public void test() {
        testMatching(
                "www.example.com",
                List.of(
                        "example.com##.ads",
                        "www.example.com##.banners",
                        "www.other.biz##.otherads"
                ),
                List.of(
                        "example.com##.ads",
                        "www.example.com##.banners"
                ));
    }

    @Test
    public void testNoMatch() {
        testMatching(
                "www.example.org",
                List.of(
                        "example.com##.ads",
                        "www.example.com##.banners",
                        "www.other.biz##.otherads"
                ),
                List.of());
    }

    @Test
    public void testException() {
        testMatching(
                "www.example.com",
                List.of(
                        "example.com##.ads",
                        "example.com##.someclass",
                        "example.com##.banners",
                        "www.example.com#@#.someclass" // exception for www
                ),
                List.of(
                        "example.com##.ads",
                        "example.com##.banners"
                )
        );
    }

    private void testMatching(String hostname, List<String> inputRules, List<String> outputRules) {
        ContentFilterList list = new ContentFilterList(new ContentFilterParser().parse(inputRules.stream()));
        List<ContentFilter> result = list.getMatchingFilters(hostname);
        List<String> matchingDefinitions = result.stream().map(Objects::toString).collect(Collectors.toList());
        Assert.assertEquals(outputRules, matchingDefinitions);
    }
}