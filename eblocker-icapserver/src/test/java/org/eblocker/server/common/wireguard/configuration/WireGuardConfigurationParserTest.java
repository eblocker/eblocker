/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.wireguard.configuration;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.Assert;
import org.junit.Test;

public class WireGuardConfigurationParserTest {

    @Test
    public void parsesProviderConfiguration() throws WireGuardConfigurationParser.ParseException {
        String config = ResourceHandler.load(new SimpleResource("classpath:test-data/wireguard/provider-valid.conf"));

        WireGuardConfiguration parsed = new WireGuardConfigurationParser().parse(config);

        Assert.assertEquals("client-private-key=", parsed.getPrivateKey());
        Assert.assertArrayEquals(new String[]{ "10.66.66.2/32", "fd42:42:42::2/128" }, parsed.getAddresses().toArray());
        Assert.assertArrayEquals(new String[]{ "10.66.66.1", "fd42:42:42::1" }, parsed.getDnsServers().toArray());
        Assert.assertEquals(Integer.valueOf(1420), parsed.getMtu());

        Assert.assertEquals(1, parsed.getPeers().size());
        WireGuardPeer peer = parsed.getPeers().get(0);
        Assert.assertEquals("provider-public-key=", peer.getPublicKey());
        Assert.assertEquals("provider-preshared-key=", peer.getPresharedKey());
        Assert.assertEquals("vpn.example.net:51820", peer.getEndpoint());
        Assert.assertArrayEquals(new String[]{ "0.0.0.0/0", "::/0" }, peer.getAllowedIps().toArray());
        Assert.assertEquals(Integer.valueOf(25), peer.getPersistentKeepalive());
    }

    @Test
    public void parsesMultiplePeersAndIgnoresCommentsAndWhitespace() throws WireGuardConfigurationParser.ParseException {
        String config = ResourceHandler.load(new SimpleResource("classpath:test-data/wireguard/provider-multi-peer.conf"));

        WireGuardConfiguration parsed = new WireGuardConfigurationParser().parse(config);

        Assert.assertEquals("another-client-private-key=", parsed.getPrivateKey());
        Assert.assertArrayEquals(new String[]{ "192.0.2.10/32" }, parsed.getAddresses().toArray());
        Assert.assertArrayEquals(new String[]{ "192.0.2.53" }, parsed.getDnsServers().toArray());
        Assert.assertNull(parsed.getMtu());
        Assert.assertEquals(2, parsed.getPeers().size());

        Assert.assertEquals("peer-one-public-key=", parsed.getPeers().get(0).getPublicKey());
        Assert.assertArrayEquals(new String[]{ "10.0.0.0/8", "192.168.0.0/16" }, parsed.getPeers().get(0).getAllowedIps().toArray());
        Assert.assertNull(parsed.getPeers().get(0).getEndpoint());

        Assert.assertEquals("peer-two-public-key=", parsed.getPeers().get(1).getPublicKey());
        Assert.assertEquals("2001:db8::1:51820", parsed.getPeers().get(1).getEndpoint());
        Assert.assertArrayEquals(new String[]{ "2000::/3" }, parsed.getPeers().get(1).getAllowedIps().toArray());
        Assert.assertEquals(Integer.valueOf(15), parsed.getPeers().get(1).getPersistentKeepalive());
    }

    @Test(expected = WireGuardConfigurationParser.ParseException.class)
    public void rejectsMissingInterfacePrivateKey() throws WireGuardConfigurationParser.ParseException {
        new WireGuardConfigurationParser().parse("[Interface]\nAddress = 10.0.0.2/32\n[Peer]\nPublicKey = peer=\nAllowedIPs = 0.0.0.0/0\n");
    }

    @Test(expected = WireGuardConfigurationParser.ParseException.class)
    public void rejectsPeerWithoutPublicKey() throws WireGuardConfigurationParser.ParseException {
        new WireGuardConfigurationParser().parse("[Interface]\nPrivateKey = private=\n[Peer]\nAllowedIPs = 0.0.0.0/0\n");
    }

    @Test(expected = WireGuardConfigurationParser.ParseException.class)
    public void rejectsKeysOutsideSection() throws WireGuardConfigurationParser.ParseException {
        new WireGuardConfigurationParser().parse("PrivateKey = private=\n[Peer]\nPublicKey = peer=\nAllowedIPs = 0.0.0.0/0\n");
    }
}
