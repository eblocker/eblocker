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
package org.eblocker.server.common.util;

import org.eblocker.server.common.data.Ip6Address;

public class Ip6Utils {

    public static Ip6Address combine(Ip6Address networkAddress, Ip6Address hostAddress) {
        byte[] address = new byte[16];
        for (int i = 0; i < 16; ++i) {
            address[i] = (byte) (networkAddress.getAddress()[i] | hostAddress.getAddress()[i]);
        }
        return Ip6Address.of(address);
    }

    public static boolean isInNetwork(Ip6Address hostAddress, Ip6Address networkAddress, int prefixLength) {
        int bytes = prefixLength / 8;
        for (int i = 0; i < bytes; ++i) {
            if (hostAddress.getAddress()[i] != networkAddress.getAddress()[i]) {
                return false;
            }
        }

        int bits = prefixLength % 8;
        if (bits == 0) {
            return true;
        }

        int mask = 0xff00 >> bits;
        int hostMasked = hostAddress.getAddress()[bytes] & mask;
        int netMasked = networkAddress.getAddress()[bytes] & mask; // not really necessary if network is specified correctly ...
        return hostMasked == netMasked;
    }

    public static Ip6Address getNetworkAddress(Ip6Address address, int prefixLength) {
        byte[] networkAddress = new byte[16];
        int bytes = prefixLength / 8;
        System.arraycopy(address.getAddress(), 0, networkAddress, 0, bytes);

        int bits = prefixLength % 8;
        if (bits != 0) {
            int mask = 0xff00 >> bits;
            networkAddress[bytes] = (byte) (address.getAddress()[bytes] & mask);
        }

        return Ip6Address.of(networkAddress);
    }

    public static Ip6Address getHostAddress(Ip6Address address, int prefixLength) {
        byte[] hostAddress = new byte[16];
        int bytes = prefixLength / 8;
        System.arraycopy(address.getAddress(), bytes, hostAddress, bytes, 16 - bytes);

        int bits = prefixLength % 8;
        if (bits != 0) {
            int mask = 0x00ff >> bits;
            hostAddress[bytes] = (byte) (address.getAddress()[bytes] & mask);
        }

        return Ip6Address.of(hostAddress);
    }

    public static boolean isLinkLocal(Ip6Address address) {
        return isInNetwork(address, Ip6Address.LINK_LOCAL_NETWORK_ADDRESS, Ip6Address.LINK_LOCAL_NETWORK_PREFIX);
    }
}
