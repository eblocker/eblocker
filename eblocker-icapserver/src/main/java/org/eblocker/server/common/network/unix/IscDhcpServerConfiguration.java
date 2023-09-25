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
package org.eblocker.server.common.network.unix;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.DhcpServerConfiguration;
import org.eblocker.server.common.network.NetworkUtils;
import org.eblocker.server.common.util.Ip4Utils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes configuration files for the DHCP server (service isc-dhcp-server)
 */
public class IscDhcpServerConfiguration {
    // Lease time is in seconds
    public static final int DEFAULT_LEASE_TIME = 600;
    public static final int DEFAULT_MAX_LEASE_TIME = 7200;

    public static String format(DhcpServerConfiguration c) {
        StringBuilder writer = new StringBuilder();
        writer.append("ddns-update-style none;\n");
        // Lease-time: Default time, given to devices when they ask
        // Take (user-supplied) value from configuration unless too short (i.e. shorter than default lease time)
        writer.append("default-lease-time ")
                .append(c.getLeaseTime() < DEFAULT_LEASE_TIME ? DEFAULT_LEASE_TIME : c.getLeaseTime()).append(";\n");
        // Lease-time: Maximum time, granted to devices asking for a timeframe
        writer.append("max-lease-time ")
                .append(c.getLeaseTime() < DEFAULT_MAX_LEASE_TIME ? DEFAULT_MAX_LEASE_TIME : c.getLeaseTime())
                .append(";\n\n");
        // Make the server authoritative for the network segments that
        // are configured, and tell it to send DHCPNAKs to bogus requests
        writer.append("authoritative;\n");
        // Allow each client to have exactly one lease, and expire
        // old leases if a new DHCPDISCOVER occurs
        writer.append("one-lease-per-client true;\n");
        // Log to the local0 facility by default
        writer.append("log-facility local7;\n");
        // Ping the IP address that is being offered to make sure it isn't
        // configured on another node. This has some potential repercussions
        // for clients that don't like delays.
        writer.append("ping-check true;\n");

        writer.append("subnet ");
        writer.append(NetworkUtils.getIPv4NetworkAddress(c.getIpAddress(), c.getNetmask()));
        writer.append(" netmask ");
        writer.append(c.getNetmask());
        writer.append(" {\n");
        if (c.getRange() == null) {
            throw new EblockerException("Invalid ISC DHCP server configuration. Range is missing.");
        }
        // Here, write down several ranges
        for (DhcpRange range : c.getRanges()) {
            writer.append("  range ").append(range.getFirstIpAddress()).append(" ").append(range.getLastIpAddress())
                    .append(";\n");
        }

        writer.append("  option routers ");
        writer.append(c.getIpAddress());
        writer.append(";\n");
        writer.append("  option domain-name-servers ");
        if (c.getNameServerPrimary() != null) {
            writer.append(c.getNameServerPrimary());
        } else {
            writer.append(c.getGateway());
        }
        if (c.getNameServerSecondary() != null) {
            writer.append(", ");
            writer.append(c.getNameServerSecondary());
        }
        writer.append(";\n}\n\n");

        /*
         * Write excluded and static devices
         */
        if (c.getDevices() != null) {

            // Excluded devices
            StringBuilder writerExcluded = new StringBuilder();
            writerExcluded.append("group {\n");
            writerExcluded.append("  option routers ");
            writerExcluded.append(c.getGateway());
            writerExcluded.append(";\n");
            boolean excludedDevicesPresent = false;
            for (Device device : c.getDevices()) {
                List<String> localIpAddresses = getLocalIpv4Addresses(device, NetworkUtils.getIPv4NetworkAddress(c.getIpAddress(), c.getNetmask()), c.getNetmask());

                if (!device.isEnabled()) {
                    writerExcluded.append("  host ");
                    writerExcluded.append(device.getHardwareAddress(false));
                    writerExcluded.append(" { hardware ethernet ");
                    writerExcluded.append(device.getHardwareAddress(true));
                    if (localIpAddresses.size() == 1
                            && device.isIpAddressFixed()
                            && !localIpAddresses.get(0).equals(c.getIpAddress())
                            && !device.isGateway()) {
                        writerExcluded.append("; fixed-address ").append(localIpAddresses.get(0));
                    }
                    writerExcluded.append("; }\n");
                    excludedDevicesPresent = true;
                }
            }
            writerExcluded.append("}\n");
            // Only add group if any devices are in it
            if (excludedDevicesPresent) {
                writer.append(writerExcluded.toString());
            }

            // Static devices
            for (Device device : c.getDevices()) {
                List<String> localIpAddresses = getLocalIpv4Addresses(device, NetworkUtils.getIPv4NetworkAddress(c.getIpAddress(), c.getNetmask()), c.getNetmask());

                if (localIpAddresses.size() == 1
                        && device.isIpAddressFixed()
                        && !localIpAddresses.get(0).equals(c.getIpAddress())
                        && !device.isGateway()
                        && device.isEnabled()) {
                    writer.append("host ").append(device.getHardwareAddress(false)).append(" {\n")
                            .append("  hardware ethernet ").append(device.getHardwareAddress(true)).append(";\n")
                            .append("  fixed-address ").append(localIpAddresses.get(0)).append(";\n")
                            .append("}\n");
                }
            }
        }
        return writer.toString();
    }

    private static List<String> getLocalIpv4Addresses(Device device, String subnet, String netmask) {
        return device.getIpAddresses().stream()
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .filter(ip -> Ip4Utils.isInSubnet(ip, subnet, netmask))
                .collect(Collectors.toList());
    }
}
