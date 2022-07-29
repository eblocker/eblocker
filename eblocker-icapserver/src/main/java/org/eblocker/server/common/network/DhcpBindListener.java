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

import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.data.validation.NetworkConfigurationValidator;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class listens for messages on a Redis channel, that announce a new IP address, which was assigned to networkInterfaces via DHCP.
 * When a message is received it will notify the specific NetworkInterfaceWrapper; for now we just have one NetworkInterfaceWrapper for 'eth0',
 * but the script is able to tell which interface got a new IP address via DHCP by the message format : '[InterfaceName] [new IP address]'
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class DhcpBindListener implements Subscriber, Runnable {
    private static final Logger log = LoggerFactory.getLogger(DhcpBindListener.class);

    private final String scriptPath;
    private final PubSubService pubSubService;
    private final NetworkInterfaceWrapper networkInterface;
    private final DataSource dataSource;
    private final List<Listener> listeners;

    @Inject
    public DhcpBindListener(@Named("network.unix.dhcp.dhclientHook.path") String scriptPath,
                            PubSubService pubSubService,
                            NetworkInterfaceWrapper networkInterface,
                            DataSource dataSource) {
        this.scriptPath = scriptPath;
        this.pubSubService = pubSubService;
        this.networkInterface = networkInterface;
        this.dataSource = dataSource;
        this.listeners = new ArrayList<>();
    }

    public void init() {
        if (!new File(scriptPath).exists()) {
            log.error("The 'DHCP update IP address' script is missing: {}", scriptPath);
        }
    }

    @Override
    public void process(String newIpAddressMessage) {
        log.info("Received this string from the Redis {} channel: {}", Channels.DHCP_IP_IN, newIpAddressMessage);
        if (newIpAddressMessage == null) {
            log.error("no message received");
            return;
        }

        String[] parts = newIpAddressMessage.split(" ");
        if (parts.length != 4) {
            log.error("Error while parsing message from DHCP listener script!: {}", newIpAddressMessage);
            return;
        }

        String interfaceName = parts[0];
        if (!networkInterface.getInterfaceName().equals(interfaceName)) {
            log.error("Unexpected interface: {}", newIpAddressMessage);
            return;
        }

        updateGateway(parts[2]);
        updateDnsServers(parts[3]);
        updateIpAddress(parts[1]);
    }

    @Override
    public void run() {
        log.info("Subscribing to Redis channel {}", Channels.DHCP_IP_IN);
        pubSubService.subscribeAndLoop(Channels.DHCP_IP_IN, this);
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            this.listeners.add(listener);
        }
    }

    private void updateIpAddress(String ipAddress) {
        if (NetworkConfigurationValidator.isValidIPv4Address(ipAddress)) {
            networkInterface.notifyIPAddressChanged(Ip4Address.parse(ipAddress));
        }
    }

    private void updateGateway(String routers) {
        Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
        List<String> newRouters = splitter.splitToList(routers);
        if (NetworkConfigurationValidator.isValidIPv4Address(newRouters.get(0))) {
            dataSource.setGateway(newRouters.get(0));
        }
    }

    private void updateDnsServers(String dnsServersOption) {
        List<String> nameServers = DhcpUtils.parseDnsServersOption(dnsServersOption);
        synchronized (listeners) {
            listeners.forEach(l -> l.updateDhcpNameServers(nameServers));
        }
    }

    public interface Listener {
        void updateDhcpNameServers(List<String> nameServers);
    }
}
