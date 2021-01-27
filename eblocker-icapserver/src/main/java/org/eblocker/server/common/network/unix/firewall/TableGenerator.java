package org.eblocker.server.common.network.unix.firewall;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class TableGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(TableGenerator.class);

    // fixed configuration parameters
    private final String interfaceName;
    private final String vpnInterfaceName;
    private final int vpnSubnet;
    private final int vpnNetmask;
    private final int proxyPort;
    private final int proxyHTTPSPort;
    private final int anonSocksPort;
    private final String anonSourceIp;
    private final int httpPort;
    private final int httpsPort;
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

    // eBlocker's IP address configuration
    private String ownIpAddress;
    private String vpnIpAddress;
    private String gatewayIpAddress;
    private String networkMask;

    // flags
    private boolean masqueradeEnabled;
    private boolean sslEnabled;
    private boolean dnsEnabled;
    private boolean vpnServerEnabled;
    private boolean malwareSetEnabled;
    private boolean serverEnvironment;

    // set by generate()
    private Table natTable, filterTable, mangleTable;
    private boolean openVpnServerActive;
    private IpAddressFilter ipAddressFilter;

    // rule templates
    private Rule stdInput, vpnInput, stdOutput;

    @Inject
    public TableGenerator(@Named("network.interface.name") String interfaceName,
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
                          @Named("openvpn.server.port") int vpnServerPort
    ) {
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
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.fallbackIp = fallbackIp;
        this.vpnServerPort = vpnServerPort;

        // prepare rule templates
        stdInput = new Rule().input(interfaceName);
        vpnInput = new Rule().input(vpnInterfaceName);
        stdOutput = new Rule().output(interfaceName);

    }

    public synchronized List<Table> generate(IpAddressFilter ipAddressFilter, Set<OpenVpnClientState> vpnClients) {
        this.ipAddressFilter = ipAddressFilter;

        openVpnServerActive = vpnServerEnabled && vpnIpAddress != null;

        natTable = new Table("nat");
        filterTable = new Table("filter");
        mangleTable = new Table("mangle");

        addNatRules();
        addFilterRules();
        addMangleRules();

        addVpnClientRules(vpnClients);

        return List.of(natTable, filterTable, mangleTable);
    }

    private void addNatRules() {
        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();

        // always answer dns queries directed at eblocker
        preRouting.rule(new Rule(stdInput).dns().destinationIp(ownIpAddress).destinationNatTo(ownIpAddress, 5300));

        if (openVpnServerActive) {
            preRouting.rule(new Rule(vpnInput).dns().destinationIp(vpnIpAddress).destinationNatTo(vpnIpAddress, 5300));
        }

        // Redirect port 80 & 443 to icapserver backend for user friendly URLs if not running in server mode
        if (serverEnvironment) {
            preRouting.rule(new Rule(stdInput).http().destinationIp(ownIpAddress).returnFromChain());
        } else {
            preRouting
                    .rule(new Rule(stdInput).http().destinationIp(ownIpAddress).destinationNatTo(ownIpAddress, httpPort))
                    .rule(new Rule(stdInput).https().destinationIp(ownIpAddress).destinationNatTo(ownIpAddress, httpsPort))
                    .rule(new Rule(stdInput).http().destinationIp(fallbackIp).destinationNatTo(fallbackIp, httpPort))
                    .rule(new Rule(stdInput).https().destinationIp(fallbackIp).destinationNatTo(fallbackIp, httpsPort));
        }

        if (openVpnServerActive) {
            preRouting
                    .rule(new Rule(vpnInput).http().destinationIp(vpnIpAddress).destinationNatTo(vpnIpAddress, httpPort))
                    .rule(new Rule(vpnInput).https().destinationIp(vpnIpAddress).destinationNatTo(vpnIpAddress, httpsPort));
        }

        ipAddressFilter.getDisabledDevicesIps().forEach(ip -> preRouting
                .rule(new Rule().input(evaluateInterfaceName(ip)).tcp().sourceIp(ip).returnFromChain()));

        if (dnsEnabled) {
            preRouting
                    // redirect all dns traffic to dns-server
                    .rule(new Rule(stdInput).dns().destinationNatTo(ownIpAddress, 5300))
                    // redirect blocked http / https traffic to redirect-service
                    .rule(new Rule().destinationIp(dnsAccessDeniedIp).http().destinationNatTo(dnsAccessDeniedIp, parentalControlRedirectHttpPort))
                    .rule(new Rule().destinationIp(dnsAccessDeniedIp).https().destinationNatTo(dnsAccessDeniedIp, parentalControlRedirectHttpsPort));
        }

        if (openVpnServerActive) {
            preRouting.rule(new Rule(vpnInput).dns().destinationNatTo(vpnIpAddress, 5300));
        }

        // no local traffic to be processed by squid and icap
        Rule tcpReturn = new Rule(stdInput).tcp().returnFromChain();
        Rule tcpReturnVpn = new Rule(vpnInput).tcp().returnFromChain();
        preRouting
                .rule(new Rule(tcpReturn).destinationIp(ipRangeClassC))
                .rule(new Rule(tcpReturn).destinationIp(ipRangeClassB))
                .rule(new Rule(tcpReturn).destinationIp(ipRangeClassA))
                .rule(new Rule(tcpReturn).destinationIp(ipRangeLinkLocal))
                .rule(new Rule(tcpReturn).destinationIp(fallbackIp))
                .rule(new Rule(tcpReturnVpn).destinationIp(ipRangeClassC))
                .rule(new Rule(tcpReturnVpn).destinationIp(ipRangeClassB))
                .rule(new Rule(tcpReturnVpn).destinationIp(ipRangeClassA))
                .rule(new Rule(tcpReturnVpn).destinationIp(ipRangeLinkLocal))
                .rule(new Rule(tcpReturnVpn).destinationIp(fallbackIp));

        // Parental Control: redirect http(s) traffic to access denied page
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            preRouting
                    .rule(redirectAutoInterface(ip, 80, ownIpAddress, parentalControlRedirectHttpPort).tcp())
                    .rule(redirectAutoInterface(ip, 443, ownIpAddress, parentalControlRedirectHttpsPort).tcp());
        });

        // Redirect port 80 to the proxy:
        preRouting.rule(new Rule(stdInput).http().destinationNatTo(ownIpAddress, proxyPort));

        if (openVpnServerActive) {
            preRouting.rule(new Rule(vpnInput).http().destinationNatTo(vpnIpAddress, proxyPort));
        }

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                    .forEach(ip -> preRouting.rule(redirectAutoInterface(ip, 443, proxyHTTPSPort).tcp()));
        }

        // Redirect all traffic from tor-clients
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> {
                    if (!dnsEnabled) {
                        preRouting.rule(redirectAutoInterface(ip, 53, 9053).udp());
                    }
                    preRouting.rule(redirectAutoInterface(ip, anonSocksPort).tcp());
                });

        // Redirect any ip / non-standard-ports known to host malware to squid for filtering
        if (malwareSetEnabled) {
            ipAddressFilter.getMalwareDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                    .forEach(ipAddress ->
                            preRouting.rule(redirectAutoInterface(ipAddress, 3128)
                                    .tcp()
                                    .matchSet(true, malwareIpSetName, "dst", "dst")));
        }

        // nat local traffic to dns-server
        Rule loopback = new Rule().output("lo");
        String localhost = "127.0.0.1";
        output.rule(new Rule(loopback).dns().sourceIp(localhost).destinationIp(localhost).destinationNatTo(localhost, 5300));

        // nat local traffic to default ports
        output.rule(new Rule(loopback).http().destinationNatTo(localhost, httpPort));
        output.rule(new Rule(loopback).https().destinationNatTo(localhost, httpsPort));

        // use redsocks for all outgoing traffic from special source ip
        output.rule(new Rule(stdOutput).sourceIp(anonSourceIp).tcp().destinationNatTo(ownIpAddress, anonSocksPort));

        // masquerading
        if (masqueradeEnabled) {
            postRouting.rule(new Rule(stdOutput).masquerade());
        }

        // Enable masquerading for vpn clients if eBlocker mobile feature is enabled
        if (openVpnServerActive) {
            ipAddressFilter.getVpnClientDevicesIps()
                    .forEach(ip -> {
                        if (IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask)) {
                            postRouting.rule(new Rule(stdOutput).sourceIp(ip).masquerade());
                        }
                    });
        }
    }

    private void addFilterRules() {
        Chain input = filterTable.chain("INPUT").accept();
        Chain forward = filterTable.chain("FORWARD").accept();
        filterTable.chain("OUTPUT").accept();

        LOG.info("Firewall eBlocker mode: {}", serverEnvironment);
        if (serverEnvironment) {
            LOG.info("Server mode: Setting firewall resctrictions");
            Rule dropNonStdPorts = new Rule(stdInput)
                    .states(true, Rule.State.NEW)
                    .destinationPorts(false, 22, 80, 443, vpnServerPort)
                    .drop();
            input.rule(new Rule(dropNonStdPorts).tcp());
            input.rule(new Rule(dropNonStdPorts).udp());
            input.rule(new Rule(vpnInput).tcp().destinationPort(httpPort).returnFromChain());
            input.rule(new Rule(vpnInput).tcp().destinationPort(proxyHTTPSPort).returnFromChain());
        }

        // allow some mobile clients access to local networks
        if (openVpnServerActive) {
            ipAddressFilter.getVpnClientDevicesPrivateNetworkAccessIps().stream()
                    .filter(ip -> IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask))
                    .forEach(ip -> forward
                            .rule(new Rule(vpnInput).sourceIp(ip).destinationIp(ipRangeClassC).accept())
                            .rule(new Rule(vpnInput).sourceIp(ip).destinationIp(ipRangeClassB).accept())
                            .rule(new Rule(vpnInput).sourceIp(ip).destinationIp(ipRangeClassA).accept())
                            .rule(new Rule(vpnInput).sourceIp(ip).destinationIp(ipRangeLinkLocal).accept()));
        }

        // reject all traffic from mobile clients to local networks
        forward
                .rule(new Rule(vpnInput).destinationIp(ipRangeClassC).reject())
                .rule(new Rule(vpnInput).destinationIp(ipRangeClassB).reject())
                .rule(new Rule(vpnInput).destinationIp(ipRangeClassA).reject())
                .rule(new Rule(vpnInput).destinationIp(ipRangeLinkLocal).reject());

        // do not block traffic to private IP addresses, e.g. routers (with DNS), printers, NAS devices, etc.
        forward
                .rule(new Rule(stdInput).destinationIp(ipRangeClassC).accept())
                .rule(new Rule(stdInput).destinationIp(ipRangeClassB).accept())
                .rule(new Rule(stdInput).destinationIp(ipRangeClassA).accept());

        // Drop all traffic which has not been diverted to redsocks / tor here
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> forward.rule(new Rule(stdInput).sourceIp(ip).reject()));

        // Parental Control: Drop all non-http(s) packets and interrupt connections redirected to squid
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            Rule srcIp = new Rule().input(evaluateInterfaceName(ip)).sourceIp(ip);
            forward.rule(new Rule(srcIp).drop());
            input
                    .rule(new Rule(srcIp).tcp().destinationPort(3128).reject())
                    .rule(new Rule(srcIp).tcp().destinationPort(3130).reject());
        });

        // drop all non http/https connections on access denied ip
        if (dnsEnabled) {
            Rule dstIp = new Rule().destinationIp(dnsAccessDeniedIp);
            input
                    .rule(new Rule(dstIp).tcp().destinationPort(parentalControlRedirectHttpPort).accept())
                    .rule(new Rule(dstIp).tcp().destinationPort(parentalControlRedirectHttpsPort).accept())
                    .rule(new Rule(dstIp).drop());
        }
    }

    private void addMangleRules() {
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
                .rule(new Rule(stdInput).destinationIp(ipRangeClassA).returnFromChain())
                .rule(new Rule(stdInput).destinationIp(ipRangeClassB).returnFromChain())
                .rule(new Rule(stdInput).destinationIp(ipRangeClassC).returnFromChain())
                // exclude multi cast ips
                .rule(new Rule(stdInput).destinationIp("224.0.0.0/4").returnFromChain())
                .rule(new Rule(stdInput).destinationIp("240.0.0.0/4").returnFromChain());

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-IN")
                .rule(new Rule().input(evaluateInterfaceName(ip)).sourceIp(ip).returnFromChain()));

        // traffic account outgoing
        Rule notSquid = new Rule(stdOutput).ownerUid(false, squidUid).returnFromChain();
        mangleTable.chain("ACCOUNT-OUT")
                // do not account traffic from local networks (but account traffic from squid as this is seen in this chain as coming from local host)
                .rule(new Rule(notSquid).sourceIp(ipRangeClassA))
                .rule(new Rule(notSquid).sourceIp(ipRangeClassB))
                .rule(new Rule(notSquid).sourceIp(ipRangeClassC));

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-OUT")
                .rule(new Rule().output(evaluateInterfaceName(ip)).destinationIp(ip).returnFromChain()));

        mangleTable.chain("PREROUTING").rule(new Rule().jumpToChain("ACCOUNT-IN"));
        mangleTable.chain("POSTROUTING").rule(new Rule().jumpToChain("ACCOUNT-OUT"));
    }

    private void addVpnClientRules(Set<OpenVpnClientState> vpnClients) {
        for (OpenVpnClientState client : vpnClients) {
            List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
            if (client.getState() == OpenVpnClientState.State.ACTIVE) {
                LOG.debug("Adding firewall rules for active VPN profile: Id: {}  virtual NIC: {}", client.getId(), client.getVirtualInterfaceName());

                if (client.getVirtualInterfaceName() == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile (" + client.getId() + ") , no name of virtual interface set!");
                }

                Rule outputVirtualInterface = new Rule().output(client.getVirtualInterfaceName());
                natTable.chain("POSTROUTING").rule(new Rule(outputVirtualInterface).masquerade());
                natTable.chain("OUTPUT").rule(new Rule(outputVirtualInterface).accept());

                //route traffic "after" squid (requests) also into the VPN tunnel
                String linkLocalAddress = client.getLinkLocalIpAddress();
                if (linkLocalAddress == null) {
                    throw new EblockerException("Error while trying to create firewall rules for VPN profile, no linklocal address specified!");
                }

                Rule markClientRoute = new Rule().mark(client.getRoute());
                clientIps.forEach(ip -> mangleTable.chain("vpn-router")
                        .rule(new Rule(markClientRoute).sourceIp(ip)));

                //also add the bound linklocal address (for the packets on port 80 and 443) which first go through squid and route this traffic through the VPN tunnel as well
                mangleTable.chain("vpn-router")
                        .rule(new Rule(markClientRoute).sourceIp(linkLocalAddress));

            } else if (client.getState() == OpenVpnClientState.State.PENDING_RESTART) {
                // disable forwarding all traffic to non-local networks to prevent leaking packets while
                // vpn is re-established
                clientIps.forEach(ip -> filterTable.chain("FORWARD")
                        .rule(new Rule(stdInput).sourceIp(ip).drop()));
            }
        }
    }

    // auto-select input interface
    private Rule redirectAutoInterface(String sourceIp, String targetIp, int targetPort) {
        return new Rule()
                .input(evaluateInterfaceName(sourceIp))
                .sourceIp(sourceIp)
                .destinationNatTo(targetIp, targetPort);
    }

    // auto-select input interface
    private Rule redirectAutoInterface(String sourceIp, int destinationPort, String targetIp, int targetPort) {
        return redirectAutoInterface(sourceIp, targetIp, targetPort)
                .destinationPort(destinationPort);
    }

    // auto-select input interface and target IP
    private Rule redirectAutoInterface(String sourceIp, int destinationPort, int targetPort) {
        return redirectAutoInterface(sourceIp, destinationPort, selectTargetIpAddress(sourceIp), targetPort);
    }

    // auto-select input interface and target IP
    private Rule redirectAutoInterface(String sourceIp, int targetPort) {
        return redirectAutoInterface(sourceIp, selectTargetIpAddress(sourceIp), targetPort);
    }

    private boolean isMobileClient(String ip) {
        return IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask);
    }

    private String evaluateInterfaceName(String ip) {
        return isMobileClient(ip) ? vpnInterfaceName : interfaceName;
    }

    private String selectTargetIpAddress(String ip) {
        return isMobileClient(ip) ? vpnIpAddress : ownIpAddress;
    }

    public void setOwnIpAddress(String ownIpAddress) {
        this.ownIpAddress = ownIpAddress;
    }

    public void setVpnIpAddress(String vpnIpAddress) {
        this.vpnIpAddress = vpnIpAddress;
    }

    public void setGatewayIpAddress(String gatewayIpAddress) {
        this.gatewayIpAddress = gatewayIpAddress;
    }

    public void setNetworkMask(String networkMask) {
        this.networkMask = networkMask;
    }

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

    public void setVpnServerEnabled(boolean vpnServerEnabled) {
        this.vpnServerEnabled = vpnServerEnabled;
    }

    public void setMalwareSetEnabled(boolean malwareSetEnabled) {
        this.malwareSetEnabled = malwareSetEnabled;
    }
}