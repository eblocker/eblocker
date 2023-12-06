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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkStateId;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.unix.DnsEnableByDefaultChecker;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.network.unix.IpSets;
import org.eblocker.server.common.network.unix.IscDhcpServerConfiguration;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The network state machine processes events (e.g. when a new network configuration is received
 * by the NetworkController) and changes the network state accordingly using the NetworkServices.
 */
@Singleton
@SubSystemService(value = SubSystem.NETWORK_STATE_MACHINE, initPriority = 100)
public class NetworkStateMachine {
    private static final Logger log = LoggerFactory.getLogger(NetworkStateMachine.class);
    private final NetworkServices services;
    private final DataSource dataSource;
    private final DnsEnableByDefaultChecker dnsEnableByDefaultChecker;
    private final EventLogger eventLogger;
    private final IpSets ipSets;
    private final EblockerDnsServer dnsServer;
    private final Ip6PrefixMonitor ip6PrefixMonitor;

    @Inject
    public NetworkStateMachine(NetworkServices services,
                               DataSource dataSource,
                               DnsEnableByDefaultChecker dnsEnableByDefaultChecker,
                               EventLogger eventLogger,
                               IpSets ipSets,
                               SslService sslService,
                               EblockerDnsServer dnsServer,
                               Ip6PrefixMonitor ip6PrefixMonitor) {
        this.services = services;
        this.dataSource = dataSource;
        this.dnsEnableByDefaultChecker = dnsEnableByDefaultChecker;
        this.eventLogger = eventLogger;
        this.ipSets = ipSets;
        this.dnsServer = dnsServer;
        this.ip6PrefixMonitor = ip6PrefixMonitor;

        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                sslStateChanged(sslEnabled);
            }

            @Override
            public void onEnable() {
                sslStateChanged(true);
            }

