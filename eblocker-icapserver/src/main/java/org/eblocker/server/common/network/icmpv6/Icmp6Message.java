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

public class Icmp6Message {
    private final byte[] sourceHardwareAddress;
    private final Ip6Address sourceAddress;
    private final byte[] destinationHardwareAddress;
    private final Ip6Address destinationAddress;
    private final int icmpType;

    public Icmp6Message(byte[] sourceHardwareAddress, Ip6Address sourceAddress, byte[] destinationHardwareAddress,
                        Ip6Address destinationAddress, int icmpType) {
        this.sourceHardwareAddress = sourceHardwareAddress;
        this.sourceAddress = sourceAddress;
        this.destinationHardwareAddress = destinationHardwareAddress;
        this.destinationAddress = destinationAddress;
        this.icmpType = icmpType;
    }

    public byte[] getSourceHardwareAddress() {
        return sourceHardwareAddress;
    }

    public Ip6Address getSourceAddress() {
        return sourceAddress;
    }

    public byte[] getDestinationHardwareAddress() {
        return destinationHardwareAddress;
    }

    public Ip6Address getDestinationAddress() {
        return destinationAddress;
    }

    public int getIcmpType() {
        return icmpType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    protected void toString(StringBuilder sb) {
        sb.append(DatatypeConverter.printHexBinary(sourceHardwareAddress).toLowerCase());
        sb.append("/");
        sb.append(DatatypeConverter.printHexBinary(sourceAddress.getAddress()).toLowerCase());
        sb.append("/");
        sb.append(DatatypeConverter.printHexBinary(destinationHardwareAddress).toLowerCase());
        sb.append("/");
        sb.append(DatatypeConverter.printHexBinary(destinationAddress.getAddress()).toLowerCase());
        sb.append("/icmp6/");
        sb.append(icmpType);
    }
}
