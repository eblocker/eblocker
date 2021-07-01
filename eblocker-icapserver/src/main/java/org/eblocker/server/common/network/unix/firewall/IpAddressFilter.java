/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.unix.firewall;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IpAddressFilter {
    private final ParentalControlAccessRestrictionsService restrictionsService;
    private final Set<Device> devices;
    private final Predicate<IpAddress> addressTypePredicate;

    public IpAddressFilter(Set<Device> devices, Predicate<IpAddress> addressTypePredicate, ParentalControlAccessRestrictionsService restrictionsService) {
        this.devices = devices;
        this.restrictionsService = restrictionsService;
        this.addressTypePredicate = addressTypePredicate;
    }

    public List<String> getAccessRestrictedIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isEnabled)
                .filter(device -> !device.getIpAddresses().isEmpty())
                .filter(device -> !restrictionsService.isAccessPermitted(device)));
    }

    private List<String> collectIpAddresses(Stream<Device> deviceStream) {
        return deviceStream
                .map(Device::getIpAddresses)
                .flatMap(List::stream)
                .filter(addressTypePredicate)
                .map(IpAddress::toString)
                .collect(Collectors.toList());
    }

    public List<String> getEnabledDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isEnabled));
    }

    public List<String> getDisabledDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(device -> !device.isEnabled())
                .filter(Device::isActive));
    }

    public List<String> getSslEnabledDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isActive)
                .filter(Device::isSslEnabled));
    }

    public List<String> getTorDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isEnabled)
                .filter(Device::isUseAnonymizationService)
                .filter(Device::isRoutedThroughTor));
    }

    public List<String> getMalwareDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isMalwareFilterEnabled));
    }

    public List<String> getMobileVpnDevicesIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isActive)
                .filter(Device::isVpnClient));
    }

    public List<String> getMobileVpnDevicesPrivateNetworkAccessIps() {
        return collectIpAddresses(devices.stream()
                .filter(Device::isActive)
                .filter(Device::isVpnClient)
                .filter(Device::isMobilePrivateNetworkAccess));
    }

    public List<String> getDevicesIps(Set<String> deviceIds) {
        return collectIpAddresses(devices.stream()
                .filter(device -> deviceIds.contains(device.getId())));
    }
}
