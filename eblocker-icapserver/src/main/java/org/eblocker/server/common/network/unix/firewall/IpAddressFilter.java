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
