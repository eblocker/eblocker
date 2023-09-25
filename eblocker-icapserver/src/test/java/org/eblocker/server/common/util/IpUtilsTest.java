/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.junit.Test;

import org.junit.Assert;

public class IpUtilsTest {
    @Test
    public void testIsIpAddress() {
        Assert.assertTrue(IpUtils.isIPAddress("1.2.3.4"));
        Assert.assertTrue(IpUtils.isIPAddress("0.0.0.0"));
        Assert.assertTrue(IpUtils.isIPAddress("::"));
        Assert.assertTrue(IpUtils.isIPAddress("2603:1000::"));

        Assert.assertFalse(IpUtils.isIPAddress(""));
        Assert.assertFalse(IpUtils.isIPAddress("localhost"));
        Assert.assertFalse(IpUtils.isIPAddress("1.2.3"));
        Assert.assertFalse(IpUtils.isIPAddress("2603:1000::/25"));
    }

    @Test
    public void testIsIpRange() {
        Assert.assertTrue(IpUtils.isIpRange("1.2.3.4/32"));
        Assert.assertTrue(IpUtils.isIpRange("17.0.0.0/8"));
        Assert.assertTrue(IpUtils.isIpRange("::/128"));
        Assert.assertTrue(IpUtils.isIpRange("2603:1000::/25"));
        Assert.assertFalse(IpUtils.isIpRange(""));
        Assert.assertFalse(IpUtils.isIpRange("localhost/32"));
        Assert.assertFalse(IpUtils.isIpRange("1.2.3"));
        Assert.assertFalse(IpUtils.isIpRange("2603:1000::"));
    }

    @Test
    public void testShrinkRange() {
        Assert.assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/0"));
        Assert.assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/1"));
        Assert.assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/2"));
        Assert.assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/7"));
        Assert.assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/8"));
        Assert.assertEquals("123.123.123.123/9", IpUtils.shrinkIpRange("123.123.123.123/9"));
        Assert.assertEquals("123.123.123.123/10", IpUtils.shrinkIpRange("123.123.123.123/10"));
        Assert.assertEquals("123.123.123.123/31", IpUtils.shrinkIpRange("123.123.123.123/31"));
        Assert.assertEquals("123.123.123.123/32", IpUtils.shrinkIpRange("123.123.123.123/32"));

        Assert.assertEquals("2603:1000::/32", IpUtils.shrinkIpRange("2603:1000::/0"));
        Assert.assertEquals("2603:1000::/32", IpUtils.shrinkIpRange("2603:1000::/25"));
        Assert.assertEquals("2603:1000::/32", IpUtils.shrinkIpRange("2603:1000::/31"));
        Assert.assertEquals("2603:1000::/32", IpUtils.shrinkIpRange("2603:1000::/32"));
        Assert.assertEquals("2603:1000::/33", IpUtils.shrinkIpRange("2603:1000::/33"));
        Assert.assertEquals("2603:1000::/64", IpUtils.shrinkIpRange("2603:1000::/64"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shrinkRangeNotAnIpAddress() {
        IpUtils.shrinkIpRange("localhost/7");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shrinkRangeNotANumber() {
        IpUtils.shrinkIpRange("1.2.3.4/a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shrinkRangeBadFormat() {
        IpUtils.shrinkIpRange("1.2.3.4/5/6");
    }
}
