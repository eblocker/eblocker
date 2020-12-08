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

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configures ethernet (and loopback) interfaces.
 */
public class NetworkInterfaceConfiguration {
    private final String interfacesConfigPath;
    private final String interfaceName;
    private final String emergencyIp;
    private final String emergencyNetmask;
    private final String anonIp;
    private final String anonIpMask;

    private interface InterfaceConfig {
        void append(BufferedWriter writer) throws IOException;
    }

    @Inject
    public NetworkInterfaceConfiguration(@Named("network.unix.interfaces.config.path") String interfacesConfigPath,
                                         @Named("network.interface.name") String interfaceName,
                                         @Named("network.emergency.ip") String emergencyIp,
                                         @Named("network.emergency.netmask") String emergencyNetmask,
                                         @Named("network.unix.anon.source.ip") String anonIpWithRange,
                                         @Named("network.unix.anon.source.netmask") String anonIpMask) {
        this.interfacesConfigPath = interfacesConfigPath;
        this.interfaceName = interfaceName;
        this.emergencyIp = emergencyIp;
        this.emergencyNetmask = emergencyNetmask;
        this.anonIp = anonIpWithRange.substring(0, anonIpWithRange.indexOf('/'));
        this.anonIpMask = anonIpMask;
    }


    public void enableDhcp() throws IOException {
        writeConfiguration((BufferedWriter writer) -> {
            writer.append("dhcp");
            writer.newLine();
        });
    }

    public void enableStatic(String ipAddress, String networkMask, String gateway) throws IOException {
        writeConfiguration((BufferedWriter writer) -> {
            writer.append("static");
            writer.newLine();

            writer.append("\taddress ");
            writer.append(ipAddress);
            writer.newLine();

            writer.append("\tnetmask ");
            writer.append(networkMask);
            writer.newLine();

            writer.append("\tgateway ");
            writer.append(gateway);
            writer.newLine();
        });
    }


    private void writeConfiguration(InterfaceConfig config) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(interfacesConfigPath))) {
            writer.append("auto lo");
            writer.newLine();
            writer.append("iface lo inet loopback");
            writer.newLine();
            writer.newLine();
            writer.append("auto ");
            writer.append(interfaceName);
            writer.newLine();
            writer.append("allow-hotplug ");
            writer.append(interfaceName);
            writer.newLine();
            writer.append("iface ");
            writer.append(interfaceName);
            writer.append(" inet ");
            config.append(writer);
            writer.newLine();

            // add emergency IP:
            writeLinkLocalAddress(interfaceName, ":0", emergencyIp, emergencyNetmask, writer);

            writer.newLine();

            // add anonymous IP
            writeLinkLocalAddress(interfaceName, ":1", anonIp, anonIpMask, writer);
        }
    }

    private void writeLinkLocalAddress(String interfaceName, String devSuffix,
                                       String linkLocalIp, String netmask, BufferedWriter writer) throws IOException {
        writer.append("auto ");
        writer.append(interfaceName);
        writer.append(devSuffix);
        writer.newLine();
        writer.append("allow-hotplug ");
        writer.append(interfaceName);
        writer.append(devSuffix);
        writer.newLine();
        writer.append("iface ");
        writer.append(interfaceName);
        writer.append(devSuffix);
        writer.append(" inet static");
        writer.newLine();
        writer.append("\taddress ");
        writer.append(linkLocalIp);
        writer.newLine();
        writer.append("\tnetmask ");
        writer.append(netmask);
        writer.newLine();
    }
}
