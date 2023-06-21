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
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.http.service.DeviceOnlineStatusCache;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ArpListenerTest {

    private ArpListener listener;
    private ConcurrentMap<String, Long> arpProbeCache;
    private IpResponseTable ipResponseTable;
    private DeviceOnlineStatusCache deviceOnlineStatusCache;
    private PubSubService pubSubService;
    private NetworkInterfaceWrapper networkInterface;
    private TestClock clock;
    private DeviceIpUpdater deviceIpUpdater;

    protected Subscriber subscriber;

    @Before
    public void setUp() throws Exception {
        deviceOnlineStatusCache = Mockito.mock(DeviceOnlineStatusCache.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddressHex()).thenReturn("caffee012345");
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.0.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("192.168.0.2"))).thenReturn(24);
        clock = new TestClock(ZonedDateTime.now());
        UserService userService = Mockito.mock(UserService.class);

        UserModule user = new UserModule(123456, null, null, null, null, null, true, null, null, null, null, null);
        Mockito.when(userService.restoreDefaultSystemUser(Mockito.any())).thenReturn(user);

        pubSubService = new PubSubService() {
            @Override
            public void subscribeAndLoop(String channel, Subscriber aSubscriber) {
                Assert.assertEquals("arp:in", channel);
                subscriber = aSubscriber;
            }

            @Override
            public void publish(String channel, String message) {
                Assert.fail("publish should never be used by the ArpListener");
            }

            @Override
            public void unsubscribe(Subscriber subscriber) {
                Assert.fail("unsubscribe should never be called by the ArpListener");
            }
        };

        arpProbeCache = new ConcurrentHashMap<>(32, 0.75f, 1);
        ipResponseTable = new IpResponseTable();

        deviceIpUpdater = Mockito.mock(DeviceIpUpdater.class);

        listener = new ArpListener(arpProbeCache, ipResponseTable, deviceOnlineStatusCache, pubSubService, networkInterface, clock, deviceIpUpdater);
    }

    @Test
    public void testInvalidMessage() {
        listener.run();
        subscriber.process("warbl");
        Mockito.verifyNoInteractions(deviceIpUpdater);
    }

    @Test
    public void testResponseMessageRefresh() {
        listener.run();
        subscriber.process(response("012345abcdef", "192.168.0.100"));
        Mockito.verify(deviceIpUpdater).refresh("device:012345abcdef", Ip4Address.parse("192.168.0.100"));
        Mockito.verify(deviceOnlineStatusCache).updateOnlineStatus("device:012345abcdef");
    }

    @Test
    public void testResponseMessageNewDeviceDifferentSubnet() {
        listener.run();
        subscriber.process(response("112345abcdee", "192.168.23.100"));
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.anyString(), Mockito.any());
        Mockito.verify(deviceOnlineStatusCache).updateOnlineStatus("device:112345abcdee");
    }

    @Test
    public void testRequestMessage() {
        listener.run();
        subscriber.process("1/012345abcdef/192.168.0.100/ffffffffffff/192.168.0.1");
        Mockito.verifyNoInteractions(deviceIpUpdater);
    }

    @Test
    public void testGratuitousRequestRefresh() {
        listener.run();
        subscriber.process("1/112345abcdee/192.168.0.100/ffffffffffff/192.168.0.100");
        Mockito.verify(deviceIpUpdater).refresh("device:112345abcdee", Ip4Address.parse("192.168.0.100"));
    }

    @Test
    public void testArpProbe() {
        listener.run();
        subscriber.process("1/012345aaaaaa/0.0.0.0/000000000000/192.168.0.101");

        String deviceId = "device:012345aaaaaa";
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.anyString(), Mockito.any());
        Assert.assertNotNull(arpProbeCache.get(deviceId));
        Assert.assertTrue(Math.abs(System.currentTimeMillis() - arpProbeCache.get(deviceId)) < 1000);
        Assert.assertTrue(ipResponseTable.isEmpty());
    }

    @Test
    public void testBrokenArpRequest() {
        listener.run();
        // ARP messages with both source and target IP set to 0.0.0.0 sometimes occur,
        // see: https://osqa-ask.wireshark.org/questions/5178/why-gratuitous-arps-for-0000
        subscriber.process("1/112345abcdee/0.0.0.0/000000000000/0.0.0.0");

        // make sure we don't process them
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.eq("device:012345aaaaaa"), Mockito.any());
        Assert.assertTrue(arpProbeCache.isEmpty());
        Assert.assertTrue(ipResponseTable.isEmpty());
    }

    @Test
    public void ignoreLocalMessages() {
        listener.run();
        subscriber.process("2/caffee012345/192.168.0.100/abcdef012345/192.168.0.99");
        Mockito.verify(deviceIpUpdater, Mockito.never()).refresh(Mockito.anyString(), Mockito.any());
    }


    @Test
    public void testArpResponseTable() {
        List<Instant> instants = List.of(Instant.ofEpochSecond(1657615000), Instant.ofEpochSecond(1657615001),  Instant.ofEpochSecond(1657615002));

        listener.run();
        clock.setInstant(instants.get(0));
        subscriber.process(response("012345aaaaaa", "192.168.0.22"));
        clock.setInstant(instants.get(1));
        subscriber.process(response("012345aaaaaa", "192.168.0.42"));
        clock.setInstant(instants.get(2));
        subscriber.process(response("012345aaaaaa", "192.168.0.242"));

        Assert.assertEquals(instants.get(0).toEpochMilli(), ipResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.22")).longValue());
        Assert.assertEquals(instants.get(1).toEpochMilli(), ipResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.42")).longValue());
        Assert.assertEquals(instants.get(2).toEpochMilli(), ipResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.242")).longValue());
    }

    private String response(String senderMac, String senderIp) {
        return String.format("2/%s/%s/abcdef012345/192.168.0.99", senderMac, senderIp);
    }
}
