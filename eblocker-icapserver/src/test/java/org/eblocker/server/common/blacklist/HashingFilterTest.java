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
package org.eblocker.server.common.blacklist;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

public class HashingFilterTest {

    private static Integer LIST_ID = 23;
    private static int SIZE = 17;
    private static String NAME = "test";
    private static byte[] BLOCKED_HASH = Hashing.sha256().hashString("eblocker.com", Charsets.UTF_8).asBytes();

    private DomainFilter<byte[]> backingFilter;
    private HashingFilter filter;

    @Before
    public void setUp() {
        backingFilter = Mockito.mock(DomainFilter.class);
        Mockito.when(backingFilter.getSize()).thenReturn(SIZE);
        Mockito.when(backingFilter.getName()).thenReturn(NAME);
        Mockito.when(backingFilter.getListId()).thenReturn(LIST_ID);
        Mockito.when(backingFilter.getChildFilters()).thenReturn(Collections.emptyList());
        Mockito.when(backingFilter.isBlocked(Mockito.any(byte[].class))).then(im -> {
            boolean blocked = Arrays.equals(BLOCKED_HASH, im.getArgument(0));
            return new FilterDecision<>(im.getArgument(0), blocked, backingFilter);
        });

        filter = new HashingFilter(Hashing.sha256(), backingFilter);
    }

    @Test
    public void getListId() {
        Assert.assertEquals(LIST_ID, filter.getListId());
    }

    @Test
    public void getName() {
        String expectedFormat = "(hash-%s %s)";
        Assert.assertEquals(String.format(expectedFormat, "md5", NAME), new HashingFilter(Hashing.md5(), backingFilter).getName());
        Assert.assertEquals(String.format(expectedFormat, "sha1", NAME), new HashingFilter(Hashing.sha1(), backingFilter).getName());
        Assert.assertEquals(String.format(expectedFormat, "sha512", NAME), new HashingFilter(Hashing.sha512(), backingFilter).getName());
        Assert.assertEquals(String.format(expectedFormat, "crc32", NAME), new HashingFilter(Hashing.crc32(), backingFilter).getName());
        Assert.assertEquals(String.format(expectedFormat, "adler32", NAME), new HashingFilter(Hashing.adler32(), backingFilter).getName());
    }

    @Test
    public void getSize() {
        Assert.assertEquals(SIZE, filter.getSize());
    }


    @Test(expected = UnsupportedOperationException.class)
    public void getDomains() {
        filter.getDomains();
    }

    @Test
    public void isBlocked() {
        FilterDecision<String> decision = filter.isBlocked("eblocker.com");
        Assert.assertTrue(decision.isBlocked());
        Assert.assertEquals(backingFilter, decision.getFilter());
        Assert.assertEquals("eblocker.com", decision.getDomain());
    }

    @Test
    public void getChildFilters() {
        Assert.assertEquals(Collections.singletonList(backingFilter), filter.getChildFilters());
    }
}
