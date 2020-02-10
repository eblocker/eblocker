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
package org.eblocker.server.common.data;

import java.net.InetAddress;
import java.util.Arrays;

public abstract class IpAddress {

    protected final byte[] address;
    private final int hashCode;
    private String string;

    protected IpAddress(byte[] address) {
        this.address = address;
        this.hashCode = Arrays.hashCode(address);
    }

    public static IpAddress of(byte[] bytes) {
        if (bytes.length == 4) {
            return Ip4Address.of(bytes);
        } else if (bytes.length == 16) {
            return Ip6Address.of(bytes);
        }
        throw new IllegalArgumentException("invalid ip address length: " + bytes.length);
    }

    public static IpAddress of(InetAddress inetAddress) {
        return of(inetAddress.getAddress());
    }

    public static IpAddress parse(String ip) {
        if (ip.contains(".")) {
            return Ip4Address.parse(ip);
        } else if (ip.contains(":")) {
            return Ip6Address.parse(ip);
        }
        throw new IllegalArgumentException("unknown ip address format: " + ip);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IpAddress)) {
            return false;
        }

        IpAddress that = (IpAddress) obj;
        return hashCode == that.hashCode && Arrays.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        if (string == null) {
            string = generateStringRepresentation();
        }
        return string;
    }

    public byte[] getAddress() {
        return address;
    }

    public abstract boolean isIpv4();
    public abstract boolean isIpv6();

    protected abstract String generateStringRepresentation();
}
