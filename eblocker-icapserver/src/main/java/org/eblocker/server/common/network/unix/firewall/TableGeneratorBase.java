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

import org.eblocker.server.common.data.openvpn.OpenVpnClientState;

import java.util.Set;

public abstract class TableGeneratorBase {
    protected final String standardInterface;
    protected final String mobileVpnInterface;
    protected final int httpPort;
    protected final int httpsPort;
    protected final int proxyPort;
    protected final int proxyHTTPSPort;
    protected final int localDnsPort;

    // rule templates
    protected final Rule standardInput, mobileVpnInput, standardOutput;

    // flags
    protected boolean masqueradeEnabled;
    protected boolean sslEnabled;
    protected boolean dnsEnabled;
    protected boolean mobileVpnServerEnabled;
    protected boolean malwareSetEnabled;
    protected boolean serverEnvironment;
    // eBlocker's IP address configuration
    protected String ownIpAddress;

    public TableGeneratorBase(
            String standardInterface,
            String mobileVpnInterface,
            int httpPort, int httpsPort,
            int proxyPort, int proxyHTTPSPort,
            int localDnsPort) {

        this.standardInterface = standardInterface;
        this.mobileVpnInterface = mobileVpnInterface;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.proxyPort = proxyPort;
        this.proxyHTTPSPort = proxyHTTPSPort;
        this.localDnsPort = localDnsPort;

        // prepare rule templates
        standardInput = new Rule().input(standardInterface);
        mobileVpnInput = new Rule().input(mobileVpnInterface);
        standardOutput = new Rule().output(standardInterface);
    }

    abstract public Table generateNatTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients);
    abstract public Table generateFilterTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients);
    abstract public Table generateMangleTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients);

    public void setServerEnvironment(boolean serverEnvironment) {
        this.serverEnvironment = serverEnvironment;
    }

    public void setMasqueradeEnabled(boolean masqueradeEnabled) {
        this.masqueradeEnabled = masqueradeEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public void setDnsEnabled(boolean dnsEnabled) {
        this.dnsEnabled = dnsEnabled;
    }

    public void setMobileVpnServerEnabled(boolean mobileVpnServerEnabled) {
        this.mobileVpnServerEnabled = mobileVpnServerEnabled;
    }

    public void setMalwareSetEnabled(boolean malwareSetEnabled) {
        this.malwareSetEnabled = malwareSetEnabled;
    }

    public void setOwnIpAddress(String ownIpAddress) {
        this.ownIpAddress = ownIpAddress;
    }
}
