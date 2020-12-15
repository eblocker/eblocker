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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkStateId;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base class for network services that are platform independent.
 */
public abstract class NetworkServicesBase implements NetworkServices {
    private static final String IP_ADDRESS_LOCALHOST = "127.0.0.1";
    private static final Logger log = LoggerFactory.getLogger(NetworkServicesBase.class);

    private final DataSource dataSource;
    private final ScheduledExecutorService executorService;
    private final ArpSpoofer arpSpoofer;
    private final long arpSpooferStartupDelay;
    private final long arpSpooferFixedDelay;
    private final NetworkInterfaceWrapper networkInterface;
    private ScheduledFuture<?> arpSpooferFuture;
    private final EblockerDnsServer eblockerDnsServer;

    private List<NetworkChangeListener> listeners = new ArrayList<>();

    public NetworkServicesBase(DataSource dataSource,
                               ScheduledExecutorService executorService,
                               NetworkInterfaceWrapper networkInterface,
                               ArpSpoofer arpSpoofer,
                               long arpSpooferStartupDelay,
                               long arpSpooferFixedDelay,
                               EblockerDnsServer eblockerDnsServer) {
        this.dataSource = dataSource;
        this.executorService = executorService;
        this.networkInterface = networkInterface;
        this.arpSpoofer = arpSpoofer;
        this.arpSpooferStartupDelay = arpSpooferStartupDelay;
        this.arpSpooferFixedDelay = arpSpooferFixedDelay;
        this.eblockerDnsServer = eblockerDnsServer;
    }

    protected Set<Device> getDevices() {
        return dataSource.getDevices();
    }

    @Override
    public NetworkConfiguration getCurrentNetworkConfiguration() {
        NetworkConfiguration config = new NetworkConfiguration();

        IpAddress ipAddress = networkInterface.getFirstIPv4Address();
        config.setIpAddress(ipAddress.toString());
        int prefixLength = networkInterface.getNetworkPrefixLength(ipAddress);
        if (prefixLength != -1) {//networkInterface was found
            config.setNetworkMask(NetworkUtils.getIPv4NetworkMask(prefixLength));
        } else {//no networkinterface found, set local link network mask (255.255.0.0)=16 Bit prefixLength => default
            config.setNetworkMask(NetworkUtils.getIPv4NetworkMask(16));
        }
        String vpnIpAddressString = networkInterface.getVpnIpv4Address() != null ? networkInterface.getVpnIpv4Address().toString() : null;
        config.setVpnIpAddress(vpnIpAddressString);

        String gateway = dataSource.getGateway();
        config.setGateway(gateway);

        config.setDnsServer(eblockerDnsServer.isEnabled());

        // to avoid having 127.0.0.1 / eblocker-ip showing up in manual configuration mode when eblocker-dns
        // has been disabled this structure always contains the first two actual configured upstream servers in
        // eblocker-dns mode
        List<String> nameserverAddresses = getNameserverAddresses();
        if (!nameserverAddresses.isEmpty()) {
            config.setNameServerPrimary(nameserverAddresses.get(0));
        }
        if (nameserverAddresses.size() >= 2) {
            config.setNameServerSecondary(nameserverAddresses.get(1));
        }

        // fallback: use router (which probably does DNS-relay) as nameserver:
        if (nameserverAddresses.isEmpty()) {
            config.setNameServerPrimary(gateway);
        }

        setFlagsAccordingToCurrentState(config);

        DhcpRange range = dataSource.getDhcpRange();
        if (range.isEmpty()) {
            config.setDhcpRangeFirst(NetworkUtils.replaceLastByte(ipAddress.toString(), (byte) 20));
            config.setDhcpRangeLast(NetworkUtils.replaceLastByte(ipAddress.toString(), (byte) 200));
        } else {
            config.setDhcpRangeFirst(range.getFirstIpAddress());
            config.setDhcpRangeLast(range.getLastIpAddress());
        }
        config.setIpFixedByDefault(dataSource.isIpFixedByDefault());
        config.setExpertMode(dataSource.isExpertMode());
        config.setAdvisedNameServer(getAdvisedNameServer());
        if (dataSource.getDhcpLeaseTime() != null) {
            config.setDhcpLeaseTime(dataSource.getDhcpLeaseTime());
        }
        return config;
    }

