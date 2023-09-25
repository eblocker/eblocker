/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

/**
 * Utility functions that can deal with both IPv4 and IPv6 addresses.
 */
public class IpUtils {
    private static final int IP4_RANGE_RANGE_THRESHOLD = 8;
    private static final int IP6_RANGE_RANGE_THRESHOLD = 32;

    /**
     * Shrinks an IP range to a reasonable limit.
     * An IPv4 prefix must be at least 8 bits while
     * an IPv6 prefix must be at least 32 bits.
     * @param ipRange IP range in CIDR notation, e.g. 17.0.0.0/8 or 2603:1000::/25
     * @return resulting IP range in CIDR notation
     */
    public static String shrinkIpRange(String ipRange) {
        String[] parts = ipRange.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Not an IP range: " + ipRange);
        }
        int ipVersion = 0;
        if (Ip4Utils.isIPAddress(parts[0])) {
            ipVersion = 4;
        } else if (Ip6Utils.isIp6Address(parts[0])) {
            ipVersion = 6;
        } else {
            throw new IllegalArgumentException("Not an IP range: " + ipRange);
        }
        try {
            int prefixLength = Integer.parseInt(parts[1]);
            boolean shrunk = false;
            if (ipVersion == 4 && prefixLength < IP4_RANGE_RANGE_THRESHOLD) {
                prefixLength = IP4_RANGE_RANGE_THRESHOLD;
                shrunk = true;
            } else if (ipVersion == 6 && prefixLength < IP6_RANGE_RANGE_THRESHOLD) {
                prefixLength = IP6_RANGE_RANGE_THRESHOLD;
                shrunk = true;
            }
            if (shrunk) {
                return parts[0] + "/" + prefixLength;
            } else {
                return ipRange;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an IP range: " + ipRange);
        }
    }

    /**
     * Returns true if a string is an IPv4 or IPv6 range in CIDR notation.
     */
    public static boolean isIpRange(String ipRange) {
        return Ip4Utils.isIpRange(ipRange) || Ip6Utils.isIp6Range(ipRange);
    }

    /**
     * Returns true if a string is an IPv4 or IPv6 address.
     */
    public static boolean isIPAddress(String address) {
        return Ip4Utils.isIPAddress(address) || Ip6Utils.isIp6Address(address);
    }
}
