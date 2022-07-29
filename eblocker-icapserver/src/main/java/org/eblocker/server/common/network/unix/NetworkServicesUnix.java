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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.ArpSpoofer;
import org.eblocker.server.common.network.DhcpServerConfiguration;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkServicesBase;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configures and controls network services on Unix OSes
 */
@Singleton
public class NetworkServicesUnix extends NetworkServicesBase {
    private static final Logger log = LoggerFactory.getLogger(NetworkServicesUnix.class);

    private final DnsConfiguration dnsConfiguration;
    private final NetworkInterfaceConfiguration interfaceConfiguration;
    private final IscDhcpServer dhcpServer;
    private final FirewallConfigurationIp4 firewallConfiguration;
    private final FirewallConfigurationIp6 firewallConfigurationIp6;
    private final String applyNetworkConfigurationCommand;
    private final String applyFirewallConfigurationCommand;
    private final String enableIp6Command;
    private final ScriptRunner scriptRunner;

    @Inject
    public NetworkServicesUnix(
            DataSource dataSource,
            DnsConfiguration dnsConfiguration,
            NetworkInterfaceConfiguration interfaceConfiguration,
            IscDhcpServer dhcpServer,
            FirewallConfigurationIp4 firewallConfiguration,
            FirewallConfigurationIp6 firewallConfigurationIp6,
            @Named("highPrioScheduledExecutor") ScheduledExecutorService executorService,
            NetworkInterfaceWrapper networkInterface,
            ArpSpoofer arpSpoofer,
            ScriptRunner scriptRunner,
            @Named("executor.arpSpoofer.startupDelay") long arpSpoofer_startupDelay,
            @Named("executor.arpSpoofer.fixedDelay") long arpSpoofer_fixedDelay,
            @Named("network.unix.apply.configuration.command") String applyNetworkConfigurationCommand,
            @Named("network.unix.apply.firewall.configuration.command") String applyFirewallConfigurationCommand,
            @Named("network.unix.enable.ip6") String enableIp6Command,
            EblockerDnsServer eblockerDnsServer
    ) {
        super(dataSource, executorService, networkInterface, arpSpoofer, arpSpoofer_startupDelay, arpSpoofer_fixedDelay, eblockerDnsServer);
        this.dnsConfiguration = dnsConfiguration;
        this.interfaceConfiguration = interfaceConfiguration;
        this.dhcpServer = dhcpServer;
        this.firewallConfiguration = firewallConfiguration;
        this.firewallConfigurationIp6 = firewallConfigurationIp6;
        this.scriptRunner = scriptRunner;
        this.applyNetworkConfigurationCommand = applyNetworkConfigurationCommand;
        this.applyFirewallConfigurationCommand = applyFirewallConfigurationCommand;
        this.enableIp6Command = enableIp6Command;
    }

    @Override
    public void configureDhcpServer(NetworkConfiguration cfg) {
        DhcpServerConfiguration config = new DhcpServerConfiguration();
        config.setIpAddress(cfg.getIpAddress());
        config.setNetmask(cfg.getNetworkMask());
        config.setGateway(cfg.getGateway());
        if (cfg.getDhcpRangeFirst() != null && cfg.getDhcpRangeLast() != null) {
            config.setRange(new DhcpRange(cfg.getDhcpRangeFirst(), cfg.getDhcpRangeLast()));
        }

        if (!cfg.isDnsServer()) {
            config.setNameServerPrimary(cfg.getNameServerPrimary());
            config.setNameServerSecondary(cfg.getNameServerSecondary());
        } else {
            config.setNameServerPrimary(cfg.getIpAddress());
            config.setNameServerSecondary(null);
        }

        config.setDevices(getDevices());
        if (cfg.isExpertMode()) {
            config.setLeaseTime(cfg.getDhcpLeaseTime());
        }
        dhcpServer.setConfiguration(config);
    }

    @Override
    public void enableDhcpServer(boolean start) {
        dhcpServer.enable(start);
    }

    @Override
    public void disableDhcpServer() {
        dhcpServer.disable();
    }

    @Override
    public void enableDhcpClient() {
        try {
            interfaceConfiguration.enableDhcp();
        } catch (IOException e) {
            throw new EblockerException("Could not enable DHCP client", e);
        }
    }

    @Override
    public void enableStaticIp(NetworkConfiguration configuration) {
        setStaticIpAddresses(configuration);
    }

    private void setStaticIpAddresses(NetworkConfiguration configuration) {
        try {
            interfaceConfiguration.enableStatic(configuration.getIpAddress(), configuration.getNetworkMask(), configuration.getGateway());
        } catch (IOException e) {
            throw new EblockerException("Could not enable static IP address", e);
        }
    }

    @Override
    public synchronized void applyNetworkConfiguration(NetworkConfiguration configuration) {
        super.applyNetworkConfiguration(configuration);
        int status = executeCommand(applyNetworkConfigurationCommand);
        if (status != 0) {
            throw new EblockerException("Command '" + applyNetworkConfigurationCommand + "' terminated with exit status: " + status);
        }
    }

    /**
     * Executes and waits for a command.
     *
     * @param command
     */
    private int executeCommand(String command, String... arguments) {
        try {
            return scriptRunner.runScript(command, arguments);
        } catch (Exception e) {
            throw new EblockerException("Could not run command '" + command + "'", e);
        }
    }

    @Override
    public void setNameserverAddresses(NetworkConfiguration configuration) {
        List<String> nameservers = new ArrayList<>();

        if (configuration.getNameServerPrimary() != null) {
            nameservers.add(configuration.getNameServerPrimary());
        }

        if (configuration.getNameServerSecondary() != null) {
            nameservers.add(configuration.getNameServerSecondary());
        }

        // fallback: use gateway as nameserver
        if (nameservers.size() == 0) {
            nameservers.add(configuration.getGateway());
        }

        try {
            dnsConfiguration.setNameserverAddresses(nameservers);
            notifyListeners(l -> l.onNameServersChange(nameservers));
        } catch (IOException e) {
            throw new EblockerException("Could not set nameserver addresses to " + nameservers, e);
        }
    }

    @Override
    protected List<String> getNativeNameServerAddresses() {
        try {
            return dnsConfiguration.getNameserverAddresses();
        } catch (IOException e) {
            log.error("Cannot get native nameserver addresses", e);
            return Collections.emptyList();
        }
    }

    @Override
    protected synchronized void enableFirewall(Set<Device> allDevices, Collection<OpenVpnClientState> vpnClients,
                                               boolean masquerade, boolean enableSSL, boolean enableEblockerDns,
                                               boolean enableEblockerMobile, boolean enableMalwareSet) {
        try {
            firewallConfiguration.enable(allDevices, vpnClients, masquerade, enableSSL, enableEblockerDns,
                    enableEblockerMobile, enableMalwareSet, () -> executeCommand(applyFirewallConfigurationCommand, "IPv4") == 0);
        } catch (IOException e) {
            log.error("i/o error applying firewall rules for IPv4", e);
        }
        try {
            firewallConfigurationIp6.enable(allDevices, vpnClients, masquerade, enableSSL, enableEblockerDns,
                    enableEblockerMobile, enableMalwareSet, () -> executeCommand(applyFirewallConfigurationCommand, "IPv6") == 0);
        } catch (IOException e) {
            log.error("i/o error applying firewall rules for IPv6", e);
        }
    }

    @Override
    public void enableIp6(boolean ip6Enabled) {
        try {
            scriptRunner.runScript(enableIp6Command, Boolean.toString(ip6Enabled));
        } catch (IOException e) {
            log.error("failed to set ip6 enabled to " + ip6Enabled, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
