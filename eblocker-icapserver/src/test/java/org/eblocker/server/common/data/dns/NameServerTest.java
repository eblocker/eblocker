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
package org.eblocker.server.common.data.dns;

import org.eblocker.server.common.data.IpAddress;
import org.junit.Assert;
import org.junit.Test;


public class NameServerTest {

    @Test
    public void testParsing() {
        NameServer nameServer = NameServer.parse("10.10.10.10");
        Assert.assertEquals(NameServer.Protocol.UDP, nameServer.getProtocol());
        Assert.assertEquals(IpAddress.parse("10.10.10.10"), nameServer.getAddress());
        Assert.assertEquals(53, nameServer.getPort());

        nameServer = NameServer.parse("tcp:10.10.10.11:5353");
        Assert.assertEquals(NameServer.Protocol.TCP, nameServer.getProtocol());
        Assert.assertEquals(IpAddress.parse("10.10.10.11"), nameServer.getAddress());
        Assert.assertEquals(5353, nameServer.getPort());

        nameServer = NameServer.parse("fe80::10:10:10:10");
        Assert.assertEquals(NameServer.Protocol.UDP, nameServer.getProtocol());
        Assert.assertEquals(IpAddress.parse("fe80::10:10:10:10"), nameServer.getAddress());
        Assert.assertEquals(53, nameServer.getPort());

        nameServer = NameServer.parse("tcp:[fe80::10:10:10:11]:5353");
        Assert.assertEquals(NameServer.Protocol.TCP, nameServer.getProtocol());
        Assert.assertEquals(IpAddress.parse("fe80::10:10:10:11"), nameServer.getAddress());
        Assert.assertEquals(5353, nameServer.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingMissingPort4() {
        NameServer.parse("tcp:10.10.10.11");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingMissingProtocol4() {
        NameServer.parse("10.10.10.11:5353");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingMissingPort6() {
        NameServer.parse("tcp:[fe80::1]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingMissingProtocol6() {
        NameServer.parse("[fe80::1]:5353");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingAmbigiousIp6() {
        NameServer.parse("tcp:fe80::1:53");
    }

    @Test
    public void testToString() {
        Assert.assertEquals("10.10.10.10", new NameServer(NameServer.Protocol.UDP, IpAddress.parse("10.10.10.10"), 53).toString());
        Assert.assertEquals("udp:10.10.10.10:5353", new NameServer(NameServer.Protocol.UDP, IpAddress.parse("10.10.10.10"), 5353).toString());
        Assert.assertEquals("tcp:10.10.10.10:53", new NameServer(NameServer.Protocol.TCP, IpAddress.parse("10.10.10.10"), 53).toString());
        Assert.assertEquals("2001:4860:4802:32::a", new NameServer(NameServer.Protocol.UDP, IpAddress.parse("2001:4860:4802:32::a"), 53).toString());
        Assert.assertEquals("udp:[2001:4860:4802:32::a]:5353", new NameServer(NameServer.Protocol.UDP, IpAddress.parse("2001:4860:4802:32::a"), 5353).toString());
        Assert.assertEquals("tcp:[2001:4860:4802:32::a]:53", new NameServer(NameServer.Protocol.TCP, IpAddress.parse("2001:4860:4802:32::a"), 53).toString());
    }
}
