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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.common.util.Levenshtein;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writes configuration files for "iptables-restore".
 */
public class FirewallConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FirewallConfiguration.class);

    private final NetworkServices networkServices;
    private final DataSource dataSource;
    private final ParentalControlAccessRestrictionsService restrictionsService;

    private final Path configFullPath;
    private final Path configDeltaPath;
    private final String interfaceName;
    private final String vpnInterfaceName;
    private final int vpnSubnet;
    private final int vpnNetmask;
    private final int proxyPort;
    private final int proxyHTTPSPort;
    private final int anonSocksPort;
    private final String anonSourceIp;
    private final Environment environment;
    private final int httpPort;
    private final int httpsPort;

    private final Levenshtein<String> levenshtein;

    private final String ipRangeClassA;
    private final String ipRangeClassB;
    private final String ipRangeClassC;
    private final String ipRangeLinkLocal;
    private final Integer squidUid;
    private final String dnsAccessDeniedIp;
    private final int parentalControlRedirectHttpPort;
    private final int parentalControlRedirectHttpsPort;
    private final String fallbackIp;
    private final String malwareIpSetName;
    private final int vpnServerPort;

    private Map<String, Table> activeTables;

    @Inject
    public FirewallConfiguration(@Named("network.unix.firewall.config.full.path") String configFullPath,
                                 @Named("network.unix.firewall.config.delta.path") String configDeltaPath,
                                 @Named("network.interface.name") String interfaceName,
                                 @Named("network.vpn.interface.name") String vpnInterfaceName,
                                 @Named("network.vpn.subnet.ip") String vpnSubnet,
                                 @Named("network.vpn.subnet.netmask") String vpnNetmask,
                                 @Named("proxyPort") int proxyPort,
                                 @Named("proxyHTTPSPort") int proxyHTTPSPort,
                                 @Named("anonSocksPort") int anonSocksPort,
                                 @Named("network.unix.anon.source.ip") String anonSourceIp,
                                 @Named("network.unix.iprange.classA") String ipRangeClassA,
                                 @Named("network.unix.iprange.classB") String ipRangeClassB,
                                 @Named("network.unix.iprange.classC") String ipRangeClassC,
                                 @Named("network.unix.iprange.linkLocal") String ipRangeLinkLocal,
                                 @Named("squid.uid") Integer squidUid,
                                 @Named("httpPort") int httpPort,
                                 @Named("httpsPort") int httpsPort,
                                 @Named("parentalControl.redirect.ip") String parentalControlRedirectIp,
                                 @Named("parentalControl.redirect.http.port") int parentalControlRedirectHttpPort,
                                 @Named("parentalControl.redirect.https.port") int parentalControlRedirectHttpsPort,
                                 @Named("network.control.bar.host.fallback.ip") String fallbackIp,
                                 @Named("malware.filter.ipset.name") String malwareIpSetName,
                                 @Named("openvpn.server.port") int vpnServerPort,
                                 NetworkServices networkServices,
                                 DataSource dataSource,
                                 ParentalControlAccessRestrictionsService restrictionsService,
                                 Environment environment) {
        this.configFullPath = Paths.get(configFullPath);
        this.configDeltaPath = Paths.get(configDeltaPath);
        this.interfaceName = interfaceName;
        this.vpnInterfaceName = vpnInterfaceName;
        this.vpnSubnet = IpUtils.convertIpStringToInt(vpnSubnet);
        this.vpnNetmask = IpUtils.convertIpStringToInt(vpnNetmask);
        this.proxyPort = proxyPort;
        this.proxyHTTPSPort = proxyHTTPSPort;
        this.anonSocksPort = anonSocksPort;
        this.anonSourceIp = anonSourceIp;
        this.ipRangeClassA = ipRangeClassA;
        this.ipRangeClassB = ipRangeClassB;
        this.ipRangeClassC = ipRangeClassC;
        this.ipRangeLinkLocal = ipRangeLinkLocal;
        this.squidUid = squidUid;
        this.dnsAccessDeniedIp = parentalControlRedirectIp;
        this.parentalControlRedirectHttpPort = parentalControlRedirectHttpPort;
        this.parentalControlRedirectHttpsPort = parentalControlRedirectHttpsPort;
        this.malwareIpSetName = malwareIpSetName;
        //FIXME: Get rid of direct dependency from networkService. We need it only for a few IP addresses.
        this.networkServices = networkServices;
        //FIXME: Get rid of direct dependency from dataSource. We need it only for the gateway IP address
        this.dataSource = dataSource;
        this.restrictionsService = restrictionsService;
        this.environment = environment;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.fallbackIp = fallbackIp;
        this.vpnServerPort = vpnServerPort;

        this.levenshtein = new Levenshtein.Builder<String>().substitutionCost(c -> Integer.MAX_VALUE).build();
    }

    private Map<String, Table> generate(Set<Device> devicesArgument, Collection<OpenVpnClientState> vpnClientArgument, boolean masquerade, boolean enableSSL, boolean enableEblockerDns, boolean enableOpenVpnServer, boolean enableMalwareSet) {
        NetworkConfiguration netConfig = networkServices.getCurrentNetworkConfiguration();
        boolean openVpnServerActive = enableOpenVpnServer && netConfig.getVpnIpAddress() != null;

        // ensure stable order to prevent deltas due to rule order changes
        Set<Device> devices = new TreeSet<>(Comparator.comparing(Device::getHardwareAddress));
        devices.addAll(devicesArgument);
        Set<OpenVpnClientState> vpnClients = new TreeSet<>(Comparator.comparing(OpenVpnClientState::getId));
        vpnClients.addAll(vpnClientArgument);
        LOG.debug("Firewall got {} vpn clients!", vpnClients.size());

        Map<String, Table> newTables = new HashMap<>();

        List<String> accessRestrictedIps = devices.stream()
                .filter(Device::isEnabled)
                .filter(device -> !device.getIpAddresses().isEmpty())
                .filter(device -> !restrictionsService.isAccessPermitted(device))
                .map(Device::getIpAddresses)
                .flatMap(List::stream)
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .collect(Collectors.toList());

        Table natTable = new Table("nat");
        Table filterTable = new Table("filter");
        Table mangleTable = new Table("mangle");

        newTables.put("nat", natTable);
        newTables.put("filter", filterTable);
        newTables.put("mangle", mangleTable);

        addNatRules(natTable, masquerade, enableSSL, enableEblockerDns, enableMalwareSet, netConfig, openVpnServerActive, devices, accessRestrictedIps);
        addFilterRules(filterTable, enableEblockerDns, openVpnServerActive, devices, accessRestrictedIps);
        addMangleRules(mangleTable, netConfig, devices);

        for (OpenVpnClientState client : vpnClients) {
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                LOG.debug("Adding firewall rules for active VPN profile: Id: {}  virtual NIC: {}", client.getId(), client.getVirtualInterfaceName());

                if (client.getVirtualInterfaceName() == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile (" + client.getId() + ") , no name of virtual interface set!");
                }

                natTable.chain("POSTROUTING").rule("-o %s -j MASQUERADE", client.getVirtualInterfaceName());
                natTable.chain("OUTPUT").rule("-o %s -j ACCEPT", client.getVirtualInterfaceName());

                //route traffic "after" squid (requests) also into the VPN tunnel
                String linkLocalAddress = client.getLinkLocalIpAddress();
                if (linkLocalAddress == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile, no linklocal address specified!");
                }

                devices.stream()
                        .filter(device -> client.getDevices().contains(device.getId()))
                        .flatMap(d -> d.getIpAddresses().stream())
                        .forEach(ip ->
                                mangleTable.chain("vpn-router").rule("-s %s -j MARK --set-mark %s", ip, client.getRoute())
                        );

                //also add the bound linklocal address (for the packets on port 80 and 443) which first go through squid and route this traffic through the VPN tunnel as well
                mangleTable.chain("vpn-router").rule("-s %s -j MARK --set-mark %s", linkLocalAddress, client.getRoute());

            } else if (client.getState() == OpenVpnClientState.State.PENDING_RESTART) {
                // disable forwarding all traffic to non-local networks to prevent leaking packets while
                // vpn is re-established
                devices.stream()
                        .filter(device -> client.getDevices().contains(device.getId()))
                        .flatMap(d -> d.getIpAddresses().stream())
                        .forEach(ip -> filterTable.chain("FORWARD").rule("-i %s -s %s -j DROP", interfaceName, ip));
            }
        }

        return newTables;
    }


    private void addNatRules(Table table, boolean masquerade, boolean enableSSL, boolean enableEblockerDns, boolean enableMalwareSet, NetworkConfiguration netConfig, boolean openVpnServerActive, Set<Device> devices, List<String> accessRestrictedIps) {
        Chain preRouting = table.chain("PREROUTING").accept();
        table.chain("INPUT").accept();
        Chain output = table.chain("OUTPUT").accept();
        Chain postRouting = table.chain("POSTROUTING").accept();

        // always answer dns queries directed at eblocker
        preRouting.rule("-i %s -d %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", interfaceName, netConfig.getIpAddress(), netConfig.getIpAddress());

        if (openVpnServerActive) {
            preRouting.rule("-i %s -d %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", vpnInterfaceName, netConfig.getVpnIpAddress(), netConfig.getVpnIpAddress());
        }

        // Redirect port 80 & 443 to icapserver backend for user friendly URLs if not running in server mode
        if (environment.isServer()) {
            preRouting.rule("-i %s -d %s -p tcp -m tcp --dport 80 -j RETURN", interfaceName, netConfig.getIpAddress());
        } else {
            preRouting
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, netConfig.getIpAddress(), netConfig.getIpAddress(), httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, netConfig.getIpAddress(), netConfig.getIpAddress(), httpsPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, fallbackIp, fallbackIp, httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, fallbackIp, fallbackIp, httpsPort);
        }

        if (openVpnServerActive) {
            preRouting
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", vpnInterfaceName, netConfig.getVpnIpAddress(), netConfig.getVpnIpAddress(), httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", vpnInterfaceName, netConfig.getVpnIpAddress(), netConfig.getVpnIpAddress(), httpsPort);
        }

        devices.stream()
                .filter(device -> !device.isEnabled())
                .filter(Device::isActive)
                .forEach(device -> device.getIpAddresses()
                        .stream()
                        .filter(IpAddress::isIpv4)
                        .map(IpAddress::toString)
                        .forEach(ip -> {
                            preRouting.rule("-i %s -p tcp -s %s -j RETURN", evaluateInterfaceName(ip), ip);
                        }));

        if (enableEblockerDns) {
            preRouting
                    // redirect all dns traffic to dns-server
                    .rule("-i %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", interfaceName, netConfig.getIpAddress())
                    // redirect blocked http / https traffic to redirect-service
                    .rule("-d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%s", dnsAccessDeniedIp, dnsAccessDeniedIp, parentalControlRedirectHttpPort)
                    .rule("-d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%s", dnsAccessDeniedIp, dnsAccessDeniedIp, parentalControlRedirectHttpsPort);
        }

        if (openVpnServerActive) {
            preRouting.rule("-i %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", vpnInterfaceName, netConfig.getVpnIpAddress());
        }

        // no local traffic to be processed by squid and icap
        final String rangeRejectOptions = "-i %s -p tcp -d %s -j RETURN";
        preRouting
                .rule(rangeRejectOptions, interfaceName, ipRangeClassC)
                .rule(rangeRejectOptions, interfaceName, ipRangeClassB)
                .rule(rangeRejectOptions, interfaceName, ipRangeClassA)
                .rule(rangeRejectOptions, interfaceName, ipRangeLinkLocal)
                .rule(rangeRejectOptions, interfaceName, fallbackIp)
                .rule(rangeRejectOptions, vpnInterfaceName, ipRangeClassC)
                .rule(rangeRejectOptions, vpnInterfaceName, ipRangeClassB)
                .rule(rangeRejectOptions, vpnInterfaceName, ipRangeClassA)
                .rule(rangeRejectOptions, vpnInterfaceName, ipRangeLinkLocal)
                .rule(rangeRejectOptions, vpnInterfaceName, fallbackIp);

        // Parental Control: redirect http(s) traffic to access denied page
        accessRestrictedIps.forEach(ip -> {
            String interfaceName = evaluateInterfaceName(ip);
            preRouting
                    .rule("-i %s -p tcp -s %s -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, ip, netConfig.getIpAddress(), parentalControlRedirectHttpPort)
                    .rule("-i %s -p tcp -s %s -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, ip, netConfig.getIpAddress(), parentalControlRedirectHttpsPort);
        });

        // Redirect port 80 to the proxy:
        preRouting.rule("-i %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, netConfig.getIpAddress(), proxyPort);

        if (openVpnServerActive) {
            preRouting.rule("-i %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", vpnInterfaceName, netConfig.getVpnIpAddress(), proxyPort);
        }

        if (enableSSL) {
            //Redirect only devices, which are enabled and SSL is enabled
            devices.stream()
                    .filter(Device::isActive)
                    .filter(Device::isSslEnabled)
                    .forEach(device -> device.getIpAddresses()
                            .stream()
                            .filter(IpAddress::isIpv4)
                            .map(IpAddress::toString)
                            .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                            .forEach(ip -> {
                                preRouting.rule("-i %s -p tcp -s %s -m tcp --dport 443 -j DNAT --to-destination %s:%d", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip, netConfig), proxyHTTPSPort);
                            }));
        }

        // Redirect all traffic from tor-clients
        devices.stream()
                .filter(Device::isEnabled)
                .filter(Device::isUseAnonymizationService)
                .filter(Device::isRoutedThroughTor)
                .flatMap(d -> d.getIpAddresses().stream())
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .forEach(ip -> {
                    if (!enableEblockerDns) {
                        preRouting.rule("-i %s -s %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:9053", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip, netConfig));
                    }
                    preRouting.rule("-i %s -p tcp -s %s -j DNAT --to-destination %s:%d", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip, netConfig), anonSocksPort);
                });

        // Redirect any ip / non-standard-ports known to host malware to squid for filtering
        if (enableMalwareSet) {
            devices.stream()
                    .filter(Device::isMalwareFilterEnabled)
                    .flatMap(device -> device.getIpAddresses().stream())
                    .filter(IpAddress::isIpv4)
                    .map(IpAddress::toString)
                    .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                    .forEach(ipAddress ->
                            preRouting.rule("-i %s -s %s -p tcp -m set --match-set %s dst,dst -j DNAT --to-destination %s:3128",
                                    evaluateInterfaceName(ipAddress), ipAddress, malwareIpSetName, selectTargetIpAddress(ipAddress, netConfig)));
        }

        // nat local traffic to dns-server
        output.rule("-o lo -p udp -s 127.0.0.1 -d 127.0.0.1 -m udp --dport 53 -j DNAT --to-destination 127.0.0.1:5300");

        // nat local traffic to default ports
        output.rule("-o lo -p tcp -m tcp --dport 80 -j DNAT --to-destination 127.0.0.1:%s", httpPort);
        output.rule("-o lo -p tcp -m tcp --dport 443 -j DNAT --to-destination 127.0.0.1:%s", httpsPort);

        // use redsocks for all outgoing traffic from special source ip
        output.rule("-o %s -s %s -p tcp -j DNAT --to-destination %s:%d", interfaceName, anonSourceIp, netConfig.getIpAddress(), anonSocksPort);

        // masquerading
        if (masquerade) {
            postRouting.rule("-o %s -j MASQUERADE", interfaceName);
        }

        // Enable masquerading for vpn clients if eBlocker mobile feature is enabled
        if (openVpnServerActive) {
            devices.stream()
                    .filter(Device::isActive)
                    .filter(Device::isVpnClient)
                    .forEach(device -> device.getIpAddresses()
                            .stream()
                            .filter(IpAddress::isIpv4)
                            .map(IpAddress::toString)
                            .forEach(ip -> {
                                if (IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask)) {
                                    postRouting.rule("-s %s -o %s -j MASQUERADE", ip, interfaceName);
                                }
                            }));
        }
    }

    private void addFilterRules(Table table, boolean enableEblockerDns, boolean openVpnServerActive, Set<Device> devices, List<String> accessRestrictedIps) {
        Chain input = table.chain("INPUT").accept();
        Chain forward = table.chain("FORWARD").accept();
        table.chain("OUTPUT").accept();

        LOG.info("Firewall eBlocker mode: {}", environment);
        if (environment.isServer()) {
            LOG.info("Server mode: Setting firewall resctrictions");
            input.rule("-i %s -p tcp -m state --state NEW -m multiport ! --dports 22,80,443,%s -j DROP", interfaceName, vpnServerPort);
            input.rule("-i %s -p udp -m state --state NEW -m multiport ! --dports 22,80,443,%s -j DROP", interfaceName, vpnServerPort);
            input.rule("-i %s -p tcp -m tcp --dport %s -j RETURN", vpnInterfaceName, httpPort);
            input.rule("-i %s -p tcp -m tcp --dport %s -j RETURN", vpnInterfaceName, proxyHTTPSPort);
        }

        // allow some mobile clients access to local networks
        if (openVpnServerActive) {
            devices.stream()
                    .filter(Device::isActive)
                    .filter(Device::isVpnClient)
                    .filter(Device::isMobilePrivateNetworkAccess)
                    .map(Device::getIpAddresses)
                    .flatMap(Collection::stream)
                    .filter(IpAddress::isIpv4)
                    .map(IpAddress::toString)
                    .filter(ip -> IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask))
                    .forEach(ip -> forward
                            .rule("-i %s -s %s -d %s -j ACCEPT", vpnInterfaceName, ip, ipRangeClassC)
                            .rule("-i %s -s %s -d %s -j ACCEPT", vpnInterfaceName, ip, ipRangeClassB)
                            .rule("-i %s -s %s -d %s -j ACCEPT", vpnInterfaceName, ip, ipRangeClassA)
                            .rule("-i %s -s %s -d %s -j ACCEPT", vpnInterfaceName, ip, ipRangeLinkLocal));
        }

        // reject all traffic from mobile clients to local networks
        forward
                .rule("-i %s -d %s -j REJECT", vpnInterfaceName, ipRangeClassC)
                .rule("-i %s -d %s -j REJECT", vpnInterfaceName, ipRangeClassB)
                .rule("-i %s -d %s -j REJECT", vpnInterfaceName, ipRangeClassA)
                .rule("-i %s -d %s -j REJECT", vpnInterfaceName, ipRangeLinkLocal);

        // do not block traffic to private IP addresses, e.g. routers (with DNS), printers, NAS devices, etc.
        forward
                .rule("-i %s -d %s -j ACCEPT", interfaceName, ipRangeClassC)
                .rule("-i %s -d %s -j ACCEPT", interfaceName, ipRangeClassB)
                .rule("-i %s -d %s -j ACCEPT", interfaceName, ipRangeClassA);

        // Drop all traffic which has not been diverted to redsocks / tor here
        devices.stream()
                .filter(Device::isEnabled)
                .filter(Device::isUseAnonymizationService)
                .filter(Device::isRoutedThroughTor)
                .flatMap(d -> d.getIpAddresses().stream())
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .forEach(ip -> forward.rule("-i %s -s %s -j REJECT", interfaceName, ip));

        // Parental Control: Drop all non-http(s) packets and interrupt connections redirected to squid
        accessRestrictedIps.forEach(ip -> {
            String interfaceName = evaluateInterfaceName(ip);
            forward.rule("-i %s -s %s -j DROP", interfaceName, ip);
            input
                    .rule("-i %s -s %s -p tcp -m tcp --dport 3128 -j REJECT", interfaceName, ip)
                    .rule("-i %s -s %s -p tcp -m tcp --dport 3130 -j REJECT", interfaceName, ip);
        });

        // drop all non http/https connections on access denied ip
        if (enableEblockerDns) {
            input
                    .rule("-d %s -p tcp -m tcp --dport %d -j ACCEPT", dnsAccessDeniedIp, parentalControlRedirectHttpPort)
                    .rule("-d %s -p tcp -m tcp --dport %d -j ACCEPT", dnsAccessDeniedIp, parentalControlRedirectHttpsPort)
                    .rule("-d %s -j DROP", dnsAccessDeniedIp);
        }
    }

    private void addMangleRules(Table table, NetworkConfiguration netConfig, Set<Device> devices) {
        //create vpn-routing or decision chain
        Chain vpnRoutingChain = table.chain("vpn-router");

        final String jumpToVpnTable = "-j " + vpnRoutingChain.getName();
        table.chain("OUTPUT").rule(jumpToVpnTable);
        table.chain("PREROUTING").rule(jumpToVpnTable);

        //do not route packets from the default gateway through any vpn tunnel -> FIXME this part has to be kept up-to-date, when the gateway IP address changes
        String gatewayIPAddress = dataSource.getGateway();
        if (gatewayIPAddress != null) {
            vpnRoutingChain.rule("-s %s -j RETURN", gatewayIPAddress);
        }

        //do not route packets for the localnet through the VPN tunnels
        final String localnetString = IpUtils.getSubnet(netConfig.getIpAddress(), netConfig.getNetworkMask());
        vpnRoutingChain.rule("-d %s -j RETURN", localnetString);

        // traffic account incoming
        table.chain("ACCOUNT-IN")
                // exclude private networks
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassA)
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassB)
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassC)
                // exclude multi cast ips
                .rule("-i %s -d %s -j RETURN", interfaceName, "224.0.0.0/4")
                .rule("-i %s -d %s -j RETURN", interfaceName, "240.0.0.0/4");

        devices.stream()
                .filter(Device::isEnabled)
                .flatMap(d -> d.getIpAddresses().stream())
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .forEach(ip -> {
                    table.chain("ACCOUNT-IN").rule(
                            "-i %s -s %s -j RETURN", evaluateInterfaceName(ip), ip);
                });

        // traffic account outgoing
        table.chain("ACCOUNT-OUT")
                // do not account traffic from local networks (but account traffic from squid as this is seen in this chain as coming from local host)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassA, squidUid)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassB, squidUid)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassC, squidUid);

        devices.stream()
                .filter(Device::isEnabled)
                .flatMap(d -> d.getIpAddresses().stream())
                .filter(IpAddress::isIpv4)
                .map(IpAddress::toString)
                .forEach(ip -> table.chain("ACCOUNT-OUT").rule("-o %s -d %s -j RETURN", evaluateInterfaceName(ip), ip));

        table.chain("PREROUTING").rule("-j ACCOUNT-IN");
        table.chain("POSTROUTING").rule("-j ACCOUNT-OUT");
    }

    private boolean isMobileClient(String ip) {
        return IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask);
    }

    private String evaluateInterfaceName(String ip) {
        return isMobileClient(ip) ? vpnInterfaceName : interfaceName;
    }

    private String selectTargetIpAddress(String ip, NetworkConfiguration netConfig) {
        return isMobileClient(ip) ? netConfig.getVpnIpAddress() : netConfig.getIpAddress();
    }

    public synchronized void enable(Set<Device> allDevices, Collection<OpenVpnClientState> vpnClients,
                                    boolean masquerade, boolean enableSSL, boolean enableEblockerDns,
                                    boolean enableOpenVpnServer, boolean enableMalwareSet,
                                    Supplier<Boolean> applyFirewallRules) throws IOException {
        Map<String, Table> newTables = generate(allDevices, vpnClients, masquerade, enableSSL, enableEblockerDns, enableOpenVpnServer, enableMalwareSet);

        // write delta config
        String deltaConfig = null;
        if (activeTables != null) {
            deltaConfig = createTablesDiff(activeTables, newTables);
            Files.write(configDeltaPath, deltaConfig.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } else {
            Files.deleteIfExists(configDeltaPath);
        }

        // write full config
        String fullConfig = createTablesDiff(Collections.emptyMap(), newTables);
        Files.write(configFullPath, fullConfig.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        if (applyFirewallRules.get()) {
            activeTables = newTables;
        } else {
            LOG.error("applying firewall rules failed");
            LOG.error("delta rules:\n{}", deltaConfig);
            LOG.error("full rules:\n{}", fullConfig);
        }
    }

    /**
     * Writes all the table information to the file (final step)
     */
    private String createTablesDiff(Map<String, Table> currentTables, Map<String, Table> newTables) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        for (Table newTable : newTables.values()) {
            writer.format("*%s\n", newTable.getName());
            Table currentTable = currentTables.get(newTable.getName());
            if (currentTable == null) {
                currentTable = new Table(newTable.getName());
            }
            writeTableDiff(writer, currentTable, newTable);
            writer.println("COMMIT");
        }
        writer.flush();
        return stringWriter.toString();
    }

    private void writeTableDiff(PrintWriter writer, Table currentTable, Table newTable) {
        Supplier<TreeSet<Chain>> factory = () -> new TreeSet<>(Comparator.comparing(Chain::getName));
        TreeSet<Chain> currentChains = currentTable.getChains().stream().collect(Collectors.toCollection(factory));
        TreeSet<Chain> newChains = newTable.getChains().stream().collect(Collectors.toCollection(factory));

        Sets.SetView<Chain> addedChains = Sets.difference(newChains, currentChains);
        addedChains.forEach(chain -> writer.format(":%s %s\n", chain.getName(), chain.getPolicy()));

        Sets.SetView<Chain> removedChains = Sets.difference(currentChains, newChains);
        removedChains.forEach(chain -> writer.format("-D %s\n", chain.getName()));

        Map<String, Chain> currentChainsByName = currentTable.getChains().stream().collect(Collectors.toMap(Chain::getName, Function.identity()));
        newChains.forEach(chain -> writeChainDiff(writer, currentChainsByName.get(chain.getName()), chain));
    }

    private void writeChainDiff(PrintWriter writer, Chain currentChain, Chain newChain) {
        if (currentChain == null) {
            newChain.getRules().forEach(rule -> writer.format("-A %s %s\n", newChain.getName(), rule));
            return;
        }

        Levenshtein.Distance distance = levenshtein.distance(currentChain.getRules(), newChain.getRules());
        int i = 1;
        for (Levenshtein.DistanceMatrixEntry e : distance.getEditSequence()) {
            switch (e.getOperation()) {
                case NO_OPERATION:
                    ++i;
                    break;
                case INSERT:
                    writer.format("-I %s %d %s\n", newChain.getName(), i, newChain.getRules().get(e.getY() - 1));
                    ++i;
                    break;
                case DELETE:
                    writer.format("-D %s %d\n", newChain.getName(), i);
                    break;
                case SUBSTITUTE:
                    writer.format("-R %s %d %s\n", newChain.getName(), i, newChain.getRules().get(e.getY() - 1));
                    ++i;
                    break;
            }
        }
    }

    private class Table {
        private String name;
        private Map<String, Chain> chainsByName = new LinkedHashMap<>();

        public Table(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Collection<Chain> getChains() {
            return chainsByName.values();
        }

        public Chain chain(String name) {
            if (!chainsByName.containsKey(name)) {
                chainsByName.put(name, new Chain(name));
            }
            return chainsByName.get(name);
        }
    }

    private class Chain {
        private String name;
        private String policy = "-";
        private List<String> rules = new ArrayList<>();

        public Chain(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getPolicy() {
            return policy;
        }

        public Chain accept() {
            policy = "ACCEPT";
            return this;
        }

        public List<String> getRules() {
            return rules;
        }

        public Chain rule(String rule) {
            rules.add(rule);
            return this;
        }

        public Chain rule(String rule, Object... options) {
            rules.add(String.format(rule, options));
            return this;
        }
    }
}
