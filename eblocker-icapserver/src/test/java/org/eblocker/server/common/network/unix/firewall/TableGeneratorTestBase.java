/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.data.openvpn.OpenVpnClientState;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TableGeneratorTestBase {
    protected final int proxyPort = 3128;
    protected final int proxyHTTPSPort = 3130;
    protected final int localDnsPort = 5300;
    protected final int anonSocksPort = 12345;
    protected final int parentalControlRedirectHttpPort = 3003;
    protected final int parentalControlRedirectHttpsPort = 3004;
    protected final int httpPort = 3000;
    protected final int httpsPort = 3443;

    protected final String standardInterface = "eth0";
    protected final String mobileVpnInterface = "tun33";

    protected IpAddressFilter deviceIpFilter;
    protected Set<OpenVpnClientState> anonVpnClients;

    protected Simulator natPre, natPost, natOutput, filterForward, filterInput, mangleVpn, mangleOutput;
    protected Table natTable, filterTable, mangleTable;

    protected void createTablesAndSimulators(TableGeneratorBase generator) {
        natTable = generator.generateNatTable(deviceIpFilter, anonVpnClients);
        mangleTable = generator.generateMangleTable(deviceIpFilter, anonVpnClients);
        filterTable = generator.generateFilterTable(deviceIpFilter, anonVpnClients);

        natPre = new Simulator(natTable.chain("PREROUTING"));
        natPost = new Simulator(natTable.chain("POSTROUTING"));
        natOutput = new Simulator(natTable.chain("OUTPUT"));
        filterForward = new Simulator(filterTable.chain("FORWARD"));
        filterInput = new Simulator(filterTable.chain("INPUT"));
        mangleVpn = new Simulator(mangleTable.chain("vpn-router"));
        mangleOutput = new Simulator(mangleTable.chain("OUTPUT"));
        mangleOutput.addSubChain(mangleTable.chain("vpn-router"));
    }

    protected List<Rule> getAllRules() {
        return Stream.of(natTable, mangleTable, filterTable)
                .flatMap(table -> table.getChains().stream())
                .flatMap(chain -> chain.getRules().stream())
                .collect(Collectors.toList());
    }
}
