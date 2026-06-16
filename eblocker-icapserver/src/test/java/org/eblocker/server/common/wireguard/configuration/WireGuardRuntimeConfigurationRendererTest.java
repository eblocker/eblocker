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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class WireGuardRuntimeConfigurationRendererTest {

    @Test
    public void rendersProviderRuntimeConfigWithPolicyRoutingDisabled() {
        WireGuardConfiguration configuration = new WireGuardConfiguration(
                "private=",
                Arrays.asList("10.66.66.2/32", "fd42:42:42::2/128"),
                Arrays.asList("10.66.66.1", "fd42:42:42::1"),
                1420,
                Arrays.asList(new WireGuardPeer("public=", "psk=", "vpn.example.net:51820", Arrays.asList("0.0.0.0/0", "::/0"), 25)));

        String rendered = new WireGuardRuntimeConfigurationRenderer().render(configuration);

        Assert.assertEquals("[Interface]\n" +
                "PrivateKey = private=\n" +
                "Address = 10.66.66.2/32, fd42:42:42::2/128\n" +
                "DNS = 10.66.66.1, fd42:42:42::1\n" +
                "MTU = 1420\n" +
                "Table = off\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = public=\n" +
                "PresharedKey = psk=\n" +
                "Endpoint = vpn.example.net:51820\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                "PersistentKeepalive = 25\n", rendered);
    }

    @Test
    public void omitsUnsetOptionalValuesAndRendersMultiplePeers() {
        WireGuardConfiguration configuration = new WireGuardConfiguration(
                "private=",
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Arrays.asList(
                        new WireGuardPeer("peer1=", null, null, Arrays.asList("10.0.0.0/8"), null),
                        new WireGuardPeer("peer2=", null, "192.0.2.1:51820", Collections.emptyList(), 15)));

        String rendered = new WireGuardRuntimeConfigurationRenderer().render(configuration);

        Assert.assertEquals("[Interface]\n" +
                "PrivateKey = private=\n" +
                "Table = off\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = peer1=\n" +
                "AllowedIPs = 10.0.0.0/8\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = peer2=\n" +
                "Endpoint = 192.0.2.1:51820\n" +
                "PersistentKeepalive = 15\n", rendered);
    }
}
