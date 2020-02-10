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

import static org.junit.Assert.*;

public class IpUtilsTest {
    @Rule
    public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testIsIpV4Address() {
		assertEquals(true, IpUtils.isIPAddress("1.1.1.1"));
		assertEquals(true, IpUtils.isIPAddress("255.255.255.255"));
		assertEquals(true, IpUtils.isIPAddress("192.168.1.1"));
		assertEquals(true, IpUtils.isIPAddress("10.10.1.1"));
		assertEquals(true, IpUtils.isIPAddress("132.254.111.10"));
		assertEquals(true, IpUtils.isIPAddress("26.10.2.10"));
		assertEquals(true, IpUtils.isIPAddress("127.0.0.1"));
		assertEquals(true, IpUtils.isIPAddress("255.254.253.252"));
		assertEquals(true, IpUtils.isIPAddress("54.74.90.10"));
		assertEquals(true, IpUtils.isIPAddress("1.255.1.255"));

		assertEquals(false, IpUtils.isIPAddress("10.10.10"));
		assertEquals(false, IpUtils.isIPAddress("10.10"));
		assertEquals(false, IpUtils.isIPAddress("10"));
		assertEquals(false, IpUtils.isIPAddress("a.b.c.d"));
		assertEquals(false, IpUtils.isIPAddress("10.10.37.a"));
		assertEquals(false, IpUtils.isIPAddress("3.6.3.256"));
		assertEquals(false, IpUtils.isIPAddress("23.322.23.32"));
		assertEquals(false, IpUtils.isIPAddress("123.0101.99.6"));
		assertEquals(false, IpUtils.isIPAddress("2222.222.22.2"));
		assertEquals(false, IpUtils.isIPAddress("2.22.222.2222"));
	}

