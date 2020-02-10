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
package org.eblocker.server.common.data.messagecenter.provider;

import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LocalDnsIsNotGatewayMessageProviderTest {

    private EblockerDnsServer dnsServer;
    private NetworkServices networkServices;
    private LocalDnsIsNotGatewayMessageProvider provider;

    @Before
    public void setup() {
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        networkServices = Mockito.mock(NetworkServices.class);
        provider = new LocalDnsIsNotGatewayMessageProvider(dnsServer, networkServices);
    }

    @Test
    public void testGetMessageIds() {
        Assert.assertEquals(Collections.singleton(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId()), provider.getMessageIds());
    }

    @Test
    public void testConfigOk() {
        for(int i = 0; i < 16; ++i) {
            if (i == 11) { // skip configuration in which message has to be shown
                continue;
            }

            boolean dnsEnabled = (i & 1) != 0;
            boolean automatic = (i & 2) != 0;
            boolean dhcp = (i & 4) != 0;
            boolean localServers = (i & 8) != 0;
            setupMocks(dnsEnabled, automatic, dhcp, localServers);

            Map<Integer, MessageContainer> messageContainers = new HashMap<>();
            provider.doUpdate(messageContainers);

            Assert.assertTrue(messageContainers.isEmpty());
        }
    }

    @Test
    public void testConfigNotOk() {
        setupMocks(true, true, false, true);

        Map<Integer, MessageContainer> messageContainers = new HashMap<>();
        provider.doUpdate(messageContainers);

        Assert.assertTrue(!messageContainers.isEmpty());
        Assert.assertNotNull(messageContainers.get(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId()));
        Assert.assertNotNull(messageContainers.get(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId()).getMessage());
    }

    @Test
    public void testMessageIsKeptOnReRun() {
        setupMocks(true, true, false, true);

        Map<Integer, MessageContainer> messageContainers = new HashMap<>();
        provider.doUpdate(messageContainers);

        Assert.assertTrue(!messageContainers.isEmpty());

        MessageContainer message = messageContainers.get(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId());
        provider.doUpdate(messageContainers);

        Assert.assertTrue(!messageContainers.isEmpty());
        Assert.assertTrue(message == messageContainers.get(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId()));
    }

    @Test
    public void testMessageRemoval() {
        setupMocks(true, true, false, true);

        Map<Integer, MessageContainer> messageContainers = new HashMap<>();
        provider.doUpdate(messageContainers);

        Assert.assertTrue(!messageContainers.isEmpty());

        setupMocks(true, true, false, false);
        provider.doUpdate(messageContainers);

        Assert.assertTrue(messageContainers.isEmpty());
    }

    private void setupMocks(boolean dnsEnabled, boolean automatic, boolean dhcp, boolean localDnsServers) {
        Mockito.when(dnsServer.isEnabled()).thenReturn(dnsEnabled);
        if (localDnsServers) {
            Mockito.when(dnsServer.getDhcpNameServers()).thenReturn(Arrays.asList("10.10.10.101", "192.168.3.20"));
        } else {
            Mockito.when(dnsServer.getDhcpNameServers()).thenReturn(Arrays.asList("10.10.10.10", "192.168.3.20"));
        }

        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(createNetworkConfiguration(automatic, dhcp, "10.10.10.10", "10.10.10.100", "255.255.255.0"));
    }

    private NetworkConfiguration createNetworkConfiguration(boolean automatic, boolean dhcp, String eblockerIpAddress, String gatewayIpAddress, String networkMask) {
        NetworkConfiguration configuration = new NetworkConfiguration();
        configuration.setAutomatic(automatic);
        configuration.setDhcp(dhcp);
        configuration.setIpAddress(eblockerIpAddress);
        configuration.setGateway(gatewayIpAddress);
        configuration.setNetworkMask(networkMask);
        return configuration;
    }
}