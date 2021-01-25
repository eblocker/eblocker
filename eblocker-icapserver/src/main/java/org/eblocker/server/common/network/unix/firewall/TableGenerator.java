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

    private void addVpnClientRules(Set<OpenVpnClientState> vpnClients) {
        for (OpenVpnClientState client : vpnClients) {
            List<String> clientIps = ipAddressFilter.getDevicesIps(client.getDevices());
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

                clientIps.forEach(ip -> mangleTable.chain("vpn-router").rule("-s %s -j MARK --set-mark %s", ip, client.getRoute()));

                //also add the bound linklocal address (for the packets on port 80 and 443) which first go through squid and route this traffic through the VPN tunnel as well
                mangleTable.chain("vpn-router").rule("-s %s -j MARK --set-mark %s", linkLocalAddress, client.getRoute());

            } else if (client.getState() == OpenVpnClientState.State.PENDING_RESTART) {
                // disable forwarding all traffic to non-local networks to prevent leaking packets while
                // vpn is re-established
                clientIps.forEach(ip -> filterTable.chain("FORWARD").rule("-i %s -s %s -j DROP", interfaceName, ip));
            }
        }
    }

    private void addNatRules() {
        Chain preRouting = natTable.chain("PREROUTING").accept();
        natTable.chain("INPUT").accept();
        Chain output = natTable.chain("OUTPUT").accept();
        Chain postRouting = natTable.chain("POSTROUTING").accept();

        // always answer dns queries directed at eblocker
        preRouting.rule("-i %s -d %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", interfaceName, ownIpAddress, ownIpAddress);

        if (openVpnServerActive) {
            preRouting.rule("-i %s -d %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", vpnInterfaceName, vpnIpAddress, vpnIpAddress);
        }

        // Redirect port 80 & 443 to icapserver backend for user friendly URLs if not running in server mode
        if (serverEnvironment) {
            preRouting.rule("-i %s -d %s -p tcp -m tcp --dport 80 -j RETURN", interfaceName, ownIpAddress);
        } else {
            preRouting
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, ownIpAddress, ownIpAddress, httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, ownIpAddress, ownIpAddress, httpsPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, fallbackIp, fallbackIp, httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, fallbackIp, fallbackIp, httpsPort);
        }

        if (openVpnServerActive) {
            preRouting
                    .rule("-i %s -d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", vpnInterfaceName, vpnIpAddress, vpnIpAddress, httpPort)
                    .rule("-i %s -d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%d", vpnInterfaceName, vpnIpAddress, vpnIpAddress, httpsPort);
        }

        ipAddressFilter.getDisabledDevicesIps().forEach(ip -> preRouting.rule("-i %s -p tcp -s %s -j RETURN", evaluateInterfaceName(ip), ip));

        if (dnsEnabled) {
            preRouting
                    // redirect all dns traffic to dns-server
                    .rule("-i %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", interfaceName, ownIpAddress)
                    // redirect blocked http / https traffic to redirect-service
                    .rule("-d %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%s", dnsAccessDeniedIp, dnsAccessDeniedIp, parentalControlRedirectHttpPort)
                    .rule("-d %s -p tcp -m tcp --dport 443 -j DNAT --to-destination %s:%s", dnsAccessDeniedIp, dnsAccessDeniedIp, parentalControlRedirectHttpsPort);
        }

        if (openVpnServerActive) {
            preRouting.rule("-i %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:5300", vpnInterfaceName, vpnIpAddress);
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
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            String interfaceName = evaluateInterfaceName(ip);
            preRouting
                    .rule("-i %s -p tcp -s %s -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, ip, ownIpAddress, parentalControlRedirectHttpPort)
                    .rule("-i %s -p tcp -s %s -m tcp --dport 443 -j DNAT --to-destination %s:%d", interfaceName, ip, ownIpAddress, parentalControlRedirectHttpsPort);
        });

        // Redirect port 80 to the proxy:
        preRouting.rule("-i %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", interfaceName, ownIpAddress, proxyPort);

        if (openVpnServerActive) {
            preRouting.rule("-i %s -p tcp -m tcp --dport 80 -j DNAT --to-destination %s:%d", vpnInterfaceName, vpnIpAddress, proxyPort);
        }

        if (sslEnabled) {
            //Redirect only devices, which are enabled and SSL is enabled
            ipAddressFilter.getSslEnabledDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                    .forEach(ip -> {
                        preRouting.rule("-i %s -p tcp -s %s -m tcp --dport 443 -j DNAT --to-destination %s:%d", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip), proxyHTTPSPort);
                    });
        }

        // Redirect all traffic from tor-clients
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> {
                    if (!dnsEnabled) {
                        preRouting.rule("-i %s -s %s -p udp -m udp --dport 53 -j DNAT --to-destination %s:9053", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip));
                    }
                    preRouting.rule("-i %s -p tcp -s %s -j DNAT --to-destination %s:%d", evaluateInterfaceName(ip), ip, selectTargetIpAddress(ip), anonSocksPort);
                });

        // Redirect any ip / non-standard-ports known to host malware to squid for filtering
        if (malwareSetEnabled) {
            ipAddressFilter.getMalwareDevicesIps().stream()
                    .filter(ip -> !isMobileClient(ip) || openVpnServerActive)
                    .forEach(ipAddress ->
                            preRouting.rule("-i %s -s %s -p tcp -m set --match-set %s dst,dst -j DNAT --to-destination %s:3128",
                                    evaluateInterfaceName(ipAddress), ipAddress, malwareIpSetName, selectTargetIpAddress(ipAddress)));
        }

        // nat local traffic to dns-server
        output.rule("-o lo -p udp -s 127.0.0.1 -d 127.0.0.1 -m udp --dport 53 -j DNAT --to-destination 127.0.0.1:5300");

        // nat local traffic to default ports
        output.rule("-o lo -p tcp -m tcp --dport 80 -j DNAT --to-destination 127.0.0.1:%s", httpPort);
        output.rule("-o lo -p tcp -m tcp --dport 443 -j DNAT --to-destination 127.0.0.1:%s", httpsPort);

        // use redsocks for all outgoing traffic from special source ip
        output.rule("-o %s -s %s -p tcp -j DNAT --to-destination %s:%d", interfaceName, anonSourceIp, ownIpAddress, anonSocksPort);

        // masquerading
        if (masqueradeEnabled) {
            postRouting.rule("-o %s -j MASQUERADE", interfaceName);
        }

        // Enable masquerading for vpn clients if eBlocker mobile feature is enabled
        if (openVpnServerActive) {
            ipAddressFilter.getVpnClientDevicesIps()
                    .forEach(ip -> {
                        if (IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), vpnSubnet, vpnNetmask)) {
                            postRouting.rule("-s %s -o %s -j MASQUERADE", ip, interfaceName);
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
            input.rule("-i %s -p tcp -m state --state NEW -m multiport ! --dports 22,80,443,%s -j DROP", interfaceName, vpnServerPort);
            input.rule("-i %s -p udp -m state --state NEW -m multiport ! --dports 22,80,443,%s -j DROP", interfaceName, vpnServerPort);
            input.rule("-i %s -p tcp -m tcp --dport %s -j RETURN", vpnInterfaceName, httpPort);
            input.rule("-i %s -p tcp -m tcp --dport %s -j RETURN", vpnInterfaceName, proxyHTTPSPort);
        }

        // allow some mobile clients access to local networks
        if (openVpnServerActive) {
            ipAddressFilter.getVpnClientDevicesPrivateNetworkAccessIps().stream()
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
        ipAddressFilter.getTorDevicesIps()
                .forEach(ip -> forward.rule("-i %s -s %s -j REJECT", interfaceName, ip));

        // Parental Control: Drop all non-http(s) packets and interrupt connections redirected to squid
        ipAddressFilter.getAccessRestrictedIps().forEach(ip -> {
            String interfaceName = evaluateInterfaceName(ip);
            forward.rule("-i %s -s %s -j DROP", interfaceName, ip);
            input
                    .rule("-i %s -s %s -p tcp -m tcp --dport 3128 -j REJECT", interfaceName, ip)
                    .rule("-i %s -s %s -p tcp -m tcp --dport 3130 -j REJECT", interfaceName, ip);
        });

        // drop all non http/https connections on access denied ip
        if (dnsEnabled) {
            input
                    .rule("-d %s -p tcp -m tcp --dport %d -j ACCEPT", dnsAccessDeniedIp, parentalControlRedirectHttpPort)
                    .rule("-d %s -p tcp -m tcp --dport %d -j ACCEPT", dnsAccessDeniedIp, parentalControlRedirectHttpsPort)
                    .rule("-d %s -j DROP", dnsAccessDeniedIp);
        }
    }

    private void addMangleRules() {
        //create vpn-routing or decision chain
        Chain vpnRoutingChain = mangleTable.chain("vpn-router");

        final String jumpToVpnTable = "-j " + vpnRoutingChain.getName();
        mangleTable.chain("OUTPUT").rule(jumpToVpnTable);
        mangleTable.chain("PREROUTING").rule(jumpToVpnTable);

        //do not route packets from the default gateway through any vpn tunnel -> FIXME this part has to be kept up-to-date, when the gateway IP address changes
        if (gatewayIpAddress != null) {
            vpnRoutingChain.rule("-s %s -j RETURN", gatewayIpAddress);
        }

        //do not route packets for the localnet through the VPN tunnels
        final String localnetString = IpUtils.getSubnet(ownIpAddress, networkMask);
        vpnRoutingChain.rule("-d %s -j RETURN", localnetString);

        List<String> enabledDevicesIps = ipAddressFilter.getEnabledDevicesIps();

        // traffic account incoming
        mangleTable.chain("ACCOUNT-IN")
                // exclude private networks
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassA)
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassB)
                .rule("-i %s -d %s -j RETURN", interfaceName, ipRangeClassC)
                // exclude multi cast ips
                .rule("-i %s -d %s -j RETURN", interfaceName, "224.0.0.0/4")
                .rule("-i %s -d %s -j RETURN", interfaceName, "240.0.0.0/4");

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-IN").rule("-i %s -s %s -j RETURN", evaluateInterfaceName(ip), ip));

        // traffic account outgoing
        mangleTable.chain("ACCOUNT-OUT")
                // do not account traffic from local networks (but account traffic from squid as this is seen in this chain as coming from local host)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassA, squidUid)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassB, squidUid)
                .rule("-o %s -s %s -m owner ! --uid-owner %d -j RETURN", interfaceName, ipRangeClassC, squidUid);

        enabledDevicesIps.forEach(ip -> mangleTable.chain("ACCOUNT-OUT").rule("-o %s -d %s -j RETURN", evaluateInterfaceName(ip), ip));

        mangleTable.chain("PREROUTING").rule("-j ACCOUNT-IN");
        mangleTable.chain("POSTROUTING").rule("-j ACCOUNT-OUT");
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
