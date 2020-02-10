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

public class Ip4AddressTest {

    @Test
    public void testParsing() {
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("c0a80304"), Ip4Address.parse("192.168.3.4").getAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooFewFields() {
        Ip4Address.parse("192.168.3").getAddress();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooManyFields() {
        Ip4Address.parse("192.168.3.4.5").getAddress();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingTooLargeField() {
        Ip4Address.parse("192.168.300.4").getAddress();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingNegativeField() {
        Ip4Address.parse("192.168.-3.4").getAddress();
    }

    @Test
    public void testOf() {
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("c0a80304"), Ip4Address.of(DatatypeConverter.parseHexBinary("c0a80304")).getAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfTooSmall() {
        Ip4Address.of(DatatypeConverter.parseHexBinary("c0a803"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfTooLarge() {
        Ip4Address.of(DatatypeConverter.parseHexBinary("c0a8030405"));
    }

    @Test
    public void testToString() {
        Assert.assertEquals("192.168.3.4", Ip4Address.of(DatatypeConverter.parseHexBinary("c0a80304")).toString());
    }

    @Test
    public void testIsIpv4() {
        Assert.assertTrue(Ip4Address.parse("127.0.0.1").isIpv4());
    }

    @Test
    public void testIsIpv6() {
        Assert.assertFalse(Ip4Address.parse("127.0.0.1").isIpv6());
    }
}
