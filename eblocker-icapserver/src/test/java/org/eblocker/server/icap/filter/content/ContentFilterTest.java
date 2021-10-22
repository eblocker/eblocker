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

public class ContentFilterTest {
    @Test
    public void test() {
        List<Domain> domains = List.of(new Domain("example.com"), new DomainEntity("google.*"));
        ContentFilter filter = new ContentFilter(domains, ContentAction.ADD) {
            @Override
            String getExpression() {
                return "<expression>";
            }
        };

        Assert.assertTrue(filter.matches("www.example.com"));
        Assert.assertTrue(filter.matches("www.google.co.jp"));

        Assert.assertFalse(filter.matches("other.biz"));
    }
}