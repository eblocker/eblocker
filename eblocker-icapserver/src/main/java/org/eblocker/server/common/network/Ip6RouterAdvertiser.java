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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisementFactory;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip6Utils;
import org.eblocker.server.http.service.DeviceService;

import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;

@Singleton
public class Ip6RouterAdvertiser {

    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final RouterAdvertisementFactory routerAdvertisementFactory;
    private final DeviceService deviceService;

    @Inject
    public Ip6RouterAdvertiser(FeatureToggleRouter featureToggleRouter,
                               NetworkInterfaceWrapper networkInterface,
                               PubSubService pubSubService,
                               RouterAdvertisementFactory routerAdvertisementFactory,
                               DeviceService deviceService) {
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
        this.routerAdvertisementFactory = routerAdvertisementFactory;
        this.deviceService = deviceService;
    }

    public void advertise() {
        if (!canSendRouterAdvertisements()) {
            return;
        }

        byte[] ownHwAddress = networkInterface.getHardwareAddress();
        Ip6Address ownIp6LinkLocal = networkInterface.getIp6LinkLocalAddress();
        if (ownHwAddress == null || ownIp6LinkLocal == null) {
            return;
        }

        for (Device device : deviceService.getDevices(false)) {
            if (!device.isEnabled() || device.isGateway() || device.isEblocker()) {
                continue;
            }
            sendUnicastAdvertisement(device, ownHwAddress, ownIp6LinkLocal, false);
        }
    }

    public void deprecate(Device device) {
        if (device == null || device.isGateway() || device.isEblocker() || !canSendRouterAdvertisements()) {
            return;
        }

        byte[] ownHwAddress = networkInterface.getHardwareAddress();
        Ip6Address ownIp6LinkLocal = networkInterface.getIp6LinkLocalAddress();
        if (ownHwAddress == null || ownIp6LinkLocal == null) {
            return;
        }

        sendUnicastAdvertisement(device, ownHwAddress, ownIp6LinkLocal, true);
    }

    private boolean canSendRouterAdvertisements() {
        if (!featureToggleRouter.shouldSendRouterAdvertisements()) {
            return false;
        }

        if (!networkInterface.isUp()) {
            return false;
        }

        return !networkInterface.getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .allMatch(ip -> Ip6Utils.isLinkLocal((Ip6Address) ip));
    }

    private void sendUnicastAdvertisement(Device device, byte[] ownHwAddress, Ip6Address ownIp6LinkLocal, boolean deprecate) {
        String hexMac = device.getHardwareAddress(false);
        if (hexMac == null) {
            return;
        }
        byte[] targetHwAddress = DatatypeConverter.parseHexBinary(hexMac);
        if (targetHwAddress.length != 6 || Arrays.equals(targetHwAddress, ownHwAddress)) {
            return;
        }

        device.getIpAddresses().stream()
                .filter(IpAddress::isIpv6)
                .map(ip -> (Ip6Address) ip)
                .filter(Ip6Utils::isLinkLocal)
                .forEach(targetIp6 -> {
                    RouterAdvertisement advertisement = deprecate
                            ? routerAdvertisementFactory.createDeprecation(ownHwAddress, ownIp6LinkLocal, targetHwAddress, targetIp6)
                            : routerAdvertisementFactory.create(ownHwAddress, ownIp6LinkLocal, targetHwAddress, targetIp6);
                    pubSubService.publish(Channels.IP6_OUT, advertisement.toString());
                });
    }

}
