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
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class ArpSweeperTest {
    private ArpSweeper sweeper;
    private PubSubServiceMock pubSubService;
    private NetworkInterfaceWrapper networkInterface;

    @Before
    public void setUp() throws Exception {
        pubSubService = new PubSubServiceMock();
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddressHex()).thenReturn("abcdef012345");
        sweeper = new ArpSweeper(1024, pubSubService, networkInterface);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSweep24() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.3.122"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("192.168.3.122"))).thenReturn(24);
        sweeper.fullScan();

        List<String[]> messages = pubSubService.getPublishedMessages();
        Assert.assertEquals(254, messages.size());
        for (int i = 0; i < 254; i++) {
            String message = "1/abcdef012345/192.168.3.122/000000000000/192.168.3." + (i + 1) + "/ffffffffffff";
            Assert.assertEquals(messages.get(i)[0], "arp:out");
            Assert.assertEquals(messages.get(i)[1], message);
        }
    }

    @Test
    public void testSweepLower25() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("10.10.10.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("10.10.10.2"))).thenReturn(25);
        sweeper.fullScan();

        List<String[]> messages = pubSubService.getPublishedMessages();
        Assert.assertEquals(126, messages.size());
        for (int i = 0; i < 126; i++) {
            String message = "1/abcdef012345/10.10.10.2/000000000000/10.10.10." + (i + 1) + "/ffffffffffff";
            Assert.assertEquals(messages.get(i)[0], "arp:out");
            Assert.assertEquals(messages.get(i)[1], message);
        }
    }

    @Test
    public void testSweepUpper25() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("10.10.10.129"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("10.10.10.129"))).thenReturn(25);
        sweeper.fullScan();

        List<String[]> messages = pubSubService.getPublishedMessages();
        Assert.assertEquals(126, messages.size());
        for (int i = 0; i < 126; i++) {
            String message = "1/abcdef012345/10.10.10.129/000000000000/10.10.10." + (i + 129) + "/ffffffffffff";
            Assert.assertEquals(messages.get(i)[0], "arp:out");
            Assert.assertEquals(messages.get(i)[1], message);
        }
    }

    @Test
    public void testSweep16() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("172.16.0.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("172.16.0.2"))).thenReturn(16);

        for (int i = 0; i < 256; ++i) {
            pubSubService.clearPublishedMessages();

            for (int j = 0; j < 256; ++j) {
                sweeper.run();

                List<String[]> messages = pubSubService.getPublishedMessages();
                int expectedIp = -1408237567 + (i * 256 + j) % 65534;
                String message = String.format("1/abcdef012345/172.16.0.2/000000000000/172.16.%d.%d/ffffffffffff",
                    (expectedIp & 0xff00) >> 8, expectedIp & 0xff);
                Assert.assertEquals(messages.get(j)[0], "arp:out");
                Assert.assertEquals(messages.get(j)[1], message);
            }
        }
    }

    @Test
    public void testFullScanNotAvailable() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("172.16.0.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("172.16.0.2"))).thenReturn(16);

        Assert.assertFalse(sweeper.isFullScanAvailable());

        sweeper.fullScan();
        Assert.assertEquals(0, pubSubService.getPublishedMessages().size());
    }

    @Test
    public void testFullScanAvailable() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("172.16.0.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("172.16.0.2"))).thenReturn(22);

        Assert.assertTrue(sweeper.isFullScanAvailable());

        sweeper.fullScan();
        Assert.assertEquals(1022, pubSubService.getPublishedMessages().size());
    }

    /** Custom mock implementation because Mockito's mocks are really really slow even when resetting after 256 calls ... */
    private class PubSubServiceMock implements PubSubService {
        private final List<String[]> publishedMessages = new ArrayList<>();

        @Override
        public void publish(String channel, String message) {
            publishedMessages.add(new String[] { channel, message });
        }

        @Override
        public void subscribeAndLoop(String channel, Subscriber subscriber) {
        }

        @Override
        public void unsubscribe(Subscriber subscriber) {
        }

        public List<String[]> getPublishedMessages() {
            return publishedMessages;
        }

        public void clearPublishedMessages() {
            publishedMessages.clear();
        }
    }
}
