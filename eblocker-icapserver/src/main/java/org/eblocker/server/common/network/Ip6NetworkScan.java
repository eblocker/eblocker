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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.NeighborSolicitation;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip6Utils;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scans a ip6 network for new addresses
 * <p>
 * This scan works by generating global address candidates by combining router prefixes and known link-local addresses
 * for each device. These addresses candidates are probed by sending Neighbor Solicitation requests. If addresses are
 * reachable their responses will be picked up by {@link NeighborDiscoveryListener}.
 *
 * @see NeighborDiscoveryListener
 */
public class Ip6NetworkScan {
    private static final Logger log = LoggerFactory.getLogger(Ip6NetworkScan.class);

    private final DeviceService deviceService;
    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;

    @Inject
    public Ip6NetworkScan(DeviceService deviceService, FeatureToggleRouter featureToggleRouter, NetworkInterfaceWrapper networkInterface, PubSubService pubSubService) {
        this.deviceService = deviceService;
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
    }

    public void run() {
        if (!featureToggleRouter.isIp6Enabled()) {
            return;
        }

        byte[] sourceHardwareAddress = networkInterface.getHardwareAddress();
        List<Option> sourceLinkLayerAddressOption = Collections.singletonList(new SourceLinkLayerAddressOption(sourceHardwareAddress));

        Ip6Address sourceLinkLocalAddress = networkInterface.getIp6LinkLocalAddress();

        List<Ip6Address> networks = networkInterface.getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .map(ip -> (Ip6Address) ip)
                .filter(ip -> !Ip6Utils.isLinkLocal(ip))
                .map(ip -> Ip6Utils.getNetworkAddress(ip, networkInterface.getNetworkPrefixLength(ip)))
                .distinct()
                .collect(Collectors.toList());

        Collection<Device> devices = deviceService.getDevices(false);
        for (Device device : devices) {
            Set<Ip6Address> ip6Addresses = device.getIpAddresses().stream()
                    .filter(IpAddress::isIpv6)
                    .map(ip -> (Ip6Address) ip)
                    .collect(Collectors.toSet());
            if (ip6Addresses.isEmpty()) {
                continue;
            }

            log.debug("ip6 addresses of {}: {}", device.getId(), ip6Addresses);

            List<Ip6Address> linkLocalAddresses = ip6Addresses.stream()
                    .filter(Ip6Utils::isLinkLocal)
                    .collect(Collectors.toList());

            log.debug("found {} link-local addresses for {}: {}", linkLocalAddresses.size(), device.getId(), linkLocalAddresses);

            Set<Ip6Address> generatedAddresses = linkLocalAddresses.stream()
                    .map(ip -> Ip6Utils.getHostAddress(ip, 64))
                    .flatMap(host -> networks.stream().map(net -> Ip6Utils.combine(net, host)))
                    .collect(Collectors.toSet());
            log.debug("generated global address for {}: {}", device.getId(), generatedAddresses);

            List<Ip6Address> globalAddressCandidates = generatedAddresses.stream()
                    .filter(ip -> !ip6Addresses.contains(ip))
                    .collect(Collectors.toList());
            log.debug("global address candidates for {}: {}", device.getId(), globalAddressCandidates);

            globalAddressCandidates
                    .forEach(ip -> sendNeighborDiscoverySolicitation(
                            sourceHardwareAddress,
                            sourceLinkLocalAddress,
                            DatatypeConverter.parseHexBinary(device.getHardwareAddress(false)),
                            ip,
                            sourceLinkLayerAddressOption));
        }
    }

    private void sendNeighborDiscoverySolicitation(byte[] sourceHardwareAddress,
                                                   Ip6Address sourceIpAddress,
                                                   byte[] targetHardwareAddress,
                                                   Ip6Address targetIpAddress,
                                                   List<Option> sourceLinkLayerAddressOption) {
        NeighborSolicitation solicitation = new NeighborSolicitation(
                sourceHardwareAddress,
                sourceIpAddress,
                targetHardwareAddress,
                targetIpAddress,
                targetIpAddress,
                sourceLinkLayerAddressOption);
        pubSubService.publish("ip6:out", solicitation.toString());
    }
}
