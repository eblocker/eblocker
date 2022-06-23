/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.network.NetworkUtils;
import org.eblocker.server.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class TableGeneratorIp4 extends TableGeneratorBase {
    private static final Logger LOG = LoggerFactory.getLogger(TableGeneratorIp4.class);

    // fixed configuration parameters
    private final int mobileVpnSubnet;
    private final int mobileVpnNetmask;
    private final int anonSocksPort;
    private final String anonSourceIp;
    private final int httpPort;
    private final int httpsPort;
    private final Integer squidUid;
    private final String dnsAccessDeniedIp;
    private final int parentalControlRedirectHttpPort;
    private final int parentalControlRedirectHttpsPort;
    private final String fallbackIp;
    private final String malwareIpSetName;
    private final int mobileVpnServerPort;
    private final int localDnsPort;
    private final int torDnsPort;

    private String mobileVpnIpAddress;
    private String gatewayIpAddress;
    private String networkMask;


    @Inject
    public TableGeneratorIp4(@Named("network.interface.name") String standardInterface,
                             @Named("network.vpn.interface.name") String mobileVpnInterface,
                             @Named("network.vpn.subnet.ip") String mobileVpnSubnet,
                             @Named("network.vpn.subnet.netmask") String mobileVpnNetmask,
                             @Named("proxyPort") int proxyPort,
                             @Named("proxyHTTPSPort") int proxyHTTPSPort,
                             @Named("anonSocksPort") int anonSocksPort,
                             @Named("network.unix.anon.source.ip") String anonSourceIp,
                             @Named("squid.uid") Integer squidUid,
                             @Named("httpPort") int httpPort,
                             @Named("httpsPort") int httpsPort,
                             @Named("parentalControl.redirect.ip") String parentalControlRedirectIp,
                             @Named("parentalControl.redirect.http.port") int parentalControlRedirectHttpPort,
                             @Named("parentalControl.redirect.https.port") int parentalControlRedirectHttpsPort,
                             @Named("network.control.bar.host.fallback.ip") String fallbackIp,
                             @Named("malware.filter.ipset.name") String malwareIpSetName,
                             @Named("openvpn.server.port") int mobileVpnServerPort,
                             @Named("dns.server.port") int localDnsPort,
                             @Named("tor.dns.port") int torDnsPort
                          ) {
        super(standardInterface, mobileVpnInterface, proxyPort, proxyHTTPSPort);
        this.mobileVpnSubnet = IpUtils.convertIpStringToInt(mobileVpnSubnet);
        this.mobileVpnNetmask = IpUtils.convertIpStringToInt(mobileVpnNetmask);
        this.anonSocksPort = anonSocksPort;
        this.anonSourceIp = anonSourceIp;
        this.squidUid = squidUid;
        this.dnsAccessDeniedIp = parentalControlRedirectIp;
        this.parentalControlRedirectHttpPort = parentalControlRedirectHttpPort;
        this.parentalControlRedirectHttpsPort = parentalControlRedirectHttpsPort;
        this.malwareIpSetName = malwareIpSetName;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.fallbackIp = fallbackIp;
        this.mobileVpnServerPort = mobileVpnServerPort;
        this.localDnsPort = localDnsPort;
        this.torDnsPort = torDnsPort;

    }

    public Table generateNatTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table natTable = new Table("nat");

        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();

        // always answer dns queries directed at eblocker
        preRouting.rule(new Rule(standardInput).dns().destinationIp(ownIpAddress).redirectTo(ownIpAddress, localDnsPort));

        if (mobileVpnServerActive()) {
            preRouting.rule(new Rule(mobileVpnInput).dns().destinationIp(mobileVpnIpAddress).redirectTo(mobileVpnIpAddress, localDnsPort));
        }

        // Redirect port 80 & 443 to icapserver backend for user friendly URLs if not running in server mode
        if (serverEnvironment) {
            preRouting.rule(new Rule(standardInput).http().destinationIp(ownIpAddress).returnFromChain());
        } else {
            preRouting
                    .rule(new Rule().http().destinationIp(ownIpAddress).redirectTo(ownIpAddress, httpPort))
                    .rule(new Rule().https().destinationIp(ownIpAddress).redirectTo(ownIpAddress, httpsPort))
                    .rule(new Rule().http().destinationIp(fallbackIp).redirectTo(fallbackIp, httpPort))
                    .rule(new Rule().https().destinationIp(fallbackIp).redirectTo(fallbackIp, httpsPort));
        }

        if (mobileVpnServerActive()) {
            preRouting
                    .rule(new Rule(mobileVpnInput).http().destinationIp(mobileVpnIpAddress).redirectTo(mobileVpnIpAddress, httpPort))
                    .rule(new Rule(mobileVpnInput).https().destinationIp(mobileVpnIpAddress).redirectTo(mobileVpnIpAddress, httpsPort));
        }

        ipAddressFilter.getDisabledDevicesIps().forEach(ip -> preRouting
                .rule(autoInputForSource(ip).tcp().returnFromChain()));

        if (dnsEnabled) {
            preRouting
                    // redirect all dns traffic to dns-server
                    .rule(new Rule(standardInput).dns().redirectTo(ownIpAddress, localDnsPort))
                    // redirect blocked http / https traffic to redirect-service
                    .rule(new Rule().destinationIp(dnsAccessDeniedIp).http().redirectTo(dnsAccessDeniedIp, parentalControlRedirectHttpPort))
                    .rule(new Rule().destinationIp(dnsAccessDeniedIp).https().redirectTo(dnsAccessDeniedIp, parentalControlRedirectHttpsPort));
        }

        if (mobileVpnServerActive()) {
            preRouting.rule(new Rule(mobileVpnInput).dns().redirectTo(mobileVpnIpAddress, localDnsPort));
        }

        // no local traffic to be processed by squid and icap
        Rule tcpReturn = new Rule(standardInput).tcp().returnFromChain();
        Rule tcpReturnVpn = new Rule(mobileVpnInput).tcp().returnFromChain();
        preRouting
                .rule(new Rule(tcpReturn).destinationIp(NetworkUtils.privateClassC))
                .rule(new Rule(tcpReturn).destinationIp(NetworkUtils.privateClassB))
                .rule(new Rule(tcpReturn).destinationIp(NetworkUtils.privateClassA))
                .rule(new Rule(tcpReturn).destinationIp(NetworkUtils.linkLocal))
                .rule(new Rule(tcpReturn).destinationIp(fallbackIp))
                .rule(new Rule(tcpReturnVpn).destinationIp(NetworkUtils.privateClassC))
                .rule(new Rule(tcpReturnVpn).destinationIp(NetworkUtils.privateClassB))
                .rule(new Rule(tcpReturnVpn).destinationIp(NetworkUtils.privateClassA))
                .rule(new Rule(tcpReturnVpn).destinationIp(NetworkUtils.linkLocal))
                .rule(new Rule(tcpReturnVpn).destinationIp(fallbackIp));

        // Parental Control: redirect http(s) traffic to access denied page
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            preRouting
                    .rule(autoInputForSource(ip).http().redirectTo(ownIpAddress, parentalControlRedirectHttpPort))
                    .rule(autoInputForSource(ip).https().redirectTo(ownIpAddress, parentalControlRedirectHttpsPort));
        });

        // Redirect port 80 to the proxy:
        preRouting.rule(new Rule(standardInput).http().redirectTo(ownIpAddress, proxyPort));

        if (mobileVpnServerActive()) {
            preRouting.rule(new Rule(mobileVpnInput).http().redirectTo(mobileVpnIpAddress, proxyPort));
        }

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .forEach(ip -> preRouting.rule(autoInputForSource(ip).https().redirectTo(selectTargetIp(ip), proxyHTTPSPort)));
        }

        // Redirect all traffic from tor-clients
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> {
                    if (!dnsEnabled) {
                        preRouting.rule(autoInputForSource(ip).dns().redirectTo(selectTargetIp(ip), torDnsPort));
                    }
                    preRouting.rule(autoInputForSource(ip).tcp().redirectTo(selectTargetIp(ip), anonSocksPort));
                });

        // Redirect any ip / non-standard-ports known to host malware to squid for filtering
        if (malwareSetEnabled) {
            ipAddressFilter.getMalwareDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || mobileVpnServerActive())
                    .forEach(ip ->
                            preRouting.rule(autoInputForSource(ip)
                                    .tcp()
                                    .matchSet(true, malwareIpSetName, "dst", "dst")
                                    .redirectTo(selectTargetIp(ip), proxyPort)));
        }

        // nat local traffic to dns-server
        Rule loopback = new Rule().output("lo");
        String localhost = "127.0.0.1";
        output.rule(new Rule(loopback).dns().sourceIp(localhost).destinationIp(localhost).redirectTo(localhost, localDnsPort));

        // nat local traffic to default ports
        output.rule(new Rule(loopback).http().redirectTo(localhost, httpPort));
        output.rule(new Rule(loopback).https().redirectTo(localhost, httpsPort));

        // use redsocks for all outgoing traffic from special source ip
        output.rule(new Rule(standardOutput).sourceIp(anonSourceIp).tcp().redirectTo(ownIpAddress, anonSocksPort));

        // masquerading
        if (masqueradeEnabled) {
            postRouting.rule(new Rule(standardOutput).masquerade());
        }

        // Enable masquerading for mobile VPN clients
        if (mobileVpnServerActive()) {
            ipAddressFilter.getMobileVpnDevicesIps()
                    .forEach(ip -> {
                        if (IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), mobileVpnSubnet, mobileVpnNetmask)) {
                            postRouting.rule(new Rule(standardOutput).sourceIp(ip).masquerade());
                        }
                    });
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

    public Table generateFilterTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table filterTable = new Table("filter");

        Chain input = filterTable.chain("INPUT").accept();
        Chain forward = filterTable.chain("FORWARD").accept();
        filterTable.chain("OUTPUT").accept();

        LOG.info("Firewall eBlocker mode: {}", serverEnvironment);
        if (serverEnvironment) {
            LOG.info("Server mode: Setting firewall resctrictions");
            Rule dropNonStdPorts = new Rule(standardInput)
                    .states(true, Rule.State.NEW)
                    .destinationPorts(false, Rule.SSH_PORT, Rule.HTTP_PORT, Rule.HTTPS_PORT, mobileVpnServerPort)
                    .drop();
            input.rule(new Rule(dropNonStdPorts).tcp());
            input.rule(new Rule(dropNonStdPorts).udp());
            input.rule(new Rule(mobileVpnInput).tcp().destinationPort(httpPort).returnFromChain());
            input.rule(new Rule(mobileVpnInput).tcp().destinationPort(proxyHTTPSPort).returnFromChain());
        }

        // allow some mobile clients access to local networks
        if (mobileVpnServerActive()) {
            ipAddressFilter.getMobileVpnDevicesPrivateNetworkAccessIps().stream()
                    .filter(ip -> IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), mobileVpnSubnet, mobileVpnNetmask))
                    .forEach(ip -> forward
                            .rule(new Rule(mobileVpnInput).sourceIp(ip).destinationIp(NetworkUtils.privateClassC).accept())
                            .rule(new Rule(mobileVpnInput).sourceIp(ip).destinationIp(NetworkUtils.privateClassB).accept())
                            .rule(new Rule(mobileVpnInput).sourceIp(ip).destinationIp(NetworkUtils.privateClassA).accept())
                            .rule(new Rule(mobileVpnInput).sourceIp(ip).destinationIp(NetworkUtils.linkLocal).accept()));
        }

        // reject all traffic from mobile clients to local networks
        forward
                .rule(new Rule(mobileVpnInput).destinationIp(NetworkUtils.privateClassC).reject())
                .rule(new Rule(mobileVpnInput).destinationIp(NetworkUtils.privateClassB).reject())
                .rule(new Rule(mobileVpnInput).destinationIp(NetworkUtils.privateClassA).reject())
                .rule(new Rule(mobileVpnInput).destinationIp(NetworkUtils.linkLocal).reject());

        // do not block traffic to private IP addresses, e.g. routers (with DNS), printers, NAS devices, etc.
        forward
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassC).accept())
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassB).accept())
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassA).accept());

        // Drop all traffic which has not been diverted to redsocks / tor here
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> forward.rule(new Rule(standardInput).sourceIp(ip).reject()));

        // Parental Control: Drop all non-http(s) packets and interrupt connections redirected to squid
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            forward.rule(autoInputForSource(ip).drop());
            input
                    .rule(autoInputForSource(ip).tcp().destinationPort(proxyPort).reject())
                    .rule(autoInputForSource(ip).tcp().destinationPort(proxyHTTPSPort).reject());
        });

        // drop all non http/https connections on access denied ip
        if (dnsEnabled) {
            Rule dstIp = new Rule().destinationIp(dnsAccessDeniedIp);
            input
                    .rule(new Rule(dstIp).tcp().destinationPort(parentalControlRedirectHttpPort).accept())
                    .rule(new Rule(dstIp).tcp().destinationPort(parentalControlRedirectHttpsPort).accept())
                    .rule(new Rule(dstIp).drop());
        }

        // block HTTP/3 for all SSL enabled devices
        ipAddressFilter.getSslEnabledDevicesIps().forEach(ip ->
            forward.rule(new Rule().sourceIp(ip).http3().reject()));

        for (OpenVpnClientState client : anonVpnClients) {
            List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
            if (client.getState() == OpenVpnClientState.State.PENDING_RESTART) {
                // disable forwarding all traffic to non-local networks to prevent leaking packets while vpn is re-established
                clientIps.forEach(ip -> filterTable.chain("FORWARD").rule(new Rule(standardInput).sourceIp(ip).drop()));
            }
        }

        // restrict access from public addresses
        if (!serverEnvironment) {
            // allow access to eBlocker's ports from private addresses
            input
                    .rule(new Rule(standardInput).sourceIp(NetworkUtils.privateClassC).accept())
                    .rule(new Rule(standardInput).sourceIp(NetworkUtils.privateClassB).accept())
                    .rule(new Rule(standardInput).sourceIp(NetworkUtils.privateClassA).accept())
                    .rule(new Rule(standardInput).sourceIp(NetworkUtils.linkLocal).accept());

            // and to eBlocker Mobile port from all addresses
            if (mobileVpnServerActive()) {
                input.rule(new Rule(standardInput).udp().destinationPort(mobileVpnServerPort).accept());
            }

            // allow responses to DNS, Squid, etc.
            input.rule(new Rule(standardInput).states(true, Rule.State.ESTABLISHED, Rule.State.RELATED).accept());

            // block everything else from public addresses
            input.rule(new Rule(standardInput).drop());
        }

        return filterTable;
    }

    public Table generateMangleTable(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> anonVpnClients) {
        Table mangleTable = new Table("mangle");

        //create vpn-routing or decision chain
        Chain vpnRoutingChain = mangleTable.chain("vpn-router");

        final Rule jumpToVpnChain = new Rule().jumpToChain(vpnRoutingChain.getName());
        mangleTable.chain("OUTPUT").rule(jumpToVpnChain);
        mangleTable.chain("PREROUTING").rule(jumpToVpnChain);

        //do not route packets from the default gateway through any vpn tunnel -> FIXME this part has to be kept up-to-date, when the gateway IP address changes
        if (gatewayIpAddress != null) {
            vpnRoutingChain.rule(new Rule().sourceIp(gatewayIpAddress).returnFromChain());
        }

        //do not route packets for the localnet through the VPN tunnels
        final String localnetString = IpUtils.getSubnet(ownIpAddress, networkMask);
        vpnRoutingChain.rule(new Rule().destinationIp(localnetString).returnFromChain());

        List<String> enabledDevicesIps = ipAddressFilter.getEnabledDevicesIps();

        // traffic account incoming
        mangleTable.chain("ACCOUNT-IN")
                // exclude private networks
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassA).returnFromChain())
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassB).returnFromChain())
                .rule(new Rule(standardInput).destinationIp(NetworkUtils.privateClassC).returnFromChain())
                // exclude multi cast ips
                .rule(new Rule(standardInput).destinationIp("224.0.0.0/4").returnFromChain())
                .rule(new Rule(standardInput).destinationIp("240.0.0.0/4").returnFromChain());

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-IN")
                .rule(autoInputForSource(ip).returnFromChain()));

        // traffic account outgoing
        Rule notSquid = new Rule(standardOutput).ownerUid(false, squidUid).returnFromChain();
        mangleTable.chain("ACCOUNT-OUT")
                // do not account traffic from local networks (but account traffic from squid as this is seen in this chain as coming from local host)
                .rule(new Rule(notSquid).sourceIp(NetworkUtils.privateClassA))
                .rule(new Rule(notSquid).sourceIp(NetworkUtils.privateClassB))
                .rule(new Rule(notSquid).sourceIp(NetworkUtils.privateClassC));

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-OUT")
                .rule(new Rule().output(selectInterfaceForSource(ip)).destinationIp(ip).returnFromChain()));

        mangleTable.chain("PREROUTING").rule(new Rule().jumpToChain("ACCOUNT-IN"));
        mangleTable.chain("POSTROUTING").rule(new Rule().jumpToChain("ACCOUNT-OUT"));

        for (OpenVpnClientState client : anonVpnClients) {
            List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                //route traffic "after" squid (requests) also into the VPN tunnel
                Rule markClientRoute = new Rule().mark(client.getRoute());
                clientIps.forEach(ip -> mangleTable.chain("vpn-router").rule(new Rule(markClientRoute).sourceIp(ip)));
            }
        }
        return mangleTable;
    }

    /**
     * Is eBlocker Mobile active?
     */
    private boolean mobileVpnServerActive() {
        return mobileVpnServerEnabled && mobileVpnIpAddress != null;
    }

    /**
     * Creates a new firewall rule with the given sourceIp and automatically selected input interface
     */
    private Rule autoInputForSource(String sourceIp) {
        return new Rule().input(selectInterfaceForSource(sourceIp)).sourceIp(sourceIp);
    }

    private boolean isMobileClient(String ip) {
        return IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), mobileVpnSubnet, mobileVpnNetmask);
    }

    private String selectInterfaceForSource(String ip) {
        return isMobileClient(ip) ? mobileVpnInterface : standardInterface;
    }

    private String selectTargetIp(String ip) {
        return isMobileClient(ip) ? mobileVpnIpAddress : ownIpAddress;
    }

    public void setMobileVpnIpAddress(String mobileVpnIpAddress) {
        this.mobileVpnIpAddress = mobileVpnIpAddress;
    }

    public void setGatewayIpAddress(String gatewayIpAddress) {
        this.gatewayIpAddress = gatewayIpAddress;
    }

    public void setNetworkMask(String networkMask) {
        this.networkMask = networkMask;
    }

}
