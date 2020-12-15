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
package org.eblocker.server.common.data;

import org.eblocker.server.http.service.DeviceService;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

/**
 * Factory for adding devices to a mock DataSource
 */
public class TestDeviceFactory {
    private Map<String, Device> devices = new HashMap<>();
    private DataSource dataSource;
    private DeviceService deviceService;

    public TestDeviceFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public TestDeviceFactory(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public void addDevice(String hwAddress, String ipAddress, boolean enabled) {
        Device device = createDevice(hwAddress, ipAddress, enabled);
        addDevice(device);
    }

    public void addDevice(Device device) {
        devices.put(device.getId(), device);
        if (dataSource != null) {
            when(dataSource.getDevice(device.getId())).thenReturn(device);
        } else {
            when(deviceService.getDeviceById(device.getId())).thenReturn(device);
            device.getIpAddresses().forEach(ipAddress -> when(deviceService.getDeviceByIp(ipAddress)).thenReturn(device));
        }

    }

    public Device getDevice(String id) {
        return devices.get(id);
    }

    public void commit() {
        if (dataSource != null) {
            when(dataSource.getDeviceIds()).thenReturn(new TreeSet<>(devices.keySet()));
            when(dataSource.getDevices()).thenReturn(new HashSet<>(devices.values()));
        } else {
            when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(devices.values());
        }

    }

    public static Device createDevice(String hwAddress, String ipAddress, boolean enabled) {
        return createDevice(hwAddress, ipAddress, null, enabled, true);
    }

    public static Device createDevice(String hwAddress, String ipAddress, boolean enabled, boolean fixed) {
        return createDevice(hwAddress, ipAddress, null, enabled, fixed);
    }

    public static Device createDevice(String hwAddress, String ipAddress, String name, boolean enabled) {
        return createDevice(hwAddress, ipAddress, name, enabled, true);
    }

    public static Device createDevice(String hwAddress, String ipAddress, String name, boolean enabled, boolean fixed) {
        Device device = new Device();
        device.setId("device:" + hwAddress);
        device.setName(name);
        if (ipAddress != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ipAddress)));
        }
        device.setEnabled(enabled);
        device.setIpAddressFixed(fixed);
        return device;
    }

    public static Device createDevice(String hwAddress, List<String> ipAddresses, boolean enabled) {
        Device device = createDevice(hwAddress, (String) null, enabled);
        if (ipAddresses != null) {
            device.setIpAddresses(ipAddresses.stream().map(IpAddress::parse).collect(Collectors.toList()));
        }
        return device;
    }

    public Set<Device> getDevices() {
        return new HashSet<Device>(devices.values());
    }
}
