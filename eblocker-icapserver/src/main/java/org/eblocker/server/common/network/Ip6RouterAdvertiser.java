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
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.MtuOption;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisementFactory;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip6Utils;

import java.util.Arrays;
import java.util.Collections;

@Singleton
public class Ip6RouterAdvertiser {

    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final RouterAdvertisementFactory routerAdvertisementFactory;

    @Inject
    public Ip6RouterAdvertiser(FeatureToggleRouter featureToggleRouter,
                               NetworkInterfaceWrapper networkInterface,
                               PubSubService pubSubService,
                               RouterAdvertisementFactory routerAdvertisementFactory) {
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
        this.routerAdvertisementFactory = routerAdvertisementFactory;
    }

    public void advertise() {
        if (!featureToggleRouter.shouldSendRouterAdvertisements()) {
            return;
        }

        if (!networkInterface.isUp()) {
            return;
        }

        if (networkInterface.getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .allMatch(ip -> Ip6Utils.isLinkLocal((Ip6Address) ip))) {
            return;
        }

        RouterAdvertisement advertisement = routerAdvertisementFactory.create(
                networkInterface.getHardwareAddress(),
                networkInterface.getIp6LinkLocalAddress(),
                RouterAdvertisementFactory.MULTICAST_ALL_NODES_HW_ADDRESS,
                Ip6Address.MULTICAST_ALL_NODES_ADDRESS);
        pubSubService.publish(Channels.IP6_OUT, advertisement.toString());
    }

}
