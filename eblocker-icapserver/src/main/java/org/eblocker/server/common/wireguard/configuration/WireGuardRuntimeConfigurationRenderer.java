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

import java.util.List;
import java.util.stream.Collectors;

public class WireGuardRuntimeConfigurationRenderer {

    public String render(WireGuardConfiguration configuration) {
        StringBuilder builder = new StringBuilder();
        builder.append("[Interface]\n");
        append(builder, "PrivateKey", configuration.getPrivateKey());
        appendList(builder, "Address", configuration.getAddresses());
        append(builder, "MTU", configuration.getMtu());
        builder.append("Table = off\n");

        for (WireGuardPeer peer : configuration.getPeers()) {
            builder.append('\n');
            builder.append("[Peer]\n");
            append(builder, "PublicKey", peer.getPublicKey());
            append(builder, "PresharedKey", peer.getPresharedKey());
            append(builder, "Endpoint", peer.getEndpoint());
            appendList(builder, "AllowedIPs", peer.getAllowedIps());
            append(builder, "PersistentKeepalive", peer.getPersistentKeepalive());
        }

        return builder.toString();
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value != null) {
            builder.append(key).append(" = ").append(value).append('\n');
        }
    }

    private void appendList(StringBuilder builder, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            append(builder, key, values.stream().collect(Collectors.joining(", ")));
        }
    }
}
