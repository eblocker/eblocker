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

import java.util.List;

public abstract class NeighborDiscoveryMessage extends Icmp6Message {
    private final List<Option> options;

    public NeighborDiscoveryMessage(byte[] sourceHardwareAddress,
                                    Ip6Address sourceAddress,
                                    byte[] destinationHardwareAddress,
                                    Ip6Address destinationAddress,
                                    int icmpType,
                                    List<Option> options) {
        super(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, icmpType);
        this.options = options;
    }

    public List<Option> getOptions() {
        return options;
    }

    @Override
    protected final void toString(StringBuilder sb) {
        super.toString(sb);
        appendParameter(sb);
        for(Option option : options) {
            sb.append("/");
            option.toString(sb);
        }
    }

    protected abstract void appendParameter(StringBuilder sb);
}
