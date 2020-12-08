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

public class IpUtils {
    private static final String IPv4ADDRESS_PATTERN =
        "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    private static final String IPRANGE_PATTERN =
        "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\/([0-9]|[12][0-9]|3[0-2])$";
    private static Pattern ipv4addresspattern = Pattern.compile(IPv4ADDRESS_PATTERN);
    private static Pattern rangepattern = Pattern.compile(IPRANGE_PATTERN);
    private static final int IP_RANGE_POSITION_ADDRESS = 0;
    private static final int IP_RANGE_POSITION_RANGE = 1;

    public static Boolean isIPAddress(String ipaddress) {
        return ipv4addresspattern.matcher(ipaddress).matches();
    }

    public static Boolean isIpRange(String iprange) {
        return rangepattern.matcher(iprange).matches();
    }

    public static String shrinkIpRange(String iprange, int size) {
        String[] segments = iprange.split("/");
        int range = Integer.parseInt(segments[IP_RANGE_POSITION_RANGE]);
        if (range < size) {
            // range is too big, restrict to /size
            range = size;
            return segments[IP_RANGE_POSITION_ADDRESS] + "/" + range;
        }
        return iprange;
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
        return isInSubnet(IpUtils.convertIpStringToInt(ip), IpUtils.convertIpStringToInt(subnet), IpUtils.convertIpStringToInt(netmask));
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
