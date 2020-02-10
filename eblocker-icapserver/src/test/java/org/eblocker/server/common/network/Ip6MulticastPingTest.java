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

import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Ip6MulticastPingTest {

    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterface;
    private TestPubSubService pubSubService;
    private Random random;
    private Ip6MulticastPing ip6MulticastPing;

    @Before
    public void setUp() {
        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);

        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[] { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::1234"));

        random = Mockito.mock(Random.class);
        Mockito.when(random.nextInt(65536)).thenReturn(12345);
        AtomicInteger rndBytesCalls = new AtomicInteger();
        Mockito.doAnswer(im -> {
            byte[] bytes = im.getArgument(0);
            Assert.assertEquals(32, bytes.length);
            int calls = rndBytesCalls.getAndIncrement();
            bytes[28] = (byte)(calls >> 24 & 0xff);
            bytes[29] = (byte)(calls >> 16 & 0xff);
            bytes[30] = (byte)(calls >> 8 & 0xff);
            bytes[31] = (byte)(calls & 0xff);
            return null;
        }).when(random).nextBytes(Mockito.any(byte[].class));

        pubSubService = new TestPubSubService();

        ip6MulticastPing = new Ip6MulticastPing(featureToggleRouter, networkInterface, pubSubService, random);
    }

    @Test
    public void testPing() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        for(int i = 0; i <= 65536; ++i) {
            ip6MulticastPing.ping();
        }
        Assert.assertEquals(65537, pubSubService.getMessages("ip6:out").size());
        for(int i = 0; i < 65536; ++i) {
            String expectedMessage = String.format("001122334455/fe800000000000000000000000001234/333300000001/ff020000000000000000000000000001/icmp6/128/12345/%d/0000000000000000000000000000000000000000000000000000000000%06x", i, i);
            Assert.assertEquals(expectedMessage, pubSubService.getMessages("ip6:out").get(i));
        }

        Assert.assertEquals("001122334455/fe800000000000000000000000001234/333300000001/ff020000000000000000000000000001/icmp6/128/12345/0/0000000000000000000000000000000000000000000000000000000000010000", pubSubService.getMessages("ip6:out").get(65536));
    }

    @Test
    public void testFeatureDisabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);
        for(int i = 0; i <= 65536; ++i) {
            ip6MulticastPing.ping();
        }
        Assert.assertEquals(0, pubSubService.getMessages("ip6:out").size());
    }

    /**
     * Custom mock for PubSubService recording messages sent to each channel to avoid use of a slow mockito mock (try calling it 65536 times :)
     */
    private static class TestPubSubService implements PubSubService {
        private Map<String, List<String>> messagesByChannel = new HashMap<>();

        @Override
        public void publish(String channel, String message) {
            messagesByChannel.computeIfAbsent(channel, key -> new ArrayList<>()).add(message);
        }

        @Override
        public void subscribeAndLoop(String channel, Subscriber subscriber) {
            Assert.fail("must not subscribe!");
        }

        @Override
        public void unsubscribe(Subscriber subscriber) {
            Assert.fail("must not unsubscribe!");
        }

        List<String> getMessages(String channel) {
            return messagesByChannel.getOrDefault(channel, Collections.emptyList());
        }
    }
}
