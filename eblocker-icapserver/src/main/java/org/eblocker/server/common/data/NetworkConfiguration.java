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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.strategicgains.syntaxe.annotation.ObjectValidation;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.eblocker.server.common.data.validation.NetworkConfigurationValidator;
import org.eblocker.server.common.network.unix.IscDhcpServerConfiguration;

/**
 * Defines how the eBlocker is integrated into the home network
 */
@ObjectValidation(NetworkConfigurationValidator.class)
public class NetworkConfiguration {
    private boolean automatic; // if true, ARP spoofing is performed
    private boolean isExpertMode;
    private boolean dhcp; // if true, eBlocker runs a DHCP server
    private boolean dnsServer; // if true eblocker uses it's own dns server
    private boolean globalIp6AddressAvailable; // if true eBlocker has a global IPv6 address
    private String ipAddress; // static IP address of eBlocker
    private String networkMask; // network mask of static IP address
    private String vpnIpAddress;
    private String gateway; // IP address of gateway
    private String nameServerPrimary; // IP address of primary name server
    private String nameServerSecondary; // IP address of secondary name server
    private String dhcpRangeFirst; // first IP address to be assigned by DHCP server
    private String dhcpRangeLast; // last IP address to be assigned by DHCP server
    private boolean ipFixedByDefault; // treat IPs used by devices as static by default or not
    private boolean rebootNecessary; // is set to true by the server, when the system needs to be rebooted
    private String advisedNameServer; // Advised name server, actually the nameserver the clients have to use.
    private int dhcpLeaseTime = IscDhcpServerConfiguration.DEFAULT_LEASE_TIME;

    @Override
    public int hashCode() {
        return new HashCodeBuilder(199, 67)
                .appendSuper(super.hashCode())
                .append(isAutomatic())
                .append(isExpertMode())
                .append(isDhcp())
                .append(isDnsServer())
                .append(isGlobalIp6AddressAvailable())
                .append(getIpAddress())
                .append(getNetworkMask())
                .append(getGateway())
                .append(getNameServerPrimary())
                .append(getNameServerSecondary())
                .append(getDhcpRangeFirst())
                .append(getDhcpRangeLast())
                .append(isIpFixedByDefault())
                .append(isRebootNecessary())
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NetworkConfiguration)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        NetworkConfiguration rhs = (NetworkConfiguration) obj;
        return new EqualsBuilder()
                .append(isAutomatic(), rhs.isAutomatic())
                .append(isExpertMode(), rhs.isExpertMode())
                .append(isDhcp(), rhs.isDhcp())
                .append(isDnsServer(), rhs.isDnsServer())
                .append(isGlobalIp6AddressAvailable(), rhs.isGlobalIp6AddressAvailable())
                .append(getIpAddress(), rhs.getIpAddress())
                .append(getNetworkMask(), rhs.getNetworkMask())
                .append(vpnIpAddress, rhs.getVpnIpAddress())
                .append(getGateway(), rhs.getGateway())
                .append(getNameServerPrimary(), rhs.getNameServerPrimary())
                .append(getNameServerSecondary(), rhs.getNameServerSecondary())
                .append(getDhcpRangeFirst(), rhs.getDhcpRangeFirst())
                .append(getDhcpRangeLast(), rhs.getDhcpRangeLast())
                .append(isIpFixedByDefault(), rhs.isIpFixedByDefault())
                .append(isRebootNecessary(), rhs.isRebootNecessary())
                .isEquals();
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("NetworkConfiguration{");
        result.append("automatic: ");
        result.append(automatic);
        result.append("expertMode: ");
        result.append(isExpertMode);
        result.append(", ipAddress: ");
        result.append(ipAddress);
        result.append(" (");
        result.append(networkMask);
        result.append("), gateway: ");
        result.append(gateway);
        result.append("), dns server: ");
        result.append(dnsServer);
        result.append(", name server: ");
        result.append(nameServerPrimary);
        if (nameServerSecondary != null) {
            result.append(", ");
            result.append(nameServerSecondary);
        }
        result.append(", public IPv6 address available: ");
        result.append(globalIp6AddressAvailable);

