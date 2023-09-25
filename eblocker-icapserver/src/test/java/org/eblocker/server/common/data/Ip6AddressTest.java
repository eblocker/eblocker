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

public class Ip6AddressTest {

    @Test
    public void testParsing() {
        assertParse("20010db80001000200080800200c417a", "2001:db8:1:2:8:800:200c:417a");
        assertParse("20010db80000000000080800200c417a", "2001:db8::8:800:200c:417a");
        assertParse("FF010000000000000000000000000101", "FF01::101");
        assertParse("00010000000000000000000000000000", "1::");
        assertParse("00000000000000000000000000000001", "::1");
        assertParse("00000000000000000000000000000000", "::");

        assertParse("20010db8000000000001000000000001", "2001:db8:0:0:1:0:0:1");
        assertParse("20010db8000000000001000000000001", "2001:0db8:0:0:1:0:0:1");
        assertParse("20010db8000000000001000000000001", "2001:db8::1:0:0:1");
        assertParse("20010db8000000000001000000000001", "2001:db8::0:1:0:0:1");
        assertParse("20010db8000000000001000000000001", "2001:0db8::1:0:0:1");
        assertParse("20010db8000000000001000000000001", "2001:db8:0:0:1::1");
        assertParse("20010db8000000000001000000000001", "2001:db8:0000:0:1::1");
        assertParse("20010db8000000000001000000000001", "2001:DB8:0:0:1::1");
    }

    private void assertParse(String expectedHex, String ipAddress) {
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary(expectedHex), Ip6Address.parse(ipAddress).getAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingAmbiguousCollapsedZeros() {
        Ip6Address.parse("1::1::1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooManyFieldsNoCollapsedZeros() {
        Ip6Address.parse("2001:db8:0:0:0:800:200c:417a:abcd");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooManyFieldsCollapsedZeros() {
        Ip6Address.parse("2001:db8::800:200c:417a:dead:beef:dead");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooLargeField() {
        Ip6Address.parse("2001:db8:1:2:8:800:0200c:417a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooLargeField2() {
        Ip6Address.parse("2001:db8:1::8:800:0200c:417a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingNonHexadecimalField() {
        Ip6Address.parse("2001:db8:1:2:abcq:800:200c:417a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingNotEnoughFields() {
        Ip6Address.parse("1:2:3:4:5:6:7");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingEmptyString() {
        Ip6Address.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTrailingGarbage() {
        Ip6Address.parse("1:2:3:4:5:6:7:8::whatever");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingLastFieldEmpty() {
        Ip6Address.parse("1:2:3:4:5:6:7:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingLastFieldEmptyCollapsedZeros() {
        Ip6Address.parse("1:2:3::5:6:7:");
    }

    @Test
    public void testOf() {
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("000102030405060708090a0b0c0d0e0f"), Ip6Address.of(DatatypeConverter.parseHexBinary("000102030405060708090a0b0c0d0e0f")).getAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfTooSmall() {
        Ip6Address.of(DatatypeConverter.parseHexBinary("000102030405060708090a0b0c0d0e"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfTooLarge() {
        Ip6Address.of(DatatypeConverter.parseHexBinary("000102030405060708090a0b0c0d0e0f10"));
    }

    @Test
    public void testToString() {
        assertToString("20010db8000000000001000000000001", "2001:db8::1:0:0:1");
        assertToString("20010db80001000200080800200c417a", "2001:db8:1:2:8:800:200c:417a");
        assertToString("20010db80000000000080800200c417a", "2001:db8::8:800:200c:417a");
        assertToString("FF010000000000000000000000000101", "ff01::101");
        assertToString("00010000000000000000000000000000", "1::");
        assertToString("00000000000000000000000000000001", "::1");
        assertToString("00000000000000000000000000000000", "::");
    }

    private void assertToString(String ipAddressHex, String expected) {
        Assert.assertEquals(expected, Ip6Address.of(DatatypeConverter.parseHexBinary(ipAddressHex)).toString());
    }

    @Test
    public void testIsIpv4() {
        Assert.assertFalse(Ip6Address.parse("::1").isIpv4());
    }

    @Test
    public void testIsIpv6() {
        Assert.assertTrue(Ip6Address.parse("::1").isIpv6());
    }
}
