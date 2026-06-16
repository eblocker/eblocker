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
package org.eblocker.server.common.wireguard;

import org.eblocker.server.common.data.wireguard.WireGuardMobilePeer;
import org.eblocker.server.common.data.wireguard.WireGuardMobileServer;
import org.junit.Assert;
import org.junit.Test;

public class WireGuardMobileConfigurationRendererTest {
    @Test
    public void rendersClientConfigurationForMobilePeer() {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPublicKey("server-public-key");
        WireGuardMobilePeer peer = new WireGuardMobilePeer();
        peer.setPrivateKey("peer-private-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.7/32");

        String config = new WireGuardMobileConfigurationRenderer().render(
                server,
                peer,
                "vpn.example.org",
                51820,
                "10.8.0.1",
                "0.0.0.0/0, ::/0",
                25);

        Assert.assertEquals("[Interface]\n" +
                "PrivateKey = peer-private-key\n" +
                "Address = 10.8.0.7/32\n" +
                "DNS = 10.8.0.1\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = server-public-key\n" +
                "PresharedKey = peer-psk\n" +
                "Endpoint = vpn.example.org:51820\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                "PersistentKeepalive = 25\n", config);
    }
}
