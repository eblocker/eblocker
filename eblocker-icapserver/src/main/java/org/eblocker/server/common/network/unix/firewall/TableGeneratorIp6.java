/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.unix.firewall;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.util.Ip6Utils;

import java.util.List;
import java.util.Set;

public class TableGeneratorIp6 extends TableGeneratorBase {
    final static Ip6Address publicNetwork = Ip6Address.parse("2000::");
    final static int publicNetworkPrefixLength = 3;

    private Set<String> prefixes = Set.of();
    private final String malwareIp6SetName;

    @Inject
    public TableGeneratorIp6(@Named("network.interface.name") String standardInterface,
                             @Named("network.vpn.interface.name") String mobileVpnInterface,
                             @Named("httpPort") int httpPort,
                             @Named("httpsPort") int httpsPort,
                             @Named("proxyPort") int proxyPort,
                             @Named("proxyHTTPSPort") int proxyHTTPSPort,
                             @Named("dns.server.port") int localDnsPort,
                             @Named("malware.filter.ip6set.name") String malwareIp6SetName
                             ) {
        super(standardInterface, mobileVpnInterface, httpPort, httpsPort, proxyPort, proxyHTTPSPort, localDnsPort);
        this.malwareIp6SetName = malwareIp6SetName;
    }

    @Override
    public Table generateNatTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table natTable = new Table("nat");
        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();
        Chain localRedirects = natTable.chain("local-redirects");
        Chain outputMasquerading = natTable.chain("masquerading");

        // always answer dns queries directed at eblocker
        preRouting.rule(new Rule(standardInput).dns().destinationIp(ownIpAddress).redirectTo(ownIpAddress, localDnsPort));

        // Redirect port 80 & 443 to icapserver backend for user friendly URLs if not running in server mode
        if (serverEnvironment) {
            preRouting.rule(new Rule(standardInput).http().destinationIp(ownIpAddress).returnFromChain());
        } else {
            preRouting
                    .rule(new Rule().http().destinationIp(ownIpAddress).redirectTo(ownIpAddress, httpPort))
                    .rule(new Rule().https().destinationIp(ownIpAddress).redirectTo(ownIpAddress, httpsPort));
        }

        prefixes.forEach(prefix -> {
                preRouting.rule(new Rule(standardInput).sourceIp(prefix).jumpToChain(localRedirects.getName()));
        });

        ipAddressFilter.getDisabledDevicesIps().stream()
                .filter(ip -> isPublicIp(ip))
                .forEach(ip -> localRedirects
                .rule(autoInputForSource(ip).tcp().returnFromChain()));

        if (dnsEnabled) {
            preRouting
                    // redirect all dns traffic to dns-server
                    //.rule(new Rule(standardInput).dns().redirectTo(ownIpAddress, localDnsPort)); // connection tracking does not work via link-local address for UDP
                    .rule(new Rule(standardInput).dns().redirectTo(localDnsPort));
        }

        // Redirect port 80 to the proxy:
        localRedirects.rule(new Rule(standardInput).http().redirectTo(ownIpAddress, proxyPort));

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .filter(ip -> isPublicIp(ip))
                    .forEach(ip -> localRedirects.rule(autoInputForSource(ip).https().redirectTo(selectTargetIp(ip), proxyHTTPSPort)));
        }

        // Redirect any ip / non-standard-ports known to host malware to squid for filtering
        if (malwareSetEnabled) {
            ipAddressFilter.getMalwareDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .filter(ip -> isPublicIp(ip))
                    .forEach(ip ->
                            localRedirects.rule(autoInputForSource(ip)
                                    .tcp()
                                    .matchSet(true, malwareIp6SetName, "dst", "dst")
                                    .redirectTo(selectTargetIp(ip), proxyPort)));
        }

        for (OpenVpnClientState client : anonVpnClients) {
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                if (client.getVirtualInterfaceName() == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile (" + client.getId() + ") , no name of virtual interface set!");
                }
                Rule outputVirtualInterface = new Rule().output(client.getVirtualInterfaceName());
                postRouting.rule(new Rule(outputVirtualInterface).masquerade());
                output.rule(new Rule(outputVirtualInterface).accept());
            }
        }

        // Masquerading is used only for enabled devices
        postRouting.rule(new Rule(standardOutput).jumpToChain(outputMasquerading.getName()));
        ipAddressFilter.getDisabledDevicesIps().stream()
                .filter(ip -> isPublicIp(ip))
                .forEach(ip -> outputMasquerading.rule(new Rule().sourceIp(ip).returnFromChain()));
        outputMasquerading.rule(new Rule().masquerade());

        return natTable;
    }

    @Override
    public Table generateFilterTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table filterTable = new Table("filter");

        Chain forward = filterTable.chain("FORWARD").accept();
        Chain input = filterTable.chain("INPUT").accept();
        Chain output = filterTable.chain("OUTPUT").accept();
        output.rule(new Rule().icmpv6().icmpType(Rule.Icmp6Type.REDIRECT).drop());

        // block HTTP/3 for all SSL enabled devices
        ipAddressFilter.getSslEnabledDevicesIps().forEach(ip ->
                forward.rule(new Rule().sourceIp(ip).http3().reject()));

        // block IPv6 for VPN provider that do not support it
        for (OpenVpnClientState client : anonVpnClients) {
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
                if (client.getGatewayIp6() == null) {
                    clientIps.forEach(ip -> blockFromPublicIp(ip, input, forward));
                }
            }
        }

        // block IPv6 for clients using Tor
        ipAddressFilter.getTorDevicesIps().forEach(ip -> blockFromPublicIp(ip, input, forward));

        return filterTable;
    }

    private boolean isPublicIp(String ip) {
        return Ip6Utils.isInNetwork(Ip6Address.parse(ip), publicNetwork, publicNetworkPrefixLength);
    }

    private void blockFromPublicIp(String ip, Chain... chains) {
        if (isPublicIp(ip)) {
            Rule blockFromIpTcp = new Rule().sourceIp(ip).tcp().rejectWithTcpReset();
            Rule blockFromIpUdp = new Rule().sourceIp(ip).udp().reject();
            for (Chain chain : chains) {
                chain.rule(blockFromIpTcp);
                chain.rule(blockFromIpUdp);
            }
        }
    }

    @Override
    public Table generateMangleTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table mangleTable = new Table("mangle");

        //create vpn-routing or decision chain
        Chain vpnRoutingChain = mangleTable.chain("vpn-router");

        final Rule jumpToVpnChain = new Rule().jumpToChain(vpnRoutingChain.getName());
        mangleTable.chain("OUTPUT").rule(jumpToVpnChain);
        mangleTable.chain("PREROUTING").rule(jumpToVpnChain);

        for (OpenVpnClientState client : anonVpnClients) {
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                // mark VPN traffic
                List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
                Rule markClientRoute = new Rule().mark(client.getRoute());
                clientIps.forEach(ip -> vpnRoutingChain.rule(new Rule(markClientRoute).sourceIp(ip)));
            }
        }
        return mangleTable;
    }

    public void setPrefixes(Set<String> prefixes) {
        this.prefixes = prefixes;
    }

    private String selectTargetIp(String ip) {
        return ownIpAddress; // TODO: not yet implemented
    }

    private Rule autoInputForSource(String sourceIp) {
        return new Rule().input(standardInterface).sourceIp(sourceIp); // TODO: not yet implemented
    }

    public boolean isMobileClient(String ip) {
        return false; // TODO: not yet implemented
    }

    private boolean mobileVpnServerActive() {
        return false; // TODO: not yet implemented
    }
}