	@Test
	public void testShrinkRange(){
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/0", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/1", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/2", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/3", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/4", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/5", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/6", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/7", 8));
		assertEquals("123.123.123.123/8", IpUtils.shrinkIpRange("123.123.123.123/8", 8));
		assertEquals("123.123.123.123/9", IpUtils.shrinkIpRange("123.123.123.123/9", 8));
		assertEquals("123.123.123.123/10", IpUtils.shrinkIpRange("123.123.123.123/10", 8));
		assertEquals("123.123.123.123/11", IpUtils.shrinkIpRange("123.123.123.123/11", 8));
		assertEquals("123.123.123.123/12", IpUtils.shrinkIpRange("123.123.123.123/12", 8));
		assertEquals("123.123.123.123/13", IpUtils.shrinkIpRange("123.123.123.123/13", 8));
		assertEquals("123.123.123.123/14", IpUtils.shrinkIpRange("123.123.123.123/14", 8));
		assertEquals("123.123.123.123/15", IpUtils.shrinkIpRange("123.123.123.123/15", 8));
		assertEquals("123.123.123.123/16", IpUtils.shrinkIpRange("123.123.123.123/16", 8));
		assertEquals("123.123.123.123/17", IpUtils.shrinkIpRange("123.123.123.123/17", 8));
		assertEquals("123.123.123.123/18", IpUtils.shrinkIpRange("123.123.123.123/18", 8));
		assertEquals("123.123.123.123/19", IpUtils.shrinkIpRange("123.123.123.123/19", 8));
		assertEquals("123.123.123.123/20", IpUtils.shrinkIpRange("123.123.123.123/20", 8));
		assertEquals("123.123.123.123/21", IpUtils.shrinkIpRange("123.123.123.123/21", 8));
		assertEquals("123.123.123.123/22", IpUtils.shrinkIpRange("123.123.123.123/22", 8));
		assertEquals("123.123.123.123/23", IpUtils.shrinkIpRange("123.123.123.123/23", 8));
		assertEquals("123.123.123.123/24", IpUtils.shrinkIpRange("123.123.123.123/24", 8));
		assertEquals("123.123.123.123/25", IpUtils.shrinkIpRange("123.123.123.123/25", 8));
		assertEquals("123.123.123.123/26", IpUtils.shrinkIpRange("123.123.123.123/26", 8));
		assertEquals("123.123.123.123/27", IpUtils.shrinkIpRange("123.123.123.123/27", 8));
		assertEquals("123.123.123.123/28", IpUtils.shrinkIpRange("123.123.123.123/28", 8));
		assertEquals("123.123.123.123/29", IpUtils.shrinkIpRange("123.123.123.123/29", 8));
		assertEquals("123.123.123.123/30", IpUtils.shrinkIpRange("123.123.123.123/30", 8));
		assertEquals("123.123.123.123/31", IpUtils.shrinkIpRange("123.123.123.123/31", 8));
		assertEquals("123.123.123.123/32", IpUtils.shrinkIpRange("123.123.123.123/32", 8));
	}

	@Test
	public void isIPRange(){
		// Too big
		assertFalse(IpUtils.isIpRange("123.123.123.123/3456"));
		assertFalse(IpUtils.isIpRange("123.123.123.123/33"));
		// Just the right size
		assertTrue(IpUtils.isIpRange("123.123.123.123/32"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/31"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/30"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/29"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/28"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/27"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/26"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/25"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/24"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/23"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/22"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/21"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/20"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/19"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/18"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/17"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/16"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/15"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/14"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/13"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/12"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/11"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/10"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/9"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/8"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/7"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/6"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/5"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/4"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/3"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/2"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/1"));
		assertTrue(IpUtils.isIpRange("123.123.123.123/0"));
		// No size
		assertFalse(IpUtils.isIpRange("123.123.123.123/"));
		assertFalse(IpUtils.isIpRange("123.123.123.123"));
		// Characters (a-z) and special characters (;) have no business being in
		// an IP address
		assertFalse(IpUtils.isIpRange("123.123.123.123/ab"));
		assertFalse(IpUtils.isIpRange("123.123.723.123/32"));
		assertFalse(IpUtils.isIpRange("123.123;123.123/32"));
		assertFalse(IpUtils.isIpRange("123.xyz.123.123/32"));

	}

	@Test
	public void testConvertIpIntToString() {
		assertEquals(0, IpUtils.convertIpStringToInt("0.0.0.0"));
		assertEquals(0x000000ff, IpUtils.convertIpStringToInt("0.0.0.255"));
		assertEquals(0x0000ff00, IpUtils.convertIpStringToInt("0.0.255.0"));
		assertEquals(0x00ff0000, IpUtils.convertIpStringToInt("0.255.0.0"));
		assertEquals(0xff000000, IpUtils.convertIpStringToInt("255.0.0.0"));
		assertEquals(0xffffffff, IpUtils.convertIpStringToInt("255.255.255.255"));
		assertEquals(521319188, IpUtils.convertIpStringToInt("31.18.179.20"));

		exception.expect(EblockerException.class);
		IpUtils.convertIpStringToInt(null);
	}

	@Test
	public void testConvertIpStringToInt() {
		assertEquals("0.0.0.0", IpUtils.convertIpIntToString(0));
		assertEquals("0.0.0.255", IpUtils.convertIpIntToString(0x000000ff));
		assertEquals("0.0.255.0", IpUtils.convertIpIntToString(0x0000ff00));
		assertEquals("0.255.0.0", IpUtils.convertIpIntToString(0x00ff0000));
		assertEquals("255.0.0.0", IpUtils.convertIpIntToString(0xff000000));
		assertEquals("255.255.255.255", IpUtils.convertIpIntToString(0xffffffff));
		assertEquals("31.18.179.20", IpUtils.convertIpIntToString(521319188));
	}

	@Test
	public void testNetMaskToCidr() {
		assertEquals(0, IpUtils.convertNetMaskToCidr(0x00000000));
		assertEquals(1, IpUtils.convertNetMaskToCidr(0x80000000));
		assertEquals(2, IpUtils.convertNetMaskToCidr(0xc0000000));
		assertEquals(3, IpUtils.convertNetMaskToCidr(0xe0000000));
		assertEquals(4, IpUtils.convertNetMaskToCidr(0xf0000000));
		assertEquals(5, IpUtils.convertNetMaskToCidr(0xf8000000));
		assertEquals(6, IpUtils.convertNetMaskToCidr(0xfc000000));
		assertEquals(7, IpUtils.convertNetMaskToCidr(0xfe000000));
		assertEquals(8, IpUtils.convertNetMaskToCidr(0xff000000));
		assertEquals(9, IpUtils.convertNetMaskToCidr(0xff800000));
		assertEquals(10, IpUtils.convertNetMaskToCidr(0xffc00000));
		assertEquals(11, IpUtils.convertNetMaskToCidr(0xffe00000));
		assertEquals(12, IpUtils.convertNetMaskToCidr(0xfff00000));
		assertEquals(13, IpUtils.convertNetMaskToCidr(0xfff80000));
		assertEquals(14, IpUtils.convertNetMaskToCidr(0xfffc0000));
		assertEquals(15, IpUtils.convertNetMaskToCidr(0xfffe0000));
		assertEquals(16, IpUtils.convertNetMaskToCidr(0xffff0000));
		assertEquals(17, IpUtils.convertNetMaskToCidr(0xffff8000));
		assertEquals(18, IpUtils.convertNetMaskToCidr(0xffffc000));
		assertEquals(19, IpUtils.convertNetMaskToCidr(0xffffe000));
		assertEquals(20, IpUtils.convertNetMaskToCidr(0xfffff000));
		assertEquals(21, IpUtils.convertNetMaskToCidr(0xfffff800));
		assertEquals(22, IpUtils.convertNetMaskToCidr(0xfffffc00));
		assertEquals(23, IpUtils.convertNetMaskToCidr(0xfffffe00));
		assertEquals(24, IpUtils.convertNetMaskToCidr(0xffffff00));
		assertEquals(25, IpUtils.convertNetMaskToCidr(0xffffff80));
		assertEquals(26, IpUtils.convertNetMaskToCidr(0xffffffc0));
		assertEquals(27, IpUtils.convertNetMaskToCidr(0xffffffe0));
		assertEquals(28, IpUtils.convertNetMaskToCidr(0xfffffff0));
		assertEquals(29, IpUtils.convertNetMaskToCidr(0xfffffff8));
		assertEquals(30, IpUtils.convertNetMaskToCidr(0xfffffffc));
		assertEquals(31, IpUtils.convertNetMaskToCidr(0xfffffffe));
		assertEquals(32, IpUtils.convertNetMaskToCidr(0xffffffff));
	}

	@Test
	public void testCidrToNetMask() {
		assertEquals(0x00000000, IpUtils.convertCidrToNetMask(0));
		assertEquals(0x80000000, IpUtils.convertCidrToNetMask(1));
		assertEquals(0xc0000000, IpUtils.convertCidrToNetMask(2));
		assertEquals(0xe0000000, IpUtils.convertCidrToNetMask(3));
		assertEquals(0xf0000000, IpUtils.convertCidrToNetMask(4));
		assertEquals(0xf8000000, IpUtils.convertCidrToNetMask(5));
		assertEquals(0xfc000000, IpUtils.convertCidrToNetMask(6));
		assertEquals(0xfe000000, IpUtils.convertCidrToNetMask(7));
		assertEquals(0xff000000, IpUtils.convertCidrToNetMask(8));
		assertEquals(0xff800000, IpUtils.convertCidrToNetMask(9));
		assertEquals(0xffc00000, IpUtils.convertCidrToNetMask(10));
		assertEquals(0xffe00000, IpUtils.convertCidrToNetMask(11));
		assertEquals(0xfff00000, IpUtils.convertCidrToNetMask(12));
		assertEquals(0xfff80000, IpUtils.convertCidrToNetMask(13));
		assertEquals(0xfffc0000, IpUtils.convertCidrToNetMask(14));
		assertEquals(0xfffe0000, IpUtils.convertCidrToNetMask(15));
		assertEquals(0xffff0000, IpUtils.convertCidrToNetMask(16));
		assertEquals(0xffff8000, IpUtils.convertCidrToNetMask(17));
		assertEquals(0xffffc000, IpUtils.convertCidrToNetMask(18));
		assertEquals(0xffffe000, IpUtils.convertCidrToNetMask(19));
		assertEquals(0xfffff000, IpUtils.convertCidrToNetMask(20));
		assertEquals(0xfffff800, IpUtils.convertCidrToNetMask(21));
		assertEquals(0xfffffc00, IpUtils.convertCidrToNetMask(22));
		assertEquals(0xfffffe00, IpUtils.convertCidrToNetMask(23));
		assertEquals(0xffffff00, IpUtils.convertCidrToNetMask(24));
		assertEquals(0xffffff80, IpUtils.convertCidrToNetMask(25));
		assertEquals(0xffffffc0, IpUtils.convertCidrToNetMask(26));
		assertEquals(0xffffffe0, IpUtils.convertCidrToNetMask(27));
		assertEquals(0xfffffff0, IpUtils.convertCidrToNetMask(28));
		assertEquals(0xfffffff8, IpUtils.convertCidrToNetMask(29));
		assertEquals(0xfffffffc, IpUtils.convertCidrToNetMask(30));
		assertEquals(0xfffffffe, IpUtils.convertCidrToNetMask(31));
		assertEquals(0xffffffff, IpUtils.convertCidrToNetMask(32));
	}

	@Test
	public void testGetSubnet() {
		assertEquals("10.0.0.0/8", IpUtils.getSubnet("10.10.10.10", "255.0.0.0"));
		assertEquals("172.16.0.0/12", IpUtils.getSubnet("172.16.10.10", "255.240.0.0"));
		assertEquals("192.168.10.0/24", IpUtils.getSubnet("192.168.10.10", "255.255.255.0"));
	}

	@Test
	public void testIsInSubnet() {
	    assertTrue(IpUtils.isInSubnet("192.168.2.11", "192.168.2.0", "255.255.255.128"));
	    assertFalse(IpUtils.isInSubnet("192.168.99.11", "192.168.2.0", "255.255.255.0"));

        assertFalse(IpUtils.isInSubnet(0x3f579cf5, 0xc0a80200, 0xffffff80));
        assertTrue(IpUtils.isInSubnet(0xc0a8020b, 0xc0a80200, 0xffffff80));
	}

	@Test
    public void testConvertIpRangeToIpNetmask() {
	    int[] ipNetmask = IpUtils.convertIpRangeToIpNetmask("10.11.12.0/24");
        Assert.assertNotNull(ipNetmask);
        Assert.assertEquals(2, ipNetmask.length);
        Assert.assertEquals(0x0a0b0c00, ipNetmask[0]);
        Assert.assertEquals(0xffffff00, ipNetmask[1]);
    }

    @Test
    public void testConvertIpToBytes() {
	    byte[] ipBytes = IpUtils.convertIpToBytes(0x0a0b0c0d);
	    Assert.assertEquals(0xa, ipBytes[0]);
        Assert.assertEquals(0xb, ipBytes[1]);
        Assert.assertEquals(0xc, ipBytes[2]);
        Assert.assertEquals(0xd, ipBytes[3]);

        ipBytes = IpUtils.convertIpToBytes(0xfffefdfc);
        Assert.assertEquals((byte)0xff, ipBytes[0]);
        Assert.assertEquals((byte)0xfe, ipBytes[1]);
        Assert.assertEquals((byte)0xfd, ipBytes[2]);
        Assert.assertEquals((byte)0xfc, ipBytes[3]);
    }

    @Test
    public void testConvertBytesToIp() {
        assertEquals(0x0a0b0c0d, IpUtils.convertBytesToIp(new byte[]{ 0x0a, 0x0b, 0x0c, 0x0d }));
        assertEquals(0xfffefdfc, IpUtils.convertBytesToIp(new byte[]{ (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc }));
    }
}
