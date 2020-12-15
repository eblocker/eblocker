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
package org.eblocker.server.common.data;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class IpAddressTest {

    @Test
    public void testParseIpv4() {
        Assert.assertEquals(Ip4Address.class, IpAddress.parse("192.168.3.4").getClass());
    }

    @Test
    public void testParseIpv6() {
        Assert.assertEquals(Ip6Address.class, IpAddress.parse("2001:db8:aaaa:bbbb:cccc:dddd:eeee:ffff").getClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNonIp() {
        IpAddress.parse("address");
    }

    @Test
    public void testOfIpv4() {
        Assert.assertEquals(Ip4Address.class, IpAddress.of(DatatypeConverter.parseHexBinary("c0a80304")).getClass());
    }

    @Test
    public void testOfIpv6() {
        Assert.assertEquals(Ip6Address.class, IpAddress.of(DatatypeConverter.parseHexBinary("20010db8aaaabbbbccccddddeeeeffff")).getClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfInvalidAddressLength() {
        IpAddress.of(new byte[5]);
    }

    @Test
    public void testOfInetAddress4() throws UnknownHostException {
        Assert.assertEquals(Ip4Address.class, IpAddress.of(InetAddress.getByName("192.168.3.4")).getClass());
    }

    @Test
    public void testOfInetAddress6() throws UnknownHostException {
        Assert.assertEquals(Ip6Address.class, IpAddress.of(InetAddress.getByName("2001:0db8:aaaa:bbbb:cccc:dddd:eeee:ffff")).getClass());
    }

    @Test
    public void testEquals() {
        IpAddress a = Ip4Address.parse("192.168.3.4");
        IpAddress b = Ip4Address.parse("192.168.3.4");
        IpAddress c = Ip4Address.parse("192.168.3.5");
        IpAddress d = Ip6Address.parse("2001:0db8:aaaa:bbbb:cccc:dddd:eeee:ffff");
        IpAddress e = Ip6Address.parse("2001:0db8:aaaa:bbbb:cccc:dddd:eeee:ffff");
        IpAddress f = Ip6Address.parse("2001:0db8:aaaa:bbbb:cccc:dddd:eeee:fff0");

        Assert.assertEquals(a, a);
        Assert.assertEquals(a, b);
        Assert.assertNotEquals(a, c);
        Assert.assertNotEquals(a, d);
        Assert.assertNotEquals(a, e);
        Assert.assertNotEquals(a, f);
        Assert.assertEquals(b, b);
        Assert.assertNotEquals(b, c);
        Assert.assertNotEquals(b, d);
        Assert.assertNotEquals(b, e);
        Assert.assertNotEquals(b, f);
        Assert.assertEquals(c, c);
        Assert.assertNotEquals(c, d);
        Assert.assertNotEquals(c, e);
        Assert.assertNotEquals(c, f);
        Assert.assertEquals(d, d);
        Assert.assertEquals(d, e);
        Assert.assertNotEquals(d, f);
        Assert.assertEquals(e, e);
        Assert.assertNotEquals(e, f);
        Assert.assertEquals(f, f);

        Assert.assertNotEquals(a, new Object());
    }

    @Test
    public void testHashCode() {
        byte[] ip4 = DatatypeConverter.parseHexBinary("c0a80304");
        Assert.assertEquals(Arrays.hashCode(ip4), IpAddress.of(ip4).hashCode());

        byte[] ip6 = DatatypeConverter.parseHexBinary("20010db8aaaabbbbccccddddeeeeffff");
        Assert.assertEquals(Arrays.hashCode(ip6), IpAddress.of(ip6).hashCode());
    }

}
