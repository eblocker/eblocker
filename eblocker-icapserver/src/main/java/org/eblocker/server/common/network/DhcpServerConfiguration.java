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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.unix.IscDhcpServerConfiguration;
import org.eblocker.server.common.util.Ip4Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Contains parameters needed for configuring a DHCP server
 */
public class DhcpServerConfiguration {
    private String ipAddress;
    private String netmask;
    private String gateway;
    private String nameServerPrimary;
    private String nameServerSecondary;
    private DhcpRange range;
    private Set<Device> devices;
    private int leaseTime = IscDhcpServerConfiguration.DEFAULT_LEASE_TIME;

    private List<DhcpRange> ranges = new ArrayList<>();

    /**
     * The machine's IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Netmask of the IP address
     */
    public String getNetmask() {
        return netmask;
    }

    /**
     * The gateway's IP address
     */
    public String getGateway() {
        return gateway;
    }

    /**
     * The primary name server. This field is optional. If it is not set,
     * the gateway's address is used.
     */
    public String getNameServerPrimary() {
        return nameServerPrimary;
    }

    /**
     * The secondary name server. This field is optional.
     */
    public String getNameServerSecondary() {
        return nameServerSecondary;
    }

    /**
     * The range of IP addresses that the DHCP server should assign to clients.
     * This field is optional.
     */
    public DhcpRange getRange() {
        return range;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public void setRange(DhcpRange range) {
        this.range = range;
    }

    public void setNameServerPrimary(String nameServerPrimary) {
        this.nameServerPrimary = nameServerPrimary;
    }

    public void setNameServerSecondary(String nameServerSecondary) {
        this.nameServerSecondary = nameServerSecondary;
    }

    public Set<Device> getDevices() {
        return devices;
    }

    public void setDevices(Set<Device> devices) {
        this.devices = devices;

        if (range != null) {
            // Here, create the gaps

            TreeSet<Integer> numIps = new TreeSet<>();
            // Sorted list of all IPs of static devices
            for (Device device : this.devices) {
                if (device.isIpAddressFixed()) {
                    device.getIpAddresses().stream()
                            .filter(IpAddress::isIpv4)
                            .map(IpAddress::toString)
                            .map(Ip4Utils::convertIpStringToInt)
                            .forEach(numIps::add);
                }
            }
            // Also add eBlocker's IP to make sure it is not assigned to any device
            numIps.add(Ip4Utils.convertIpStringToInt(ipAddress));

            // Go through range, make gaps
            int lastFixedIp = Ip4Utils.convertIpStringToInt(range.getFirstIpAddress()) - 1;
            int rangeFirstIpNumerical = Ip4Utils.convertIpStringToInt(range.getFirstIpAddress());
            int rangeLastIpNumerical = Ip4Utils.convertIpStringToInt(range.getLastIpAddress());

            for (Integer numIp : numIps) {
                if (numIp >= rangeFirstIpNumerical && numIp <= rangeLastIpNumerical) {
                    int curFixedIp = numIp;
                    // If there is an unused IP in the gap
                    if (curFixedIp - lastFixedIp > 1) {
                        addRange(new DhcpRange(Ip4Utils.convertIpIntToString(lastFixedIp + 1), Ip4Utils.convertIpIntToString(curFixedIp - 1)));
                    }
                    lastFixedIp = curFixedIp;
                }
            }
            // Last range
            if (rangeLastIpNumerical - lastFixedIp >= 1) {
                addRange(new DhcpRange(Ip4Utils.convertIpIntToString(lastFixedIp + 1), range.getLastIpAddress()));
            }

        }
    }

    public void addRange(DhcpRange range) {
        ranges.add(range);
    }

    public List<DhcpRange> getRanges() {
        return ranges;
    }

    public void setLeaseTime(int leaseTime) {
        this.leaseTime = leaseTime;
    }

    public int getLeaseTime() {
        return leaseTime;
    }
}