        result.append(", dhcp: ");
        result.append(dhcp);
        result.append(", DHCP range: ");
        result.append(dhcpRangeFirst);
        result.append("-");
        result.append(dhcpRangeLast);
        result.append(", dhcpIpFixedByDefault: ");
        result.append(ipFixedByDefault);
        result.append("}");
        return result.toString();
    }

    @JsonProperty
    public boolean isAutomatic() {
        return automatic;
    }

    public void setAutomatic(boolean automatic) {
        this.automatic = automatic;
    }

    @JsonProperty
    public boolean isExpertMode() {
        return isExpertMode;
    }

    public void setExpertMode(boolean isExpertMode) {
        this.isExpertMode = isExpertMode;
    }

    @JsonProperty
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @JsonProperty
    public boolean isDhcp() {
        return dhcp;
    }

    public void setDhcp(boolean dhcp) {
        this.dhcp = dhcp;
    }

    @JsonProperty
    public boolean isDnsServer() {
        return dnsServer;
    }

    public void setDnsServer(boolean dnsServer) {
        this.dnsServer = dnsServer;
    }

    @JsonProperty
    public String getNetworkMask() {
        return networkMask;
    }

    public void setNetworkMask(String networkMask) {
        this.networkMask = networkMask;
    }

    public String getVpnIpAddress() {
        return vpnIpAddress;
    }

    public void setVpnIpAddress(String vpnIpAddress) {
        this.vpnIpAddress = vpnIpAddress;
    }

    @JsonProperty
    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    @JsonProperty
    public String getNameServerPrimary() {
        return nameServerPrimary;
    }

    public void setNameServerPrimary(String nameServerPrimary) {
        this.nameServerPrimary = nameServerPrimary;
    }

    @JsonProperty
    public String getNameServerSecondary() {
        return nameServerSecondary;
    }

    public void setNameServerSecondary(String nameServerSecondary) {
        this.nameServerSecondary = nameServerSecondary;
    }

    @JsonProperty
    public String getDhcpRangeFirst() {
        return dhcpRangeFirst;
    }

    public void setDhcpRangeFirst(String dhcpRangeFirst) {
        this.dhcpRangeFirst = dhcpRangeFirst;
    }

    @JsonProperty
    public String getDhcpRangeLast() {
        return dhcpRangeLast;
    }

    public void setDhcpRangeLast(String dhcpRangeLast) {
        this.dhcpRangeLast = dhcpRangeLast;
    }

    @JsonProperty
    public boolean isIpFixedByDefault() {
        return ipFixedByDefault;
    }

    public void setIpFixedByDefault(boolean ipFixedByDefault) {
        this.ipFixedByDefault = ipFixedByDefault;
    }

    public boolean isRebootNecessary() {
        return rebootNecessary;
    }

    public void setRebootNecessary(boolean rebootNecessary) {
        this.rebootNecessary = rebootNecessary;
    }

    @JsonProperty
    public String getAdvisedNameServer() {
        return advisedNameServer;
    }

    public void setAdvisedNameServer(String nameServer) {
        this.advisedNameServer = nameServer;
    }

    @JsonProperty
    public int getDhcpLeaseTime() {
        return dhcpLeaseTime;
    }

    public void setDhcpLeaseTime(int leaseTime) {
        this.dhcpLeaseTime = leaseTime;
    }

    @JsonProperty
    public boolean isGlobalIp6AddressAvailable() {
        return globalIp6AddressAvailable;
    }

    public void setGlobalIp6AddressAvailable(boolean globalIp6AddressAvailable) {
        this.globalIp6AddressAvailable = globalIp6AddressAvailable;
    }
}
