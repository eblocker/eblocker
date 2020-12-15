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
package org.eblocker.server.icap.filter.url;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.TestContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubstringUrlFilterTest {

    @Test
    public void test() {
        String url = "http://world.most.annoying.ads/image/large/overlay";

        assertEquals(Decision.BLOCK, filter(StringMatchType.CONTAINS, "ads", FilterType.BLOCK, url));
        assertEquals(Decision.PASS, filter(StringMatchType.CONTAINS, "ads", FilterType.PASS, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.CONTAINS, "xyz", FilterType.BLOCK, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.CONTAINS, "xyz", FilterType.PASS, url));

        assertEquals(Decision.BLOCK, filter(StringMatchType.STARTSWITH, "http://world", FilterType.BLOCK, url));
        assertEquals(Decision.PASS, filter(StringMatchType.STARTSWITH, "http://world", FilterType.PASS, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.STARTSWITH, "ads", FilterType.BLOCK, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.STARTSWITH, "ads", FilterType.PASS, url));

        assertEquals(Decision.BLOCK, filter(StringMatchType.ENDSWITH, "large/overlay", FilterType.BLOCK, url));
        assertEquals(Decision.PASS, filter(StringMatchType.ENDSWITH, "large/overlay", FilterType.PASS, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.ENDSWITH, "ads", FilterType.BLOCK, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.ENDSWITH, "ads", FilterType.PASS, url));

        assertEquals(Decision.BLOCK, filter(StringMatchType.EQUALS, "http://world.most.annoying.ads/image/large/overlay", FilterType.BLOCK, url));
        assertEquals(Decision.PASS, filter(StringMatchType.EQUALS, "http://world.most.annoying.ads/image/large/overlay", FilterType.PASS, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.EQUALS, "ads", FilterType.BLOCK, url));
        assertEquals(Decision.NO_DECISION, filter(StringMatchType.EQUALS, "ads", FilterType.PASS, url));

    }

    private Decision filter(StringMatchType matchType, String substring, FilterType type, String url) {
        TransactionContext context = new TestContext(url);
        Filter filter = UrlFilterFactory.getInstance()
                .setStringMatchType(matchType)
                .setMatchString(substring)
                .setType(type)
                .build();
        return filter.filter(context).getDecision();
    }

}
