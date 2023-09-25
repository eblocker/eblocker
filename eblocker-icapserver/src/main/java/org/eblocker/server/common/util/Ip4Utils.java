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

import org.eblocker.server.common.exceptions.EblockerException;

import java.util.regex.Pattern;

/**
 * Utility functions for IPv4 addresses.
 */
public class Ip4Utils {
    private static final String IPv4ADDRESS_PATTERN =
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    private static Pattern ipv4addresspattern = Pattern.compile(IPv4ADDRESS_PATTERN);

    /**
     * Returns true if the given string is an IPv4 address.
     * @param ipaddress IP address as string
     * @return true if ipaddress is an IPv4 address
     */
    public static boolean isIPAddress(String ipaddress) {
        return ipv4addresspattern.matcher(ipaddress).matches();
    }

    public static boolean isIpRange(String ipRange) {
        String[] parts = ipRange.split("/");
        if (parts.length != 2) {
            return false;
        }
        if (!isIPAddress(parts[0])) {
            return false;
        }
        try {
            int prefixLength = Integer.parseInt(parts[1]);
             return prefixLength >= 0 && prefixLength <= 32;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String getSubnet(String ipString, String netMaskString) {
        int ip = convertIpStringToInt(ipString);
        int netMask = convertIpStringToInt(netMaskString);
        ip &= netMask;
        return convertIpIntToString(ip) + "/" + convertNetMaskToCidr(netMask);
    }

    public static int convertIpStringToInt(String ip) {
        if (ip == null) {
            throw new EblockerException("IP must not be null");
        }

        String[] octets = ip.split("\\.");
        return Integer.valueOf(octets[0]) << 24 | Integer.valueOf(octets[1]) << 16 | Integer.valueOf(octets[2]) << 8 | Integer.valueOf(octets[3]);
    }

    public static String convertIpIntToString(int ip) {
        return String.format("%d.%d.%d.%d", (ip & 0xff000000) >>> 24, (ip & 0x00ff0000) >> 16, (ip & 0x0000ff00) >> 8, ip & 0x000000ff);
    }

    public static int convertNetMaskToCidr(int netMask) {
        return 32 - Integer.numberOfTrailingZeros(netMask);
    }

    public static int convertCidrToNetMask(int cidr) {
        // need to use long as rhs in int shifts is limited to 5-bits
        // http://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.19
        return (int) ((0xffffffffl >> cidr) ^ 0xffffffffl);
    }

    public static boolean isInSubnet(String ip, String subnet, String netmask) {
        return isInSubnet(Ip4Utils.convertIpStringToInt(ip), Ip4Utils.convertIpStringToInt(subnet), Ip4Utils.convertIpStringToInt(netmask));
    }

    public static boolean isInSubnet(int ip, int subnet, int netmask) {
        return (ip & netmask) == subnet;
    }

    public static int[] convertIpRangeToIpNetmask(String ipRange) {
        String[] components = ipRange.split("/");
        int[] ipNetmask = new int[2];
        ipNetmask[0] = convertIpStringToInt(components[0]);
        ipNetmask[1] = convertCidrToNetMask(Integer.valueOf(components[1]));
        return ipNetmask;
    }

    public static byte[] convertIpToBytes(int ip) {
        return new byte[]{
                (byte) ((ip >> 24) & 0xff),
                (byte) ((ip >> 16) & 0xff),
                (byte) ((ip >> 8) & 0xff),
                (byte) (ip & 0xff)
        };
    }

    public static int convertBytesToIp(byte[] bytes) {
        return bytes[0] << 24 | bytes[1] << 16 & 0xff0000 | bytes[2] << 8 & 0xff00 | bytes[3] & 0xff;
    }
}
