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

public class PrefixOption extends Option {

    public static final int TYPE = 3;

    private final short prefixLength;
    private final boolean onLink;
    private final boolean autonomousAddressConfiguration;
    private final long validLifetime;
    private final long preferredLifetime;
    private final Ip6Address prefix;

    public PrefixOption(short prefixLength, boolean onLink, boolean autonomousAddressConfiguration,
                        long validLifetime, long preferredLifetime, Ip6Address prefix) {
        super(TYPE);
        this.prefixLength = prefixLength;
        this.onLink = onLink;
        this.autonomousAddressConfiguration = autonomousAddressConfiguration;
        this.validLifetime = validLifetime;
        this.preferredLifetime = preferredLifetime;
        this.prefix = prefix;
    }

    public short getPrefixLength() {
        return prefixLength;
    }

    public boolean isOnLink() {
        return onLink;
    }

    public boolean isAutonomousAddressConfiguration() {
        return autonomousAddressConfiguration;
    }

    public long getValidLifetime() {
        return validLifetime;
    }

    public long getPreferredLifetime() {
        return preferredLifetime;
    }

    public Ip6Address getPrefix() {
        return prefix;
    }

    @Override
    protected void toString(StringBuilder sb) {
        super.toString(sb);
        sb.append("/");
        sb.append(prefixLength);
        sb.append("/");
        sb.append(onLink ? 1 : 0);
        sb.append("/");
        sb.append(autonomousAddressConfiguration ? 1 : 0);
        sb.append("/");
        sb.append(validLifetime);
        sb.append("/");
        sb.append(preferredLifetime);
        sb.append("/");
        sb.append(DatatypeConverter.printHexBinary(prefix.getAddress()).toLowerCase());
    }
}
