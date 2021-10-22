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

public class DomainEntityTest {
    @Test
    public void test() {
        DomainEntity entity = new DomainEntity("google.*");

        Assert.assertTrue(entity.matches("google.com"));
        Assert.assertTrue(entity.matches("google.co.uk"));
        Assert.assertTrue(entity.matches("www.google.com"));
        Assert.assertTrue(entity.matches("google.is.google.biz"));

        Assert.assertFalse(entity.matches("google.evil.biz"));
        Assert.assertFalse(entity.matches("www-google.com"));
        Assert.assertFalse(entity.matches("localhost"));
        Assert.assertFalse(entity.matches("google.tldnotfound"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDomain() {
        new DomainEntity("google.*").matches("let.me.google.this.4u");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEntity() {
        new DomainEntity("*.google.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSpecificGenericFiltersNotSupported() {
        new DomainEntity("*");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyEntity() {
        new DomainEntity(".*");
    }
}