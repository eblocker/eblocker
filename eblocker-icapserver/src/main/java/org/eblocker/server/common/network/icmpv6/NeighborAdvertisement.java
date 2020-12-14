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

public class NeighborAdvertisement extends NeighborDiscoveryMessage {

    public static final int ICMP_TYPE = 136;

    private final boolean router;
    private final boolean solicited;
    private final boolean override;
    private final Ip6Address targetAddress;

    public NeighborAdvertisement(byte[] sourceHardwareAddress,
                                 Ip6Address sourceAddress,
                                 byte[] destinationHardwareAddress,
                                 Ip6Address destinationAddress,
                                 boolean router, boolean solicited, boolean override,
                                 Ip6Address targetAddress,
                                 List<Option> options) {
        super(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, ICMP_TYPE, options);
        this.router = router;
        this.solicited = solicited;
        this.override = override;
        this.targetAddress = targetAddress;
    }

    public boolean isRouter() {
        return router;
    }

    public boolean isSolicited() {
        return solicited;
    }

    public boolean isOverride() {
        return override;
    }

    public Ip6Address getTargetAddress() {
        return targetAddress;
    }

    @Override
    protected void appendParameter(StringBuilder sb) {
        sb.append("/");
        sb.append(router ? 1 : 0);
        sb.append("/");
        sb.append(solicited ? 1 : 0);
        sb.append("/");
        sb.append(override ? 1 : 0);
        sb.append("/");
        sb.append(DatatypeConverter.printHexBinary(targetAddress.getAddress()).toLowerCase());
    }
}
