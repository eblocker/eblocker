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
package org.eblocker.server.common.network;

import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkUtilsTest {

    @Test
    public void testNetworkMask() {
        assertEquals("255.0.0.0", NetworkUtils.getIPv4NetworkMask(8));
        assertEquals("255.240.0.0", NetworkUtils.getIPv4NetworkMask(12));
        assertEquals("255.255.0.0", NetworkUtils.getIPv4NetworkMask(16));
        assertEquals("255.255.254.0", NetworkUtils.getIPv4NetworkMask(23));
        assertEquals("255.255.255.0", NetworkUtils.getIPv4NetworkMask(24));
        assertEquals("255.255.255.252", NetworkUtils.getIPv4NetworkMask(30));
    }

    @Test(expected = EblockerException.class)
    public void nobodyHasANetworkLargerThanClassA() {
        NetworkUtils.getIPv4NetworkMask(7);
    }

    @Test(expected = EblockerException.class)
    public void noUsableIpAddresses() {
        NetworkUtils.getIPv4NetworkMask(31);
    }

    @Test
    public void testNetworkAddress() {
        assertEquals("192.168.3.0", NetworkUtils.getIPv4NetworkAddress("192.168.3.23", "255.255.255.0"));
        assertEquals("192.168.0.0", NetworkUtils.getIPv4NetworkAddress("192.168.3.23", "255.255.0.0"));
        assertEquals("10.0.0.0", NetworkUtils.getIPv4NetworkAddress("10.16.3.23", "255.0.0.0"));
    }

    @Test
    public void testPrefixLength() {
        assertEquals(8, NetworkUtils.getPrefixLength("255.0.0.0"));
        assertEquals(12, NetworkUtils.getPrefixLength("255.240.0.0"));
        assertEquals(16, NetworkUtils.getPrefixLength("255.255.0.0"));
        assertEquals(23, NetworkUtils.getPrefixLength("255.255.254.0"));
        assertEquals(24, NetworkUtils.getPrefixLength("255.255.255.0"));
        assertEquals(30, NetworkUtils.getPrefixLength("255.255.255.252"));
    }

    @Test(expected = EblockerException.class)
    public void invalidNetworkMaskMissingBit() {
        NetworkUtils.getPrefixLength("255.160.0.0"); // 1111 1111 . 1010 0000 . 0000 0000 . 0000 0000
    }

    @Test(expected = EblockerException.class)
    public void invalidNetworkMaskMissingByte() {
        NetworkUtils.getPrefixLength("255.0.255.0");
    }

    @Test(expected = EblockerException.class)
    public void invalidNetworkMaskTooManyBits() {
        NetworkUtils.getPrefixLength("255.255.255.255");
    }

    @Test(expected = EblockerException.class)
    public void invalidNetworkMaskNotEnoughBits() {
        NetworkUtils.getPrefixLength("128.0.0.0");
    }

    @Test
    public void replaceLastByte() {
        assertEquals("192.168.3.27", NetworkUtils.replaceLastByte("192.168.3.42", (byte) 27));
    }

    @Test(expected = EblockerException.class)
    public void replaceIpv6Address() {
        NetworkUtils.replaceLastByte("fe80::1", (byte) 27);
    }
}
