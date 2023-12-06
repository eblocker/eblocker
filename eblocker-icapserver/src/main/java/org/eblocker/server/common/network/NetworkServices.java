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
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkIp6Configuration;

/**
 * Instances of this class should know how to configure, start and stop network
 * services of the OS.
 */
public interface NetworkServices {
    NetworkConfiguration getCurrentNetworkConfiguration();

    void enableArpSpoofer();

    void disableArpSpoofer();

    void enableDhcpServer(boolean start);

    void configureDhcpServer(NetworkConfiguration configuration);

    void disableDhcpServer();

    void enableDhcpClient();

    void enableStaticIp(NetworkConfiguration configuration);

    void configureEblockerDns(NetworkConfiguration configuration);

    void setNameserverAddresses(NetworkConfiguration configuration);

    void updateIp6State();

    /**
     * Write the firewall configuration
     *
     * @param masquerade should SNAT or masquerading be enabled?
     */
    void enableFirewall(boolean masquerade, boolean enableSSL, boolean enableOpenVpnServer, boolean enableMalwareSet);

    void applyNetworkConfiguration(NetworkConfiguration networkConfiguration);

    /**
     * Restore "normal" ARP cache of device
     *
     * @param device
     * @return true if ARP messages were sent
     */
    boolean healDevice(Device device);

    /**
     * Retrieve the OS's current gateway and store it
     */
    void updateGateway();

    /**
     * Retrieve DHCP-status
     *
     * @return true if the eBlocker is the DHCP server
     */
    boolean getDHCPActive();

    void addListener(NetworkChangeListener listener);

    NetworkIp6Configuration getNetworkIp6Configuration();

    void updateNetworkIp6Configuration(NetworkIp6Configuration networkIp6Configuration);
}
