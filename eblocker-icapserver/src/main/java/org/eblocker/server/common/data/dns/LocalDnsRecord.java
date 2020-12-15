/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data.dns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;

public class LocalDnsRecord {

    private final boolean builtin;
    private final Ip4Address ipAddress;
    private final Ip6Address ip6Address;
    private final Ip4Address vpnIpAddress;
    private final Ip6Address vpnIp6Address;
    private final String name;
    private final boolean hidden;

    @JsonCreator
    public LocalDnsRecord(@JsonProperty("name") String name,
                          @JsonProperty("builtin") boolean builtin,
                          @JsonProperty("hidden") boolean hidden,
                          @JsonProperty("ipAddress") Ip4Address ipAddress,
                          @JsonProperty("ip6Address") Ip6Address ip6Address,
                          @JsonProperty("vpnIpAddress") Ip4Address vpnIpAddress,
                          @JsonProperty("vpnIp6Address") Ip6Address vpnIp6Address) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.ip6Address = ip6Address;
        this.builtin = builtin;
        this.hidden = hidden;
        this.vpnIpAddress = vpnIpAddress;
        this.vpnIp6Address = vpnIp6Address;
    }

    public String getName() {
        return name;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Ip4Address getIpAddress() {
        return ipAddress;
    }

    public Ip6Address getIp6Address() {
        return ip6Address;
    }

    public Ip4Address getVpnIpAddress() {
        return vpnIpAddress;
    }

    public Ip6Address getVpnIp6Address() {
        return vpnIp6Address;
    }
}