    @Override
    public void applyNetworkConfiguration(NetworkConfiguration configuration) {
        if (configuration.isDhcp() && !configuration.isAutomatic()) {
            if (configuration.isExpertMode()) {
                dataSource.setDhcpLeaseTime(configuration.getDhcpLeaseTime());
            }
            DhcpRange range = new DhcpRange(configuration.getDhcpRangeFirst(), configuration.getDhcpRangeLast());
            dataSource.setIpFixedByDefault(configuration.isIpFixedByDefault());
            dataSource.setDhcpRange(range);
        } else {
            dataSource.clearDhcpRange();
        }
        if (!configuration.isExpertMode()) {
            dataSource.setDhcpLeaseTime(null);
        }
        dataSource.setGateway(configuration.getGateway());
    }

    private List<String> getNameserverAddresses() {
        if (eblockerDnsServer.isEnabled()) {
            if (NetworkStateId.PLUG_AND_PLAY == dataSource.getCurrentNetworkState()) {
                return eblockerDnsServer.getDhcpNameServers();
            } else {
                return eblockerDnsServer.getCustomNameServers();
            }
        }

        return getNativeNameServerAddresses();
    }

    protected abstract List<String> getNativeNameServerAddresses();

    private void setFlagsAccordingToCurrentState(NetworkConfiguration config) {
        NetworkStateId state = dataSource.getCurrentNetworkState();
        config.setAutomatic(state == NetworkStateId.PLUG_AND_PLAY);
        config.setDhcp(state == NetworkStateId.LOCAL_DHCP);
    }

    @Override
    public void enableArpSpoofer() {
        if (arpSpooferFuture != null && !arpSpooferFuture.isCancelled()) {
            log.warn("ArpSpoofer is already running");
            return;
        }

        log.debug("Starting ArpSpoofer: " + (arpSpoofer != null) + " startupDelay: " + arpSpooferStartupDelay + " fixedDelay: " + arpSpooferFixedDelay);
        arpSpooferFuture = executorService.scheduleWithFixedDelay(arpSpoofer, arpSpooferStartupDelay, arpSpooferFixedDelay, TimeUnit.SECONDS);
    }

    @Override
    public void disableArpSpoofer() {
        if (arpSpooferFuture == null) {
            log.info("ArpSpoofer is not running");
            return;
        }

        log.info("Stopping ArpSpoofer");
        arpSpooferFuture.cancel(false);
        arpSpooferFuture = null;
    }

    protected abstract void enableFirewall(Set<Device> allDevices, Collection<OpenVpnClientState> vpnClients, boolean masquerade, boolean enableSSL, boolean enableEblockerDns, boolean enableEblockerMobile, boolean enableMalwareSet);

    @Override
    public void enableFirewall(boolean masquerade, boolean enableSSL, boolean enableEblockerMobile, boolean enableMalwareSet) {
        Set<Device> allDevices = dataSource.getDevices();
        Collection<OpenVpnClientState> vpnClients = dataSource.getAll(OpenVpnClientState.class);
        enableFirewall(allDevices, vpnClients, masquerade, enableSSL, eblockerDnsServer.isEnabled(), enableEblockerMobile, enableMalwareSet);
    }

    public boolean healDevice(Device device) {
        log.info("Healing ARP cache of just disabled device {}", device.getHardwareAddress());
        return arpSpoofer.heal(device);//if in auto mode and device was just disabled, heal the ARP cache of the device
    }

    @Override
    public void updateGateway() {
        networkInterface.findGatewayAndWriteToRedis();
    }

    @Override
    public boolean getDHCPActive() {
        return this.dataSource.getCurrentNetworkState() == NetworkStateId.LOCAL_DHCP;
    }

    @Override
    public void configureEblockerDns(NetworkConfiguration configuration) {
        if (configuration.isDnsServer()) {
            eblockerDnsServer.enable(configuration);

            // write dns config
            NetworkConfiguration systemDnsConfiguration = new NetworkConfiguration();
            systemDnsConfiguration.setNameServerPrimary(IP_ADDRESS_LOCALHOST);
            setNameserverAddresses(systemDnsConfiguration);
        } else {
            eblockerDnsServer.disable();
            setNameserverAddresses(configuration);
        }
    }

    @Override
    public void addListener(NetworkChangeListener listener) {
        listeners.add(listener);
    }

    protected void notifyListeners(Consumer<NetworkChangeListener> eventConsumer) {
        listeners.forEach(eventConsumer);
    }

    private String getAdvisedNameServer() {
        if (eblockerDnsServer.isEnabled()) {
            return networkInterface.getFirstIPv4Address().toString();
        }

        return getNativeNameServerAddresses().get(0);
    }
}
