/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.network.icmpv6.NeighborSolicitation;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tries to verify IPv6 addresses that have been detected in neighbor solicitation
 * messages from devices that do duplicate address detection.
 *
 * <p>The basic workflow is:
 * <ul>
 *     <li>A device assigns itself an IPv6 address</li>
 *     <li>It sends out neighbor solicitation requests (with the unspecified address as source address)
 *     to check whether the address is already in use</li>
 *     <li>The NeighborDiscoveryListener puts the device's MAC address and the IP address candidate into the Ip6AddressDelayedValidator</li>
 *     <li>The device will probably not get any answer to its solicitation request, so it will assign itself the new address</li>
 *     <li>After a few seconds the Ip6AddressDelayedValidator sends a solicitation request with the cached address to the device</li>
 *     <li>The neighbor advertisement response from the device will be processed by the NeighborDiscoveryListener</li>
 * </ul>
 */
@Singleton
public class Ip6AddressDelayedValidator {
    private static final Logger log = LoggerFactory.getLogger(Ip6AddressDelayedValidator.class);

    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final ScheduledExecutorService executorService;

    @Inject
    public Ip6AddressDelayedValidator(@Named("highPrioScheduledExecutor") ScheduledExecutorService executorService, FeatureToggleRouter featureToggleRouter, NetworkInterfaceWrapper networkInterface, PubSubService pubSubService) {
        this.executorService = executorService;
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
    }

    public void validateDelayed(String hardwareAddress, Ip6Address candidate) {
        if (!featureToggleRouter.isIp6Enabled()) {
            return;
        }
        log.debug("Scheduling validation of address candidate {} for device {}", candidate, hardwareAddress);
        Future future = executorService.scheduleWithFixedDelay(getValidator(hardwareAddress, candidate), 5, 10, TimeUnit.SECONDS);
        executorService.schedule(() -> {
            future.cancel(false);
        }, 30, TimeUnit.SECONDS);
    }

    private Runnable getValidator(String hardwareAddress, Ip6Address ipAddress) {
        byte[] sourceHardwareAddress = networkInterface.getHardwareAddress();
        byte[] destHardwareAddress = DatatypeConverter.parseHexBinary(hardwareAddress);
        List<Option> sourceLinkLayerAddressOption = Collections.singletonList(new SourceLinkLayerAddressOption(sourceHardwareAddress));
        Ip6Address sourceLinkLocalAddress = networkInterface.getIp6LinkLocalAddress();

        return () -> {
            log.debug("Sending out neighbor solicitation for candidate {} to device {}", ipAddress, hardwareAddress);
            NeighborSolicitation solicitation = new NeighborSolicitation(
                    sourceHardwareAddress,
                    sourceLinkLocalAddress,
                    destHardwareAddress,
                    ipAddress,
                    ipAddress,
                    sourceLinkLayerAddressOption);
            pubSubService.publish(Channels.IP6_OUT, solicitation.toString());
        };
    }
}
