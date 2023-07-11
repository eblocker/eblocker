/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.icmpv6;

import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.RouterAdvertisementCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class RouterAdvertisementFactoryTest {
    private RouterAdvertisementFactory factory;
    private NetworkInterfaceWrapper networkInterface;
    private byte[] localHwAddr = new byte[] {1, 2, 3, 4, 5, 6};
    private Ip6Address localIpAddr = Ip6Address.parse("fe80::1:2:3:4");
    private RouterAdvertisementCache routerAdvertisementCache;
    private Clock clock;
    private ScheduledExecutorService executor;

    @Before
    public void setUp() {
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(localHwAddr);
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(localIpAddr);
        Mockito.when(networkInterface.getMtu()).thenReturn(1500);
        clock = Mockito.mock(Clock.class);
        executor = Mockito.mock(ScheduledExecutorService.class);
        routerAdvertisementCache = new RouterAdvertisementCache(clock, executor);
        factory = new RouterAdvertisementFactory(RouterAdvertisement.RouterPreference.HIGH, 600, 300, routerAdvertisementCache, networkInterface);
    }

    @Test
    public void testDefaultMtu() {
        byte[] targetHwAddr = new byte[] {9, 8, 7, 6, 5, 4};
        Ip6Address targetIpAddr = Ip6Address.parse("fe80::9:8:7:6");

        RouterAdvertisement result = factory.create(localHwAddr, localIpAddr, targetHwAddr, targetIpAddr);

        Assert.assertEquals(1500L, getMtu(result));
    }

    @Test
    public void testMinMtuFromMainRouter() {
        byte[] targetHwAddr = new byte[] {9, 8, 7, 6, 5, 4};
        Ip6Address targetIpAddr = Ip6Address.parse("fe80::9:8:7:6");

        routerAdvertisementCache.addEntry(createMainRouterAdvertisement(new byte[] {10, 11, 12, 13, 14, 15}, Ip6Address.parse("fe80::a:b:c:d"), 1492));  // 8 bytes less because of PPPoE
        routerAdvertisementCache.addEntry(createMainRouterAdvertisement(new byte[] {17, 18, 19, 20, 21, 22}, Ip6Address.parse("fe80::e:f:0:1"), 1500));  // some other router
        RouterAdvertisement result = factory.create(localHwAddr, localIpAddr, targetHwAddr, targetIpAddr);

        Assert.assertEquals(1492L, getMtu(result));
    }

    private long getMtu(RouterAdvertisement advertisement) {
        return advertisement.getOptions().stream()
                .filter(opt -> opt instanceof MtuOption)
                .map(opt -> (MtuOption)opt)
                .findFirst()
                .map(opt -> opt.getMtu())
                .get();
    }

    private RouterAdvertisement createMainRouterAdvertisement(byte[] routerHwAddr, Ip6Address routerIpAddr, int mtu) {
        List<Option> options = List.of(
                new SourceLinkLayerAddressOption(routerHwAddr),
                new RecursiveDnsServerOption(300, Collections.singletonList(networkInterface.getIp6LinkLocalAddress())),
                new MtuOption(mtu)
        );
        return new RouterAdvertisement(routerHwAddr, routerIpAddr,
                RouterAdvertisementFactory.MULTICAST_ALL_NODES_HW_ADDRESS, Ip6Address.MULTICAST_ALL_NODES_ADDRESS,
                (short) 255, false, false, false,
                RouterAdvertisement.RouterPreference.MEDIUM, 600, 0, 0, options);

    }
}
