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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.Ip6Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Network utilities
 */
@Singleton
@SubSystemService(value = SubSystem.HTTP_SERVER, initPriority = -1000)
public class NetworkInterfaceWrapper {
    private static final Logger log = LoggerFactory.getLogger(NetworkInterfaceWrapper.class);

    private final NetworkInterfaceFactory networkInterfaceFactory;
    private NetworkInterface networkInterface;
    private final ScriptRunner scriptRunner;
    private final String findGatewayScript;
    private Ip4Address emergencyIp;

    private Ip4Address firstIPv4Address;
    private final String interfaceName;
    private final String vpnInterfaceName;

    private List<IpAddressChangeListener> ipAddressChangeListeners = new ArrayList<>();
    private boolean waitingForIPv4Address = false;

    @Inject
    public NetworkInterfaceWrapper(NetworkInterfaceFactory networkInterfaceFactory,
                                   @Named("network.interface.name") String interfaceName,
                                   @Named("network.emergency.ip") Ip4Address emergencyIp,
                                   @Named("find.gateway.command") String findGatewayScript,
                                   @Named("network.vpn.interface.name") String vpnInterfaceName,
                                   ScriptRunner scriptRunner) {
        this.networkInterfaceFactory = networkInterfaceFactory;
        this.interfaceName = interfaceName;
        this.emergencyIp = emergencyIp;
        this.scriptRunner = scriptRunner;
        this.findGatewayScript = findGatewayScript;
        this.vpnInterfaceName = vpnInterfaceName;
    }

    @SubSystemInit
    public void init() {
        try {
            networkInterface = networkInterfaceFactory.getNetworkInterfaceByName(interfaceName);

            if (networkInterface == null) {
                log.warn("Could not find a network interface named " + interfaceName);
            }

            firstIPv4Address = readFirstIPv4Address();

            if (firstIPv4Address == null) {
                log.warn("Using the emergency address! The ethernet interface {} does not have an IPv4 address assigned (seems to be in automatic mode), waiting for DHCP Bind Event...", interfaceName);
                waitingForIPv4Address = true;
            }

        } catch (IOException e) {
            throw new EblockerException("Could not get network interface named " + interfaceName, e);
        }

    }

    /**
     * Returns the primary IPv4 IP address
     */
    public Ip4Address getFirstIPv4Address() {
        if (!waitingForIPv4Address && firstIPv4Address != null) {//IPv4 address was assigned already
            log.debug("Telling other objects this IP address: {}", firstIPv4Address);
            return firstIPv4Address;
        } else {//no IPv4 address assigned to the ethernet interface yet, so return emergency address until we get a new
            // IPv4 address assigned by DHCP
            log.debug("No IPv4 address assigned to ethernet interface {} yet...returning emergency IP address instead.", interfaceName);
            return emergencyIp;
        }
    }

    /**
     * This method actually updates the current IPv4 address from the network interface;
     * Call this method, if e.g. the device got a new IPv4 address via DHCP in automatic mode.
     */
    public void notifyIPAddressChanged(Ip4Address newIP) {
        //Dont reread the IP, just use the message that came from the script

        log.info("The network interface {} got an new IP address assigned via DHCP: {}", interfaceName, newIP);

        firstIPv4Address = newIP;

        //reload NetworkInterface, otherwise it will stay in old state
        try {
            networkInterface = networkInterfaceFactory.getNetworkInterfaceByName(interfaceName);
            if (waitingForIPv4Address) {
                log.warn("The network interface {} finally got an IPv4 address {}, not returning the emergency IP anymore...", interfaceName, newIP);
            }
            waitingForIPv4Address = false;
        } catch (SocketException e) {
            log.error("Error while reloading the network interface {} : {}", interfaceName, e.toString(), e);
        }
        notifyIpAddressChangeListeners(newIP);
    }

    /**
     * Returns the primary IPv4 IP address assigned the network interface
     *
     * @return IPv4 address -> null if the IPv4 Address could not be found
     */
    private Ip4Address readFirstIPv4Address() {
        List<Ip4Address> assignedIps = getAddresses().stream()
                .filter(IpAddress::isIpv4)
                .map(ip -> (Ip4Address) ip)
                .collect(Collectors.toList());
        if (assignedIps.isEmpty()) {
            log.error("Could not find primary IPv4 address");
            return null;
        }
        if (assignedIps.size() > 1) {
            log.warn("More than one IPv4 address ({}). Returning the first one.", assignedIps);
        }
        return assignedIps.get(0);
    }

