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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WireGuardConfiguration {
    private String privateKey;
    private List<String> addresses = new ArrayList<>();
    private List<String> dnsServers = new ArrayList<>();
    private Integer mtu;
    private List<WireGuardPeer> peers = new ArrayList<>();

    public WireGuardConfiguration() {
    }

    public WireGuardConfiguration(String privateKey, List<String> addresses, List<String> dnsServers,
                                  Integer mtu, List<WireGuardPeer> peers) {
        this.privateKey = privateKey;
        this.addresses = copy(addresses);
        this.dnsServers = copy(dnsServers);
        this.mtu = mtu;
        this.peers = copy(peers);
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public List<String> getAddresses() {
        return immutableCopy(addresses);
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = copy(addresses);
    }

    public List<String> getDnsServers() {
        return immutableCopy(dnsServers);
    }

    public void setDnsServers(List<String> dnsServers) {
        this.dnsServers = copy(dnsServers);
    }

    public Integer getMtu() {
        return mtu;
    }

    public void setMtu(Integer mtu) {
        this.mtu = mtu;
    }

    public List<WireGuardPeer> getPeers() {
        return immutableCopy(peers);
    }

    public void setPeers(List<WireGuardPeer> peers) {
        this.peers = copy(peers);
    }

    private static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(values);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
