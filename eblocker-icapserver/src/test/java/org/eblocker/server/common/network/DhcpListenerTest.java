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

import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DhcpListenerTest {

    private DhcpListener listener;
    private PubSubService pubSubService;
    private Subscriber subscriber;
    private ConcurrentMap<String, Long> arpProbeCache;

    @Before
    public void setup() {
        pubSubService = new PubSubService() {
            @Override
            public void subscribeAndLoop(String channel, Subscriber aSubscriber) {
                Assert.assertEquals(Channels.DHCP_IN, channel);
                subscriber = aSubscriber;
            }

            @Override
            public void publish(String channel, String message) {
                Assert.fail("publish should never be used by the DhcpListener");
            }

            @Override
            public void unsubscribe(Subscriber subscriber) {
                Assert.fail("unsubscribe should never be called by the DhcpListener");
            }
        };

        arpProbeCache = new ConcurrentHashMap<>(32, 0.75f, 1);
        listener = new DhcpListener(arpProbeCache, pubSubService);
    }

    @Test
    public void testRequest() {
        listener.run();
        subscriber.process("1/001122334455");
        Assert.assertTrue(arpProbeCache.containsKey("device:001122334455"));
        Assert.assertNotNull(arpProbeCache.get("device:001122334455"));
        Assert.assertTrue(System.currentTimeMillis() - arpProbeCache.get("device:001122334455") < 500);
    }

    @Test
    public void testMalformedRequests() {
        listener.run();
        subscriber.process("001122334455");
        Assert.assertTrue(arpProbeCache.isEmpty());
    }
}
