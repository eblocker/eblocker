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

import java.util.Arrays;

public class WireGuardMobileServerConfigurationRendererTest {
    @Test
    public void rendersServerConfigurationWithPeers() {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setAddress("10.8.0.1/24");

        WireGuardMobilePeer peer = new WireGuardMobilePeer(7, "device:001122334455");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.7/32");

        String config = new WireGuardMobileServerConfigurationRenderer().render(server, Arrays.asList(peer), 51820);

        Assert.assertEquals("[Interface]\n" +
                "PrivateKey = server-private-key\n" +
                "Address = 10.8.0.1/24\n" +
                "ListenPort = 51820\n" +
                "\n" +
                "[Peer]\n" +
                "# device:001122334455\n" +
                "PublicKey = peer-public-key\n" +
                "PresharedKey = peer-psk\n" +
                "AllowedIPs = 10.8.0.7/32\n", config);
    }
}
