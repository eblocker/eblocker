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

import java.util.Set;

public class TableGeneratorIp6 extends TableGeneratorBase {
    @Inject
    public TableGeneratorIp6(@Named("network.interface.name") String standardInterface,
                             @Named("network.vpn.interface.name") String mobileVpnInterface,
                             @Named("proxyPort") int proxyPort,
                             @Named("proxyHTTPSPort") int proxyHTTPSPort) {
        super(standardInterface, mobileVpnInterface, proxyPort, proxyHTTPSPort);
    }

    @Override
    public Table generateNatTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table natTable = new Table("nat");
        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();

        ipAddressFilter.getDisabledDevicesIps().forEach(ip -> preRouting
                .rule(autoInputForSource(ip).tcp().returnFromChain()));

        // Redirect port 80 to the proxy:
        preRouting.rule(new Rule(standardInput).http().redirectTo(ownIpAddress, proxyPort));

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .forEach(ip -> preRouting.rule(autoInputForSource(ip).https().redirectTo(selectTargetIp(ip), proxyHTTPSPort)));
        }

        return natTable;
    }

    @Override
    public Table generateFilterTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        return new Table("filter");
    }

    @Override
    public Table generateMangleTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        return new Table("mangle");
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
