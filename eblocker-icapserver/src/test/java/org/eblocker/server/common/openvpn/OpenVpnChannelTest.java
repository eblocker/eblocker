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
package org.eblocker.server.common.openvpn;

import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.TestPubSubService;
import org.eblocker.server.http.service.EmbeddedRedisServiceTestBase;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class OpenVpnChannelTest extends EmbeddedRedisServiceTestBase {

    @Test(timeout = 5000)
    public void testOpenVpnChannel() throws InterruptedException {
        // Processing messages in a PubSubService is done in another thread so we need
        // to ensure all messages have been processed before checking anything.
        CountDownLatch latch = new CountDownLatch(10);
        PubSubService service = new TestPubSubService() {
            @Override
            public void publish(String channel, String message) {
                super.publish(channel, message);
                latch.countDown();
            }
        };

        // setup listener mock and start channel
        OpenVpnChannelListener listener = Mockito.mock(OpenVpnChannelListener.class);
        OpenVpnChannel channel = new OpenVpnChannel(Executors.newSingleThreadExecutor(), service, 0, listener);
        channel.start();

        // send messages for two clients to the according channels
        String channelNames[] = {
                String.format(Channels.VPN_PROFILE_STATUS_IN, 0),
                String.format(Channels.VPN_PROFILE_STATUS_IN, 1)
        };
        service.publish(channelNames[0], "pid 51723");
        service.publish(channelNames[0], "unknown-message-log-some-error");
        service.publish(channelNames[0], "up tun0 10.10.10.10 10.0.51.1 10.0.51.42 5.79.71.195 8.8.8.8,8.8.4.4");
        service.publish(channelNames[0], "down test");
        service.publish(channelNames[0], "down");
        service.publish(channelNames[1], "pid 23175");
        service.publish(channelNames[0], "unknown-message-log-some-error");
        service.publish(channelNames[1], "up tun0 10.11.10.10 11.0.51.1 11.0.51.42 6.79.71.195");
        service.publish(channelNames[1], "down test2");
        service.publish(channelNames[1], "down");

        // wait until all messages have been processed
        latch.await();
        channel.stop();

        // check listener of client 0 have been called in sequence and with correct parameters
        InOrder inOrder = Mockito.inOrder(listener);
        inOrder.verify(listener).reportPid(51723);
        inOrder.verify(listener).up("tun0", "10.10.10.10", "10.0.51.1", "10.0.51.42", "5.79.71.195", Arrays.asList("8.8.8.8", "8.8.4.4"));
        inOrder.verify(listener).down("test");
        inOrder.verify(listener).down("unknown");

        // check none of the methods for the second client has been called
        inOrder.verifyNoMoreInteractions();
    }
}
