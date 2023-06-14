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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.IpAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Ip6PrefixMonitorTest {
    private Ip6PrefixMonitor monitor;
    private NetworkInterfaceWrapper networkInterface;

    @Before
    public void setUp() {
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getNetworkPrefixLength(Mockito.any())).thenReturn(64);

        monitor = new Ip6PrefixMonitor(networkInterface);
    }

    @Test
    public void noPrefix() {
        setIpAddresses();
        monitor.init();
        Assert.assertEquals(Set.of(), monitor.getCurrentPrefixes());
    }

    @Test
    public void prefixesNotUpdated() {
        setIpAddresses("fe80::1:2:3:4", "2a04:1::", "2a04:2::");
        monitor.init();
        Assert.assertEquals(Set.of("2a04:1::/64", "2a04:2::/64"), monitor.getCurrentPrefixes());

        ArgumentCaptor<NetworkInterfaceWrapper.IpAddressChangeListener> addressChangeListener = ArgumentCaptor.forClass(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        Mockito.verify(networkInterface).addIpAddressChangeListener(addressChangeListener.capture());

        Ip6PrefixMonitor.PrefixChangeListener listener = Mockito.mock(Ip6PrefixMonitor.PrefixChangeListener.class);
        monitor.addPrefixChangeListener(listener);

        setIpAddresses("fe80::5:6:7:8", "2a04:1::", "2a04:2::");
        addressChangeListener.getValue().onIpAddressChange(false, true);
        Mockito.verifyNoInteractions(listener);
        Assert.assertEquals(Set.of("2a04:1::/64", "2a04:2::/64"), monitor.getCurrentPrefixes());
    }

    @Test
    public void prefixesUpdated() {
        setIpAddresses("fe80::1:2:3:4", "2a04:1::", "2a04:2::");
        monitor.init();
        Assert.assertEquals(Set.of("2a04:1::/64", "2a04:2::/64"), monitor.getCurrentPrefixes());

        ArgumentCaptor<NetworkInterfaceWrapper.IpAddressChangeListener> addressChangeListener = ArgumentCaptor.forClass(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        Mockito.verify(networkInterface).addIpAddressChangeListener(addressChangeListener.capture());

        Ip6PrefixMonitor.PrefixChangeListener listener = Mockito.mock(Ip6PrefixMonitor.PrefixChangeListener.class);
        monitor.addPrefixChangeListener(listener);

        setIpAddresses("fe80::1:2:3:4", "2a04:1::", "2a04:3::");
        addressChangeListener.getValue().onIpAddressChange(false, true);
        Mockito.verify(listener).onPrefixChange();
        Assert.assertEquals(Set.of("2a04:1::/64", "2a04:3::/64"), monitor.getCurrentPrefixes());
    }

    private void setIpAddresses(String... addressStrings) {
        List<IpAddress> addresses = Arrays.stream(addressStrings)
                .map(IpAddress::parse)
                .collect(Collectors.toList());
        Mockito.when(networkInterface.getAddresses()).thenReturn(addresses);
    }
}