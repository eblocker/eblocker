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
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.exceptions.EblockerException;

import java.util.List;
import java.util.Set;

public class TableGeneratorIp6 extends TableGeneratorBase {
    @Inject
    public TableGeneratorIp6(@Named("network.interface.name") String standardInterface,
                             @Named("network.vpn.interface.name") String mobileVpnInterface,
                             @Named("httpPort") int httpPort,
                             @Named("httpsPort") int httpsPort,
                             @Named("proxyPort") int proxyPort,
                             @Named("proxyHTTPSPort") int proxyHTTPSPort,
                             @Named("dns.server.port") int localDnsPort
                             ) {
        super(standardInterface, mobileVpnInterface, httpPort, httpsPort, proxyPort, proxyHTTPSPort, localDnsPort);
    }

    @Override
    public Table generateNatTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table natTable = new Table("nat");
        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();

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

        ipAddressFilter.getDisabledDevicesIps().forEach(ip -> preRouting
                .rule(autoInputForSource(ip).tcp().returnFromChain()));

        if (dnsEnabled) {
            preRouting
                    // redirect all dns traffic to dns-server
                    .rule(new Rule(standardInput).dns().redirectTo(ownIpAddress, localDnsPort));
        }

        // Redirect port 80 to the proxy:
        preRouting.rule(new Rule(standardInput).http().redirectTo(ownIpAddress, proxyPort));

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .forEach(ip -> preRouting.rule(autoInputForSource(ip).https().redirectTo(selectTargetIp(ip), proxyHTTPSPort)));
        }

        for (OpenVpnClientState client : anonVpnClients) {
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                if (client.getVirtualInterfaceName() == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile (" + client.getId() + ") , no name of virtual interface set!");
                }
                Rule outputVirtualInterface = new Rule().output(client.getVirtualInterfaceName());
                natTable.chain("POSTROUTING").rule(new Rule(outputVirtualInterface).masquerade());
                natTable.chain("OUTPUT").rule(new Rule(outputVirtualInterface).accept());
            }
        }

        return natTable;
    }

    @Override
    public Table generateFilterTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table filterTable = new Table("filter");

        Chain output = filterTable.chain("OUTPUT").accept();
        output.rule(new Rule().icmpv6().icmpType(Rule.Icmp6Type.REDIRECT).drop());

        return filterTable;
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
            List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                // mark VPN traffic
                Rule markClientRoute = new Rule().mark(client.getRoute());
                clientIps.forEach(ip -> mangleTable.chain("vpn-router").rule(new Rule(markClientRoute).sourceIp(ip)));
            }
        }
        return mangleTable;
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
