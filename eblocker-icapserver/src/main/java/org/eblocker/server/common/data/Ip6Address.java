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

public class Ip6Address extends IpAddress {

    public static final Ip6Address UNSPECIFIED_ADDRESS = new Ip6Address(new byte[16]);
    public static final Ip6Address MULTICAST_ALL_NODES_ADDRESS = new Ip6Address(new byte[]{ (byte) 0xff, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
    public static final Ip6Address LINK_LOCAL_NETWORK_ADDRESS = new Ip6Address(new byte[]{ (byte) 0xfe, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
    public static final int LINK_LOCAL_NETWORK_PREFIX = 10;

    private Ip6Address(byte[] address) {
        super(address);
    }

    public static Ip6Address of(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("expected 16 bytes but got " + bytes.length);
        }
        return new Ip6Address(bytes);
    }

    public static Ip6Address parse(String ip) {
        String[] parts = ip.split("::");
        if (parts.length > 2) {
            throw new IllegalArgumentException("invalid ipv6 address: " + ip);
        }

        byte[] address = new byte[16];
        if (parts.length == 0) {
            return new Ip6Address(address);
        }

        int index = 0;
        if (!parts[0].isEmpty()) {
            String[] fields = parts[0].split(":");
            if (fields.length > 8) {
                throw new IllegalArgumentException("invalid ipv6 address: " + ip);
            }

            for (int i = 0; i < fields.length; ++i) {
                if (fields[i].length() > 4) {
                    throw new IllegalArgumentException("invalid ipv6 address: " + ip);
                }
                int value = Integer.parseInt(fields[i], 16);
                address[index++] = (byte) (value >> 8 & 0xff);
                address[index++] = (byte) (value & 0xff);
            }
            if (index == 16) {
                return new Ip6Address(address);
            }
        }

        if (parts.length == 2) {
            String[] fields = parts[1].split(":");
            if (index + fields.length * 2 >= 16) {
                throw new IllegalArgumentException("invalid ipv6 address: " + ip);
            }

            index = 15;
            for (int i = fields.length - 1; i >= 0; --i) {
                int value = Integer.parseInt(fields[i], 16);
                address[index--] = (byte) (value & 0xff);
                address[index--] = (byte) (value >> 8 & 0xff);
            }
        }

        return new Ip6Address(address);
    }

    @Override
    public boolean isIpv4() {
        return false;
    }

    @Override
    public boolean isIpv6() {
        return true;
    }

    @Override
    protected String generateStringRepresentation() {
        // recommended canonical ipv6 address representation (rfc-5952)
        // * no leading zeros
        // * collapse longest run of zeros (first one if multiple of same length exists)
        // * lower case
        int maxZeroCount = 0;
        int maxZeroStart = 0;

        int zeroCount = 0;
        int zeroStart = 0;

        int[] fields = new int[8];
        for (int i = 0; i < 8; ++i) {
            fields[i] = address[i * 2] << 8 & 0xffff | address[i * 2 + 1] & 0xff;
            if (fields[i] == 0) {
                if (zeroCount == 0) {
                    zeroStart = i;
                }
                ++zeroCount;
            } else if (zeroCount > 0) {
                if (zeroCount > maxZeroCount) {
                    maxZeroCount = zeroCount;
                    maxZeroStart = zeroStart;
                }
                zeroCount = 0;
            }
        }
        if (zeroCount == 8) {
            return "::";
        } else if (zeroCount > maxZeroCount) {
            maxZeroCount = zeroCount;
            maxZeroStart = zeroStart;
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < 8) {
            if (i > 0) {
                sb.append(":");
            }

            if (maxZeroCount > 1 && maxZeroStart == i) {
                i += maxZeroCount;
                if (maxZeroStart == 0 || i == 8) {
                    sb.append(":");
                }
            } else {
                sb.append(String.format("%x", fields[i]));
                ++i;
            }
        }

        return sb.toString();
    }
}
