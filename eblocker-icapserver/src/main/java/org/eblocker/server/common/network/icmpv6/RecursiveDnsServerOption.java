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
package org.eblocker.server.common.network.icmpv6;

import org.eblocker.server.common.data.Ip6Address;

import javax.xml.bind.DatatypeConverter;
import java.util.List;

public class RecursiveDnsServerOption extends Option {
    public static final int TYPE = 25;

    private final long lifetime;
    private final List<Ip6Address> dnsServers;

    public RecursiveDnsServerOption(long lifetime, List<Ip6Address> dnsServers) {
        super(TYPE);
        this.lifetime = lifetime;
        this.dnsServers = dnsServers;
    }

    public long getLifetime() {
        return lifetime;
    }

    public List<Ip6Address> getDnsServers() {
        return dnsServers;
    }

    @Override
    protected void toString(StringBuilder sb) {
        super.toString(sb);
        sb.append("/");
        sb.append(lifetime);
        sb.append("/");
        sb.append(dnsServers.size());
        for (Ip6Address dnsServer : dnsServers) {
            sb.append("/");
            sb.append(DatatypeConverter.printHexBinary(dnsServer.getAddress()).toLowerCase());
        }
    }
}
