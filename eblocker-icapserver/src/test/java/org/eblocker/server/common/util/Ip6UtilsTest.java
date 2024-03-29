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

import org.eblocker.server.common.data.Ip6Address;
import org.junit.Assert;
import org.junit.Test;

public class Ip6UtilsTest {

    @Test
    public void testCombine() {
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), Ip6Utils.combine(Ip6Address.parse("2a02:8106:21:6f03::"), Ip6Address.parse("::d9d0:5cb9:8956:be08")));
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), Ip6Utils.combine(Ip6Address.parse("2a02:8106:20::"), Ip6Address.parse("::1:6f03:d9d0:5cb9:8956:be08")));
    }

    @Test
    public void testGetNetworkAddress() {
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 128));
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be00"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 124));
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03::"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 64));
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f00::"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 56));
        Assert.assertEquals(Ip6Address.parse("2000::"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 4));
        Assert.assertEquals(Ip6Address.parse("::"), Ip6Utils.getNetworkAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 0));
    }

    @Test
    public void testGetHostAddress() {
        Assert.assertEquals(Ip6Address.parse("::"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 128));
        Assert.assertEquals(Ip6Address.parse("::8"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 124));
        Assert.assertEquals(Ip6Address.parse("::d9d0:5cb9:8956:be08"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 64));
        Assert.assertEquals(Ip6Address.parse("::3:d9d0:5cb9:8956:be08"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 56));
        Assert.assertEquals(Ip6Address.parse("a02:8106:21:6f03:d9d0:5cb9:8956:be08"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 4));
        Assert.assertEquals(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), Ip6Utils.getHostAddress(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08"), 0));
    }

    @Test
    public void testIsInNetwork() {
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("fe80::1234"), Ip6Address.parse("fe80::"), 10));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("fe80::1234"), Ip6Address.parse("fe80::"), 64));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("2002::1234"), Ip6Address.parse("2002::"), 16));
        Assert.assertFalse(Ip6Utils.isInNetwork(Ip6Address.parse("2002::1234"), Ip6Address.parse("2003::"), 16));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2002::"), 15));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2003::"), 15));
        Assert.assertFalse(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2004::"), 15));
        Assert.assertFalse(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2003::1233"), 127));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2003::1234"), 127));
        Assert.assertTrue(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2003::1235"), 127));
        Assert.assertFalse(Ip6Utils.isInNetwork(Ip6Address.parse("2003::1234"), Ip6Address.parse("2003::1236"), 127));
    }

    @Test
    public void testIsLinkLocal() {
        Assert.assertTrue(Ip6Utils.isLinkLocal(Ip6Address.parse("fe80::1234")));
        Assert.assertFalse(Ip6Utils.isLinkLocal(Ip6Address.parse("fc00::1234")));
        Assert.assertFalse(Ip6Utils.isLinkLocal(Ip6Address.parse("::1")));
        Assert.assertFalse(Ip6Utils.isLinkLocal(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08")));
    }

    @Test
    public void testIsUniqueLocal() {
        Assert.assertTrue(Ip6Utils.isUniqueLocal(Ip6Address.parse("fd00::1234")));
        Assert.assertTrue(Ip6Utils.isUniqueLocal(Ip6Address.parse("fd43:af9e:4892:1ec9:1434:47ce:d268:3b3e")));
        Assert.assertFalse(Ip6Utils.isUniqueLocal(Ip6Address.parse("fc00::1234")));
        Assert.assertFalse(Ip6Utils.isUniqueLocal(Ip6Address.parse("fe00::1234")));
        Assert.assertFalse(Ip6Utils.isUniqueLocal(Ip6Address.parse("::1")));
        Assert.assertFalse(Ip6Utils.isUniqueLocal(Ip6Address.parse("2a02:8106:21:6f03:d9d0:5cb9:8956:be08")));
    }

    @Test
    public void testStripBrackets() {
        Assert.assertEquals("::1", Ip6Utils.stripBrackets("[::1]"));
        Assert.assertEquals("1.1.1.1", Ip6Utils.stripBrackets("1.1.1.1"));
        Assert.assertEquals("host.name", Ip6Utils.stripBrackets("host.name"));
        Assert.assertNull(Ip6Utils.stripBrackets(null));
        Assert.assertEquals("[::1", Ip6Utils.stripBrackets("[::1"));
        Assert.assertEquals("not an address", Ip6Utils.stripBrackets("[not an address]"));
        Assert.assertEquals("", Ip6Utils.stripBrackets("[]"));
        Assert.assertEquals("[", Ip6Utils.stripBrackets("["));
    }

    @Test
    public void testIsIp6Address() {
        Assert.assertTrue(Ip6Utils.isIp6Address("::"));
        Assert.assertTrue(Ip6Utils.isIp6Address("::1"));
        Assert.assertTrue(Ip6Utils.isIp6Address("3::"));
        Assert.assertTrue(Ip6Utils.isIp6Address("AbCd:1234::9876:42"));
        Assert.assertTrue(Ip6Utils.isIp6Address("1:2:3:4:5:6:7:8"));
        Assert.assertTrue(Ip6Utils.isIp6Address("1:234:ffff:AA:abc:ab:c:9999"));

        Assert.assertFalse(Ip6Utils.isIp6Address("localhost"));
        Assert.assertFalse(Ip6Utils.isIp6Address(""));
        Assert.assertFalse(Ip6Utils.isIp6Address("example.com"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1.2.3.4"));
        Assert.assertFalse(Ip6Utils.isIp6Address(":"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1::2::3"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4:5:6:7"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4:5:6:7:88888"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4:5:6:7:8:9"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4::5:6:7:8"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4:5:6:7:8/64"));
        Assert.assertFalse(Ip6Utils.isIp6Address("1:2:3:4:5:6:7:8::"));
    }

    @Test
    public void testIsIp6Range() {
        Assert.assertTrue(Ip6Utils.isIp6Range("::/0"));
        Assert.assertTrue(Ip6Utils.isIp6Range("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff/128"));
        Assert.assertTrue(Ip6Utils.isIp6Range("2A03::/16"));
        Assert.assertTrue(Ip6Utils.isIp6Range("2603:1000::/25"));

        Assert.assertFalse(Ip6Utils.isIp6Range(""));
        Assert.assertFalse(Ip6Utils.isIp6Range("2603:1000::/ 25"));
        Assert.assertFalse(Ip6Utils.isIp6Range("2a03::/ab"));
        Assert.assertFalse(Ip6Utils.isIp6Range("2a03::/129"));
        Assert.assertFalse(Ip6Utils.isIp6Range("1.2.3.4/8"));
    }
}
