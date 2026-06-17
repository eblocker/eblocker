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

import java.util.Collection;

public class WireGuardMobileServerConfigurationRenderer {
    public String render(WireGuardMobileServer server, Collection<WireGuardMobilePeer> peers, int listenPort) {
        StringBuilder builder = new StringBuilder();
        builder.append("[Interface]\n");
        builder.append("PrivateKey = ").append(server.getPrivateKey()).append('\n');
        builder.append("Address = ").append(server.getAddress()).append('\n');
        builder.append("ListenPort = ").append(listenPort).append('\n');

        for (WireGuardMobilePeer peer : peers) {
            builder.append('\n');
            builder.append("[Peer]\n");
            if (peer.getDeviceId() != null && !peer.getDeviceId().isEmpty()) {
                builder.append("# ").append(peer.getDeviceId()).append('\n');
            }
            builder.append("PublicKey = ").append(peer.getPublicKey()).append('\n');
            if (peer.getPresharedKey() != null && !peer.getPresharedKey().isEmpty()) {
                builder.append("PresharedKey = ").append(peer.getPresharedKey()).append('\n');
            }
            builder.append("AllowedIPs = ").append(formatAllowedIps(peer)).append('\n');
        }

        return builder.toString();
    }

    private String formatAllowedIps(WireGuardMobilePeer peer) {
        if (peer.getAddressIp6() == null || peer.getAddressIp6().isEmpty()) {
            return peer.getAddress();
        }
        return peer.getAddress() + ", " + peer.getAddressIp6();
    }
}
