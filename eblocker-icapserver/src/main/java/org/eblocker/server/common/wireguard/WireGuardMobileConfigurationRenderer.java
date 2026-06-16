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

public class WireGuardMobileConfigurationRenderer {
    public String render(WireGuardMobileServer server, WireGuardMobilePeer peer, String endpointHost, int endpointPort,
                         String dns, String allowedIps, int persistentKeepalive) {
        StringBuilder builder = new StringBuilder();
        builder.append("[Interface]\n");
        builder.append("PrivateKey = ").append(peer.getPrivateKey()).append('\n');
        builder.append("Address = ").append(peer.getAddress()).append('\n');
        if (dns != null && !dns.isEmpty()) {
            builder.append("DNS = ").append(dns).append('\n');
        }
        builder.append('\n');
        builder.append("[Peer]\n");
        builder.append("PublicKey = ").append(server.getPublicKey()).append('\n');
        if (peer.getPresharedKey() != null && !peer.getPresharedKey().isEmpty()) {
            builder.append("PresharedKey = ").append(peer.getPresharedKey()).append('\n');
        }
        builder.append("Endpoint = ").append(endpointHost).append(':').append(endpointPort).append('\n');
        builder.append("AllowedIPs = ").append(allowedIps).append('\n');
        if (persistentKeepalive > 0) {
            builder.append("PersistentKeepalive = ").append(persistentKeepalive).append('\n');
        }
        return builder.toString();
    }
}
