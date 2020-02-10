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
package org.eblocker.server.common.squid.acl;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.service.DeviceService;

import java.util.function.Predicate;
import java.util.stream.Stream;

class DevicePredicateFilterAcl extends DeviceFilterAcl {

    private final Predicate<Device> devicePredicate;
    private final Predicate<IpAddress> ipAddressPredicate;

    DevicePredicateFilterAcl(String path, DeviceService deviceService, Predicate<Device> devicePredicate) {
        this(path, deviceService, devicePredicate, device -> true);
    }

    DevicePredicateFilterAcl(String path, DeviceService deviceService, Predicate<Device> devicePredicate, Predicate<IpAddress> ipAddressPredicate) {
        super(path, deviceService);
        this.devicePredicate = devicePredicate;
        this.ipAddressPredicate = ipAddressPredicate;
    }

    @Override
    protected boolean filter(Device device) {
        return devicePredicate.test(device);
    }

    @Override
    protected Stream<String> ipAddresses(Device device) {
        return device.getIpAddresses().stream().filter(ipAddressPredicate).map(IpAddress::toString);
    }
}
