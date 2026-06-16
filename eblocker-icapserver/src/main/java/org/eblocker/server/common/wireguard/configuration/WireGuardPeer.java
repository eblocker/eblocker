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
public class WireGuardPeer {
    private String publicKey;
    private String presharedKey;
    private String endpoint;
    private List<String> allowedIps = new ArrayList<>();
    private Integer persistentKeepalive;

    public WireGuardPeer() {
    }

    public WireGuardPeer(String publicKey, String presharedKey, String endpoint, List<String> allowedIps,
                         Integer persistentKeepalive) {
        this.publicKey = publicKey;
        this.presharedKey = presharedKey;
        this.endpoint = endpoint;
        this.allowedIps = copy(allowedIps);
        this.persistentKeepalive = persistentKeepalive;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPresharedKey() {
        return presharedKey;
    }

    public void setPresharedKey(String presharedKey) {
        this.presharedKey = presharedKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<String> getAllowedIps() {
        return immutableCopy(allowedIps);
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = copy(allowedIps);
    }

    public Integer getPersistentKeepalive() {
        return persistentKeepalive;
    }

    public void setPersistentKeepalive(Integer persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
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
