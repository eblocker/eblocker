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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WireGuardConfiguration {
    private final String privateKey;
    private final List<String> addresses;
    private final List<String> dnsServers;
    private final Integer mtu;
    private final List<WireGuardPeer> peers;

    public WireGuardConfiguration(String privateKey, List<String> addresses, List<String> dnsServers,
                                  Integer mtu, List<WireGuardPeer> peers) {
        this.privateKey = privateKey;
        this.addresses = immutableCopy(addresses);
        this.dnsServers = immutableCopy(dnsServers);
        this.mtu = mtu;
        this.peers = immutableCopy(peers);
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public List<String> getDnsServers() {
        return dnsServers;
    }

    public Integer getMtu() {
        return mtu;
    }

    public List<WireGuardPeer> getPeers() {
        return peers;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
