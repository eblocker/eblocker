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
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip6Utils;

import java.util.Arrays;
import java.util.Collections;

@Singleton
public class Ip6RouterAdvertiser {

    private static final byte[] MULTICAST_ALL_NODES_HW_ADDRESS = new byte[]{51, 51, 0, 0, 0, 1};

    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;

    @Inject
    public Ip6RouterAdvertiser(FeatureToggleRouter featureToggleRouter,
                               NetworkInterfaceWrapper networkInterface,
                               PubSubService pubSubService) {
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
    }

    public void advertise() {
        if (!featureToggleRouter.isIp6Enabled()) {
            return;
        }

        if (!networkInterface.isUp()) {
            return;
        }

        if (networkInterface.getAddresses().stream()
            .filter(IpAddress::isIpv6)
            .allMatch(ip -> Ip6Utils.isInNetwork((Ip6Address) ip, Ip6Address.LINK_LOCAL_NETWORK_ADDRESS, Ip6Address.LINK_LOCAL_NETWORK_PREFIX))) {
            return;
        }

        RouterAdvertisement advertisement = new RouterAdvertisement(
            networkInterface.getHardwareAddress(),
            networkInterface.getIp6LinkLocalAddress(),
            MULTICAST_ALL_NODES_HW_ADDRESS,
            Ip6Address.MULTICAST_ALL_NODES_ADDRESS, (short) 255, false, false, false,
            RouterAdvertisement.RouterPreference.HIGH, 120, 0, 0, Arrays.asList(
            new SourceLinkLayerAddressOption(networkInterface.getHardwareAddress()),
            new RecursiveDnsServerOption(120, Collections.singletonList(networkInterface.getIp6LinkLocalAddress())),
            new MtuOption(networkInterface.getMtu())));
        pubSubService.publish("ip6:out", advertisement.toString());
    }

}
