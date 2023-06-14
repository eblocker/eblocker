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

import com.google.inject.Inject;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.network.icmpv6.EchoRequest;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;

import java.util.Random;

/**
 * Sends an echo request to the all-nodes multicast address ff02::1
 * <p>
 * This is used to get addresses of passive clients which mostly will reply with a echo response which will be picked
 * up by {@link NeighborDiscoveryListener}.
 *
 * @see NeighborDiscoveryListener
 */
public class Ip6MulticastPing {

    private static final byte[] MULTICAST_ALL_NODES_HW_ADDRESS = new byte[]{ 51, 51, 0, 0, 0, 1 };

    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final Random random;

    private final int identifier;

    private int sequence;

    @Inject
    public Ip6MulticastPing(FeatureToggleRouter featureToggleRouter, NetworkInterfaceWrapper networkInterface, PubSubService pubSubService, Random random) {
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
        this.random = random;

        identifier = random.nextInt(0x10000);
    }

    public void ping() {
        if (!featureToggleRouter.isIp6Enabled()) {
            return;
        }

        byte[] data = new byte[32];
        random.nextBytes(data);
        EchoRequest echoRequest = new EchoRequest(networkInterface.getHardwareAddress(), networkInterface.getIp6LinkLocalAddress(), MULTICAST_ALL_NODES_HW_ADDRESS, Ip6Address.MULTICAST_ALL_NODES_ADDRESS, identifier, sequence, data);
        String message = echoRequest.toString();
        pubSubService.publish(Channels.IP6_OUT, message);
        sequence = (sequence + 1) & 0xffff;
    }
}
