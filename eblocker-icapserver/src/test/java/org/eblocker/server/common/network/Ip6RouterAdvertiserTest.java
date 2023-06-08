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

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisementFactory;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

public class Ip6RouterAdvertiserTest {

    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterface;
    private PubSubService pubSubService;
    private Ip6RouterAdvertiser advertiser;

    @Before
    public void setUp() {
        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[]{ 0, 1, 2, 3, 4, 5 });
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::1:2:3:4:5"));
        Mockito.when(networkInterface.getMtu()).thenReturn(1500);
        pubSubService = Mockito.mock(PubSubService.class);
        RouterAdvertisementFactory routerAdvertisementFactory = new RouterAdvertisementFactory(RouterAdvertisement.RouterPreference.HIGH, 120, 120, networkInterface);
        advertiser = new Ip6RouterAdvertiser(featureToggleRouter, networkInterface, pubSubService, routerAdvertisementFactory);
    }

    @Test
    public void testAdvertisement() {
        Mockito.when(networkInterface.isUp()).thenReturn(true);
        Mockito.when(featureToggleRouter.shouldSendRouterAdvertisements()).thenReturn(true);
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"),
                Ip4Address.parse("10.12.34.5"), Ip6Address.parse("2000::1:2:3:4:5")));
        advertiser.advertise();
        Mockito.verify(pubSubService).publish("ip6:out", "000102030405/fe800000000000010002000300040005/333300000001/ff020000000000000000000000000001/icmp6/134/255/0/0/0/1/120/0/0/1/000102030405/25/120/1/fe800000000000010002000300040005/5/1500");
    }

    @Test
    public void testAdvertisementsOrIp6Disabled() {
        Mockito.when(networkInterface.isUp()).thenReturn(true);
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"),
                Ip4Address.parse("10.12.34.5"), Ip6Address.parse("2000::1:2:3:4:5")));
        advertiser.advertise();
        Mockito.verify(pubSubService, Mockito.never()).publish(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testAdvertisementNoGlobalAddress() {
        Mockito.when(networkInterface.isUp()).thenReturn(true);
        Mockito.when(featureToggleRouter.shouldSendRouterAdvertisements()).thenReturn(true);
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"),
                Ip4Address.parse("10.12.34.5")));
        advertiser.advertise();
        Mockito.verify(pubSubService, Mockito.never()).publish(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testAdvertisementNetworkInterfaceDown() {
        Mockito.when(networkInterface.isUp()).thenReturn(false);
        Mockito.when(featureToggleRouter.shouldSendRouterAdvertisements()).thenReturn(true);
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"),
                Ip4Address.parse("10.12.34.5"), Ip6Address.parse("2000::1:2:3:4:5")));
        advertiser.advertise();
        Mockito.verify(pubSubService, Mockito.never()).publish(Mockito.anyString(), Mockito.anyString());
    }
}
