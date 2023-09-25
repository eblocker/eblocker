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

import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Ip4UtilsTest {
    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testIsIpV4Address() {
        assertEquals(true, Ip4Utils.isIPAddress("1.1.1.1"));
        assertEquals(true, Ip4Utils.isIPAddress("255.255.255.255"));
        assertEquals(true, Ip4Utils.isIPAddress("192.168.1.1"));
        assertEquals(true, Ip4Utils.isIPAddress("10.10.1.1"));
        assertEquals(true, Ip4Utils.isIPAddress("132.254.111.10"));
        assertEquals(true, Ip4Utils.isIPAddress("26.10.2.10"));
        assertEquals(true, Ip4Utils.isIPAddress("127.0.0.1"));
        assertEquals(true, Ip4Utils.isIPAddress("255.254.253.252"));
        assertEquals(true, Ip4Utils.isIPAddress("54.74.90.10"));
        assertEquals(true, Ip4Utils.isIPAddress("1.255.1.255"));

        assertEquals(false, Ip4Utils.isIPAddress("10.10.10"));
        assertEquals(false, Ip4Utils.isIPAddress("10.10"));
        assertEquals(false, Ip4Utils.isIPAddress("10"));
        assertEquals(false, Ip4Utils.isIPAddress("a.b.c.d"));
        assertEquals(false, Ip4Utils.isIPAddress("10.10.37.a"));
        assertEquals(false, Ip4Utils.isIPAddress("3.6.3.256"));
        assertEquals(false, Ip4Utils.isIPAddress("23.322.23.32"));
        assertEquals(false, Ip4Utils.isIPAddress("123.0101.99.6"));
        assertEquals(false, Ip4Utils.isIPAddress("2222.222.22.2"));
        assertEquals(false, Ip4Utils.isIPAddress("2.22.222.2222"));
    }

    @Test
    public void isIPRange() {
        // Too big
        assertFalse(Ip4Utils.isIpRange("123.123.123.123/3456"));
        assertFalse(Ip4Utils.isIpRange("123.123.123.123/33"));
        // Just the right size
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/32"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/31"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/30"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/16"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/3"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/2"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/1"));
        assertTrue(Ip4Utils.isIpRange("123.123.123.123/0"));
        // No size
        assertFalse(Ip4Utils.isIpRange("123.123.123.123/"));
        assertFalse(Ip4Utils.isIpRange("123.123.123.123"));
        // Characters (a-z) and special characters (;) have no business being in
        // an IP address
        assertFalse(Ip4Utils.isIpRange("123.123.123.123/ab"));
        assertFalse(Ip4Utils.isIpRange("123.123.723.123/32"));
        assertFalse(Ip4Utils.isIpRange("123.123;123.123/32"));
        assertFalse(Ip4Utils.isIpRange("123.xyz.123.123/32"));
        assertFalse(Ip4Utils.isIpRange("123.123.123.123/ 16"));
    }

    @Test
    public void testConvertIpIntToString() {
        assertEquals(0, Ip4Utils.convertIpStringToInt("0.0.0.0"));
        assertEquals(0x000000ff, Ip4Utils.convertIpStringToInt("0.0.0.255"));
        assertEquals(0x0000ff00, Ip4Utils.convertIpStringToInt("0.0.255.0"));
        assertEquals(0x00ff0000, Ip4Utils.convertIpStringToInt("0.255.0.0"));
        assertEquals(0xff000000, Ip4Utils.convertIpStringToInt("255.0.0.0"));
        assertEquals(0xffffffff, Ip4Utils.convertIpStringToInt("255.255.255.255"));
        assertEquals(521319188, Ip4Utils.convertIpStringToInt("31.18.179.20"));

        exception.expect(EblockerException.class);
        Ip4Utils.convertIpStringToInt(null);
    }

    @Test
    public void testConvertIpStringToInt() {
        assertEquals("0.0.0.0", Ip4Utils.convertIpIntToString(0));
        assertEquals("0.0.0.255", Ip4Utils.convertIpIntToString(0x000000ff));
        assertEquals("0.0.255.0", Ip4Utils.convertIpIntToString(0x0000ff00));
        assertEquals("0.255.0.0", Ip4Utils.convertIpIntToString(0x00ff0000));
        assertEquals("255.0.0.0", Ip4Utils.convertIpIntToString(0xff000000));
        assertEquals("255.255.255.255", Ip4Utils.convertIpIntToString(0xffffffff));
        assertEquals("31.18.179.20", Ip4Utils.convertIpIntToString(521319188));
    }

    @Test
    public void testNetMaskToCidr() {
        assertEquals(0, Ip4Utils.convertNetMaskToCidr(0x00000000));
        assertEquals(1, Ip4Utils.convertNetMaskToCidr(0x80000000));
        assertEquals(2, Ip4Utils.convertNetMaskToCidr(0xc0000000));
        assertEquals(3, Ip4Utils.convertNetMaskToCidr(0xe0000000));
        assertEquals(4, Ip4Utils.convertNetMaskToCidr(0xf0000000));
        assertEquals(5, Ip4Utils.convertNetMaskToCidr(0xf8000000));
        assertEquals(6, Ip4Utils.convertNetMaskToCidr(0xfc000000));
        assertEquals(7, Ip4Utils.convertNetMaskToCidr(0xfe000000));
        assertEquals(8, Ip4Utils.convertNetMaskToCidr(0xff000000));
        assertEquals(9, Ip4Utils.convertNetMaskToCidr(0xff800000));
        assertEquals(10, Ip4Utils.convertNetMaskToCidr(0xffc00000));
        assertEquals(11, Ip4Utils.convertNetMaskToCidr(0xffe00000));
        assertEquals(12, Ip4Utils.convertNetMaskToCidr(0xfff00000));
        assertEquals(13, Ip4Utils.convertNetMaskToCidr(0xfff80000));
        assertEquals(14, Ip4Utils.convertNetMaskToCidr(0xfffc0000));
        assertEquals(15, Ip4Utils.convertNetMaskToCidr(0xfffe0000));
        assertEquals(16, Ip4Utils.convertNetMaskToCidr(0xffff0000));
        assertEquals(17, Ip4Utils.convertNetMaskToCidr(0xffff8000));
        assertEquals(18, Ip4Utils.convertNetMaskToCidr(0xffffc000));
        assertEquals(19, Ip4Utils.convertNetMaskToCidr(0xffffe000));
        assertEquals(20, Ip4Utils.convertNetMaskToCidr(0xfffff000));
        assertEquals(21, Ip4Utils.convertNetMaskToCidr(0xfffff800));
        assertEquals(22, Ip4Utils.convertNetMaskToCidr(0xfffffc00));
        assertEquals(23, Ip4Utils.convertNetMaskToCidr(0xfffffe00));
        assertEquals(24, Ip4Utils.convertNetMaskToCidr(0xffffff00));
        assertEquals(25, Ip4Utils.convertNetMaskToCidr(0xffffff80));
        assertEquals(26, Ip4Utils.convertNetMaskToCidr(0xffffffc0));
        assertEquals(27, Ip4Utils.convertNetMaskToCidr(0xffffffe0));
        assertEquals(28, Ip4Utils.convertNetMaskToCidr(0xfffffff0));
        assertEquals(29, Ip4Utils.convertNetMaskToCidr(0xfffffff8));
        assertEquals(30, Ip4Utils.convertNetMaskToCidr(0xfffffffc));
        assertEquals(31, Ip4Utils.convertNetMaskToCidr(0xfffffffe));
        assertEquals(32, Ip4Utils.convertNetMaskToCidr(0xffffffff));
    }

    @Test
    public void testCidrToNetMask() {
        assertEquals(0x00000000, Ip4Utils.convertCidrToNetMask(0));
        assertEquals(0x80000000, Ip4Utils.convertCidrToNetMask(1));
        assertEquals(0xc0000000, Ip4Utils.convertCidrToNetMask(2));
        assertEquals(0xe0000000, Ip4Utils.convertCidrToNetMask(3));
        assertEquals(0xf0000000, Ip4Utils.convertCidrToNetMask(4));
        assertEquals(0xf8000000, Ip4Utils.convertCidrToNetMask(5));
        assertEquals(0xfc000000, Ip4Utils.convertCidrToNetMask(6));
        assertEquals(0xfe000000, Ip4Utils.convertCidrToNetMask(7));
        assertEquals(0xff000000, Ip4Utils.convertCidrToNetMask(8));
        assertEquals(0xff800000, Ip4Utils.convertCidrToNetMask(9));
        assertEquals(0xffc00000, Ip4Utils.convertCidrToNetMask(10));
        assertEquals(0xffe00000, Ip4Utils.convertCidrToNetMask(11));
        assertEquals(0xfff00000, Ip4Utils.convertCidrToNetMask(12));
        assertEquals(0xfff80000, Ip4Utils.convertCidrToNetMask(13));
        assertEquals(0xfffc0000, Ip4Utils.convertCidrToNetMask(14));
        assertEquals(0xfffe0000, Ip4Utils.convertCidrToNetMask(15));
        assertEquals(0xffff0000, Ip4Utils.convertCidrToNetMask(16));
        assertEquals(0xffff8000, Ip4Utils.convertCidrToNetMask(17));
        assertEquals(0xffffc000, Ip4Utils.convertCidrToNetMask(18));
        assertEquals(0xffffe000, Ip4Utils.convertCidrToNetMask(19));
        assertEquals(0xfffff000, Ip4Utils.convertCidrToNetMask(20));
        assertEquals(0xfffff800, Ip4Utils.convertCidrToNetMask(21));
        assertEquals(0xfffffc00, Ip4Utils.convertCidrToNetMask(22));
        assertEquals(0xfffffe00, Ip4Utils.convertCidrToNetMask(23));
        assertEquals(0xffffff00, Ip4Utils.convertCidrToNetMask(24));
        assertEquals(0xffffff80, Ip4Utils.convertCidrToNetMask(25));
        assertEquals(0xffffffc0, Ip4Utils.convertCidrToNetMask(26));
        assertEquals(0xffffffe0, Ip4Utils.convertCidrToNetMask(27));
        assertEquals(0xfffffff0, Ip4Utils.convertCidrToNetMask(28));
        assertEquals(0xfffffff8, Ip4Utils.convertCidrToNetMask(29));
        assertEquals(0xfffffffc, Ip4Utils.convertCidrToNetMask(30));
        assertEquals(0xfffffffe, Ip4Utils.convertCidrToNetMask(31));
        assertEquals(0xffffffff, Ip4Utils.convertCidrToNetMask(32));
    }

    @Test
    public void testGetSubnet() {
        assertEquals("10.0.0.0/8", Ip4Utils.getSubnet("10.10.10.10", "255.0.0.0"));
        assertEquals("172.16.0.0/12", Ip4Utils.getSubnet("172.16.10.10", "255.240.0.0"));
        assertEquals("192.168.10.0/24", Ip4Utils.getSubnet("192.168.10.10", "255.255.255.0"));
    }

    @Test
    public void testIsInSubnet() {
        assertTrue(Ip4Utils.isInSubnet("192.168.2.11", "192.168.2.0", "255.255.255.128"));
        assertFalse(Ip4Utils.isInSubnet("192.168.99.11", "192.168.2.0", "255.255.255.0"));

        assertFalse(Ip4Utils.isInSubnet(0x3f579cf5, 0xc0a80200, 0xffffff80));
        assertTrue(Ip4Utils.isInSubnet(0xc0a8020b, 0xc0a80200, 0xffffff80));
    }

    @Test
    public void testConvertIpRangeToIpNetmask() {
        int[] ipNetmask = Ip4Utils.convertIpRangeToIpNetmask("10.11.12.0/24");
        Assert.assertNotNull(ipNetmask);
        Assert.assertEquals(2, ipNetmask.length);
        Assert.assertEquals(0x0a0b0c00, ipNetmask[0]);
        Assert.assertEquals(0xffffff00, ipNetmask[1]);
    }

    @Test
    public void testConvertIpToBytes() {
        byte[] ipBytes = Ip4Utils.convertIpToBytes(0x0a0b0c0d);
        Assert.assertEquals(0xa, ipBytes[0]);
        Assert.assertEquals(0xb, ipBytes[1]);
        Assert.assertEquals(0xc, ipBytes[2]);
        Assert.assertEquals(0xd, ipBytes[3]);

        ipBytes = Ip4Utils.convertIpToBytes(0xfffefdfc);
        Assert.assertEquals((byte) 0xff, ipBytes[0]);
        Assert.assertEquals((byte) 0xfe, ipBytes[1]);
        Assert.assertEquals((byte) 0xfd, ipBytes[2]);
        Assert.assertEquals((byte) 0xfc, ipBytes[3]);
    }

    @Test
    public void testConvertBytesToIp() {
        assertEquals(0x0a0b0c0d, Ip4Utils.convertBytesToIp(new byte[]{ 0x0a, 0x0b, 0x0c, 0x0d }));
        assertEquals(0xfffefdfc, Ip4Utils.convertBytesToIp(new byte[]{ (byte) 0xff, (byte) 0xfe, (byte) 0xfd, (byte) 0xfc }));
    }
}