    public Ip6Address getIp6LinkLocalAddress() {
        return getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .map(ip -> (Ip6Address) ip)
                .filter(Ip6Utils::isLinkLocal)
                .findFirst()
                .orElse(null);
    }

    public List<IpAddress> getAddresses() {
        if (networkInterface == null) {
            return Collections.emptyList();
        }

        // collect all addresses which belongs to sub-interfaces
        Set<InetAddress> subInterfaceAddresses = networkInterface.subInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .collect(Collectors.toSet());

        // get all addresses which are assigned to the primary interface
        List<IpAddress> assignedIps = networkInterface.inetAddresses()
                .filter(ip -> !subInterfaceAddresses.contains(ip))
                .map(IpAddress::of)
                .collect(Collectors.toList());

        if (assignedIps.isEmpty()) {
            log.warn("could not find any ip assigned to the primary interface");
            return Collections.emptyList();
        }

        return assignedIps;
    }

    public boolean hasGlobalIp6Address() {
        if (networkInterface == null) {
            return false;
        }
        return networkInterface.inetAddresses()
                .filter(address -> address instanceof Inet6Address)
                .anyMatch(address -> !address.isLinkLocalAddress() && !address.isSiteLocalAddress());
    }

    /**
     * Returns the network prefix length (in bits) of the given IP address
     *
     * @param ipAddress
     * @return -1 if there was no networkInterface
     */
    public int getNetworkPrefixLength(IpAddress ipAddress) {
        if (networkInterface == null) {
            return -1;
        }

        return networkInterface.getInterfaceAddresses().stream()
                .filter(address -> Arrays.equals(address.getAddress().getAddress(), ipAddress.getAddress()))
                .map(InterfaceAddress::getNetworkPrefixLength)
                .findFirst()
                .orElse((short) -1);
    }

    public byte[] getHardwareAddress() {
        if (networkInterface != null) {
            try {
                return networkInterface.getHardwareAddress();
            } catch (IOException e) {
                throw new EblockerException("Failed to get hardware address for network interface " + networkInterface.getName(), e);
            }
        }
        return null;
    }

    public String getHardwareAddressHex() {
        byte[] mac = getHardwareAddress();
        if (mac != null) {
            return String.format("%02x%02x%02x%02x%02x%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
        } else {
            return null;
        }
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    /**
     * Look for a gateway, and if found write it to Redis (script)
     */
    public void findGatewayAndWriteToRedis() {
        try {
            scriptRunner.runScript(findGatewayScript);
        } catch (IOException e) {
            log.error("Error while starting the script {}", e);
        } catch (InterruptedException e) {
            log.error("Error while starting the script {}", e);
            Thread.currentThread().interrupt();
        }
    }

    public boolean isUp() {
        if (networkInterface == null) {
            return false;
        }

        try {
            return networkInterface.isUp();
        } catch (SocketException e) {
            log.error("Cannot figure out if interface is up, assuming down state", e);
            return false;
        }
    }

    public Ip4Address getVpnIpv4Address() {
        try {
            NetworkInterface vpnNetworkInterface = networkInterfaceFactory.getNetworkInterfaceByName(vpnInterfaceName);
            if (vpnNetworkInterface == null) {
                log.debug("No vpn network interface present");
                return null;
            }

            return vpnNetworkInterface.inetAddresses()
                    .map(IpAddress::of)
                    .filter(addr -> addr instanceof Ip4Address)
                    .map(addr -> (Ip4Address)addr)
                    .findFirst()
                    .orElse(null);
        } catch (SocketException e) {
            throw new EblockerException("Could not get ip address of VPN interface " + vpnInterfaceName, e);
        }
    }

    public int getMtu() {
        if (networkInterface == null) {
            return -1;
        }
        try {
            return networkInterface.getMTU();
        } catch (SocketException e) {
            throw new EblockerException("error getting mtu", e);
        }
    }

    public void addIpAddressChangeListener(IpAddressChangeListener listener) {
        ipAddressChangeListeners.add(listener);
    }

    private void notifyIpAddressChangeListeners(IpAddress newIp) {
        ipAddressChangeListeners.forEach(l -> l.onIpAddressChange(newIp));
    }

    public interface IpAddressChangeListener {
        void onIpAddressChange(IpAddress newIp);
    }

}
