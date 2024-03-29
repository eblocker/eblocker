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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

public class DhcpBindListenerTest {

    private static final String SCRIPT_PATH = "/tmp/dhclient.sh";

    private DataSource dataSource;
    private EblockerDnsServer eblockerDnsServer;
    private NetworkInterfaceWrapper networkInterfaceWrapper;
    private PubSubService pubSubService;

    private DhcpBindListener dhcpBindListener;
    private DhcpBindListener.Listener listener;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);
        pubSubService = Mockito.mock(PubSubService.class);

        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterfaceWrapper.getInterfaceName()).thenReturn("eth1");

        dhcpBindListener = new DhcpBindListener(SCRIPT_PATH, pubSubService, networkInterfaceWrapper, dataSource);

        listener = Mockito.mock(DhcpBindListener.Listener.class);
        dhcpBindListener.addListener(listener);
    }

    @Test
    public void dhcpChange() {
        dhcpBindListener.process("eth1 192.168.1.23 192.168.1.1 8.8.8.8,8.8.4.4");
        Mockito.verify(networkInterfaceWrapper).notifyIPAddressChanged(Ip4Address.parse("192.168.1.23"));
        Mockito.verify(dataSource).setGateway("192.168.1.1");
        Mockito.verify(listener).updateDhcpNameServers(Arrays.asList("8.8.8.8", "8.8.4.4"));
    }

    @Test
    public void dhcpChangeUnknownInterface() {
        dhcpBindListener.process("eth0 192.168.1.23 192.168.1.1 8.8.8.8,8.8.4.4");
        Mockito.verify(networkInterfaceWrapper, Mockito.never()).notifyIPAddressChanged(Mockito.any(Ip4Address.class));
        Mockito.verifyNoInteractions(dataSource);
        Mockito.verifyNoInteractions(eblockerDnsServer);
    }

    // EB-1877: router reports 0.0.0.0 as secondary dns server
    @Test
    public void dnsServerAllZero() {
        dhcpBindListener.process("eth1 192.168.1.23 192.168.1.1 8.8.8.8,0.0.0.0");
        Mockito.verify(networkInterfaceWrapper).notifyIPAddressChanged(Ip4Address.parse("192.168.1.23"));
        Mockito.verify(dataSource).setGateway("192.168.1.1");
        Mockito.verify(listener).updateDhcpNameServers(Collections.singletonList("8.8.8.8"));
    }

    @Test
    public void noMessage() {
        dhcpBindListener.process(null);
        Mockito.verifyNoInteractions(networkInterfaceWrapper);
        Mockito.verifyNoInteractions(dataSource);
        Mockito.verifyNoInteractions(eblockerDnsServer);
    }

    @Test
    public void messageFormatError() {
        dhcpBindListener.process("eth0 192.168.1.23 192.168.1.1");
        Mockito.verifyNoInteractions(networkInterfaceWrapper);
        Mockito.verifyNoInteractions(dataSource);
        Mockito.verifyNoInteractions(eblockerDnsServer);
    }

    @Test
    public void run() {
        dhcpBindListener.run();
        Mockito.verify(pubSubService).subscribeAndLoop(Channels.DHCP_IP_IN, dhcpBindListener);
    }

}
