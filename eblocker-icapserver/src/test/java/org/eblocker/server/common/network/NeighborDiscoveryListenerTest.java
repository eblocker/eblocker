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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.MtuOption;
import org.eblocker.server.common.network.icmpv6.PrefixOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.xml.bind.DatatypeConverter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NeighborDiscoveryListenerTest {

    private Table<String, IpAddress, Long> arpResponseTable;
    private TestClock clock;
    private DeviceIpUpdater deviceIpUpdater;
    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterface;
    private RouterAdvertisementCache routerAdvertisementCache;
    private NeighborDiscoveryListener listener;

    private TestPubSubService pubSubService;

    private Subscriber subscriber;

    @Before
    public void setUp() {
        arpResponseTable = HashBasedTable.create();
        clock = new TestClock(ZonedDateTime.now());

        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);

        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[]{ 0, 0, 1, 2, 3, 4 });
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::1:2:3:4"));
        Mockito.when(networkInterface.getMtu()).thenReturn(1500);

        pubSubService = new TestPubSubService();
        routerAdvertisementCache = Mockito.mock(RouterAdvertisementCache.class);

        deviceIpUpdater = Mockito.mock(DeviceIpUpdater.class);

        listener = new NeighborDiscoveryListener(arpResponseTable, clock, deviceIpUpdater, featureToggleRouter, networkInterface, pubSubService, routerAdvertisementCache);
    }

    @Test
    public void testSubscription() {
        listener.run();
        Assert.assertNotNull(subscriber);
    }

    @Test
    public void testNeighborSolicitation() {
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/135/fe800000000000000010001000100010/1/000010101010");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101010", Ip6Address.parse("fe80::10:10:10:10"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101010", IpAddress.parse("fe80::10:10:10:10")));
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testNeighborAdvertisement() {
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/136/1/1/0/fe800000000000000010001000100011");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101010", Ip6Address.parse("fe80::10:10:10:11"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101010", IpAddress.parse("fe80::10:10:10:11")));
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testIcmpEchoRequest() {
        listener.run();
        subscriber.process("000010101020/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/128");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101020", Ip6Address.parse("fe80::10:10:10:10"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101020", IpAddress.parse("fe80::10:10:10:10")));
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testUnspecifiedSourceAddressIcmpEchoResponse() {
        listener.run();
        subscriber.process("000010101020/00000000000000000000000000000000/000010101000/fe800000000000000010001000100000/icmp6/129");
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.anyString(), Mockito.any());
        Assert.assertEquals(0, arpResponseTable.size());
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testRouterSolicitation() {
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"), Ip4Address.parse("10.12.34.5"), Ip6Address.parse("2000::1:2:3:4:5")));
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/133");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101010", Ip6Address.parse("fe80::10:10:10:10"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101010", IpAddress.parse("fe80::10:10:10:10")));
        Assert.assertEquals(1, pubSubService.getPublishedMessages().size());
        Assert.assertEquals("ip6:out", pubSubService.getPublishedMessages().get(0)[0]);
        Assert.assertEquals("000001020304/fe800000000000000001000200030004/000010101010/fe800000000000000010001000100010/icmp6/134/255/0/0/0/1/120/0/0/25/120/1/fe800000000000000001000200030004/1/000001020304/5/1500",
                pubSubService.getPublishedMessages().get(0)[1]);
    }

    @Test
    public void testRouterSolicitationNoGlobalAddress() {
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(Ip6Address.parse("fe80::1:2:3:4:5"), Ip4Address.parse("10.12.34.5")));
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/133");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101010", Ip6Address.parse("fe80::10:10:10:10"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101010", IpAddress.parse("fe80::10:10:10:10")));
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testRouterAdvertisement() {
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/333300000001/ff020000000000000000000000000001/icmp6/134/64/0/1/0/-1/1800/0/0/3/64/1/1/86400/14400/2a02810600216f030000000000000000/1/000010101010/5/1500");
        Mockito.verify(deviceIpUpdater).refresh("device:000010101010", Ip6Address.parse("fe80::10:10:10:10"));
        Assert.assertEquals((Long) clock.millis(), arpResponseTable.get("000010101010", IpAddress.parse("fe80::10:10:10:10")));
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());

        ArgumentCaptor<RouterAdvertisement> captor = ArgumentCaptor.forClass(RouterAdvertisement.class);
        Mockito.verify(routerAdvertisementCache).addEntry(captor.capture());

        RouterAdvertisement advertisement = captor.getValue();
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("000010101010"), advertisement.getSourceHardwareAddress());
        Assert.assertEquals(Ip6Address.of(DatatypeConverter.parseHexBinary("fe800000000000000010001000100010")), advertisement.getSourceAddress());
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("333300000001"), advertisement.getDestinationHardwareAddress());
        Assert.assertEquals(Ip6Address.of(DatatypeConverter.parseHexBinary("ff020000000000000000000000000001")), advertisement.getDestinationAddress());
        Assert.assertEquals(RouterAdvertisement.ICMP_TYPE, advertisement.getIcmpType());
        Assert.assertEquals(64, advertisement.getCurrentHopLimit());
        Assert.assertFalse(advertisement.isManagedAddressConfiguration());
        Assert.assertTrue(advertisement.isOtherConfiguration());
        Assert.assertFalse(advertisement.isHomeAgent());
        Assert.assertEquals(RouterAdvertisement.RouterPreference.LOW, advertisement.getRouterPreference());
        Assert.assertEquals(1800, advertisement.getRouterLifetime());
        Assert.assertEquals(0, advertisement.getReachableTime());
        Assert.assertEquals(0, advertisement.getRetransTimer());
        Assert.assertNotNull(advertisement.getOptions());
        Assert.assertEquals(3, advertisement.getOptions().size());

        Assert.assertEquals(PrefixOption.TYPE, advertisement.getOptions().get(0).getType());
        PrefixOption prefixOption = (PrefixOption) advertisement.getOptions().get(0);
        Assert.assertEquals(64, prefixOption.getPrefixLength());
        Assert.assertTrue(prefixOption.isAutonomousAddressConfiguration());
        Assert.assertTrue(prefixOption.isOnLink());
        Assert.assertEquals(86400, prefixOption.getValidLifetime());
        Assert.assertEquals(14400, prefixOption.getPreferredLifetime());

        Assert.assertEquals(SourceLinkLayerAddressOption.TYPE, advertisement.getOptions().get(1).getType());
        SourceLinkLayerAddressOption sourceLinkLayerAddressOption = (SourceLinkLayerAddressOption) advertisement.getOptions().get(1);
        Assert.assertArrayEquals(DatatypeConverter.parseHexBinary("000010101010"), sourceLinkLayerAddressOption.getHardwareAddress());

        Assert.assertEquals(MtuOption.TYPE, advertisement.getOptions().get(2).getType());
        MtuOption mtuOption = (MtuOption) advertisement.getOptions().get(2);
        Assert.assertEquals(1500, mtuOption.getMtu());
    }

    @Test
    public void testFeatureDisabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);
        listener.run();
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/135/fe800000000000000010001000100010/1/000010101010");
        subscriber.process("000010101010/fe800000000000000010001000100011/000010101000/fe800000000000000010001000100000/icmp6/136/1/1/0/fe800000000000000010001000100010");
        subscriber.process("000010101020/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/128");
        subscriber.process("000010101020/00000000000000000000000000000000/000010101000/fe800000000000000010001000100000/icmp6/129");
        subscriber.process("000010101010/fe800000000000000010001000100010/000010101000/fe800000000000000010001000100000/icmp6/133");
        subscriber.process("000010101010/fe800000000000000010001000100010/333300000001/ff020000000000000000000000000001/icmp6/134/64/0/1/1800/0/0/3/64/1/1/86400/14400/2a02810600216f030000000000000000/1/000010101010");
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.anyString(), Mockito.any());
        Assert.assertTrue(arpResponseTable.isEmpty());
    }

    private class TestPubSubService implements PubSubService {

        private final List<String[]> publishedMessages = new ArrayList<>();

        @Override
        public void subscribeAndLoop(String channel, Subscriber subscriber) {
            Assert.assertEquals("ip6:in", channel);
            NeighborDiscoveryListenerTest.this.subscriber = subscriber;
        }

        @Override
        public void publish(String channel, String message) {
            publishedMessages.add(new String[]{ channel, message });
        }

        @Override
        public void unsubscribe(Subscriber subscriber) {
            Assert.fail("unsubscribe must not be called");
        }

        public List<String[]> getPublishedMessages() {
            return publishedMessages;
        }
    }
}