            @Override
            public void onDisable() {
                sslStateChanged(false);
            }
        });
    }

    /**
     * Enable services that must be started at boot time so that the current state is functional
     */
    @SubSystemInit
    public void initialize() {
        NetworkStateId currentState = dataSource.getCurrentNetworkState();
        services.updateGateway();
        if (currentState == NetworkStateId.PLUG_AND_PLAY) {
            services.enableArpSpoofer();
        }

        dnsEnableByDefaultChecker.check();
        services.updateIp6State();
        services.enableFirewall(shouldMasquerade(currentState), isSSLEnabled(), isOpenVpnServerEnabled(), ipSets.isSupportedByOperatingSystem());

        ip6PrefixMonitor.addPrefixChangeListener(this::updateFirewall);
    }

    public boolean isSSLEnabled() {
        log.debug("SSL state is :{}", dataSource.getSSLEnabledState());
        return dataSource.getSSLEnabledState();
    }

    private boolean isOpenVpnServerEnabled() {
        boolean state = dataSource.getOpenVpnServerState();
        log.debug("eBlocker mobile state is :{}", state);
        return state;
    }

    private void sslStateChanged(boolean sslEnabled) {
        //reconfigure firewall
        NetworkStateId currentState = getCurrentNetworkState().getId();
        services.enableFirewall(shouldMasquerade(currentState), sslEnabled, isOpenVpnServerEnabled(), ipSets.isSupportedByOperatingSystem());

        log.debug("SSL state is now {}", sslEnabled);
    }

    private void updateFirewall() {
        NetworkStateId currentState = getCurrentNetworkState().getId();
        services.enableFirewall(shouldMasquerade(currentState), isSSLEnabled(), isOpenVpnServerEnabled(), ipSets.isSupportedByOperatingSystem());
    }

    // Masquerading is not a good idea when ARP-spoofing is used (see https://trac.bmb-dev.de/trac/wiki/NetworkConfiguration)
    private boolean shouldMasquerade(NetworkStateId currentState) {
        return currentState != NetworkStateId.PLUG_AND_PLAY;
    }

    /**
     * Updates the current configuration
     *
     * @param networkConfiguration
     * @return true, if the network state has changed
     */
    public synchronized boolean updateConfiguration(NetworkConfiguration networkConfiguration) {
        NetworkConfiguration currentNetworkConfiguration = services.getCurrentNetworkConfiguration();
        boolean rebootNeeded = false;

        //To avoid that somebody enters e.g. another static IP for the eBlocker but forgets to reboot (to really apply the change),
        // the DHCP-config is written anyway and contains the new configured static IP for the eBlocker, which is not bound to the networkinterface yet....

        log.info("Current network configuration:  {}", currentNetworkConfiguration);
        log.info("Selected network configuration: {}", networkConfiguration);

        NetworkState current = getCurrentNetworkState();
        NetworkState selected = getSelectedNetworkState(networkConfiguration);

        if (current.getId() == selected.getId()) {
            switch (current.getId()) {
                case PLUG_AND_PLAY:
                    break;//no need to reboot
                case EXTERNAL_DHCP:
                case LOCAL_DHCP:
                    rebootNeeded = evaluateReboot(networkConfiguration, currentNetworkConfiguration);
                    break;
            }
            selected.onConfigurationUpdate(services, networkConfiguration, rebootNeeded);

        } else { //FIXME if only dhcp server is en/disabled reboot MIGHT not be neccessary (still neccessary if the conditions above are true)
            log.info("Network state transition from {} to {}", current.getId(), selected.getId());
            // Log event
            eventLogger.log(Events.networkModeChange(selected.getId()));

            rebootNeeded = true;//networkstate change -> a reboot is probably a good idea
            current.onExit(services);
            selected.onEntry(services, networkConfiguration, rebootNeeded);
            // If DHCP server is enabled on the eBlocker, all devices are given a static flag
            dataSource.setIpAddressesFixed(true);
            // The DHCP configuration must be written down
            services.configureDhcpServer(networkConfiguration);
        }

        if (selected.getId() == NetworkStateId.LOCAL_DHCP) {
            dnsServer.setDnsCustomResolver();
        }

        services.configureEblockerDns(networkConfiguration);

        services.applyNetworkConfiguration(networkConfiguration);
        services.enableFirewall(shouldMasquerade(selected.getId()), isSSLEnabled(), isOpenVpnServerEnabled(), ipSets.isSupportedByOperatingSystem());

        setCurrentNetworkState(selected);

        dataSource.setIsExpertMode(networkConfiguration.isExpertMode());

        log.error("dhcp lease time: {}", networkConfiguration.getDhcpLeaseTime());
        if (networkConfiguration.isExpertMode()) {
            dataSource.setDhcpLeaseTime(networkConfiguration.getDhcpLeaseTime());
        } else {
            dataSource.setDhcpLeaseTime(IscDhcpServerConfiguration.DEFAULT_LEASE_TIME);
        }

        dataSource.createSnapshot();

        return rebootNeeded;
    }

    private boolean evaluateReboot(NetworkConfiguration networkConfiguration, NetworkConfiguration currentNetworkConfiguration) {
        if (!currentNetworkConfiguration.getIpAddress().equals(networkConfiguration.getIpAddress())) {
            log.info("Static IP address of eBlocker has changed! Reboot needed now!");
            return true;//because static IP has changed, so networkInterface has to be restarted
        } else if (!currentNetworkConfiguration.getGateway().equals(networkConfiguration.getGateway())) {
            log.info("Gateway IP address has changed! Reboot needed now!");
            return true;
        } else if (!currentNetworkConfiguration.getNetworkMask().equals(networkConfiguration.getNetworkMask())) {
            log.info("Network mask has changed! Reboot needed now!");
            return true;
        }

        return false;
    }

    /**
     * Call this method when a device is put on / removed from the whitelist.
     */
    public void deviceStateChanged() {
        NetworkStateId currentState = getCurrentNetworkState().getId();
        services.enableFirewall(shouldMasquerade(currentState), isSSLEnabled(), isOpenVpnServerEnabled(), ipSets.isSupportedByOperatingSystem());
        if (currentState == NetworkStateId.LOCAL_DHCP) {
            services.configureDhcpServer(services.getCurrentNetworkConfiguration());
        }
    }

    public void deviceStateChanged(Device justChangedDevice) {
        deviceStateChanged(); // do the same as normal method

        NetworkStateId currentState = getCurrentNetworkState().getId();
        if (currentState == NetworkStateId.PLUG_AND_PLAY && !justChangedDevice.isEnabled()) {
            services.healDevice(justChangedDevice);
        }
    }

    private NetworkState getSelectedNetworkState(NetworkConfiguration networkConfiguration) {
        if (networkConfiguration.isAutomatic()) {
            return new NetworkStatePlugAndPlay();
        } else {
            if (networkConfiguration.isDhcp()) {
                return new NetworkStateLocalDhcp();
            } else {
                return new NetworkStateExternalDhcp();
            }
        }
    }

    private NetworkState getCurrentNetworkState() {
        switch (dataSource.getCurrentNetworkState()) {
            case EXTERNAL_DHCP:
                return new NetworkStateExternalDhcp();
            case LOCAL_DHCP:
                return new NetworkStateLocalDhcp();
            case PLUG_AND_PLAY:
                return new NetworkStatePlugAndPlay();
            default:
                throw new EblockerException("Could not get current network state");
        }
    }

    private void setCurrentNetworkState(NetworkState state) {
        dataSource.setCurrentNetworkState(state.getId());
    }
}
