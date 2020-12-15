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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.http.service.DeviceService;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigurableDeviceFilterAcl extends DeviceFilterAcl {

    private Set<String> deviceIds = Collections.emptySet();

    @Inject
    ConfigurableDeviceFilterAcl(@Assisted String path,
                                DeviceService deviceService) {
        super(path, deviceService);
    }

    public void setDevices(Collection<Device> devices) {
        deviceIds = devices.stream().map(Device::getId).collect(Collectors.toSet());
    }

    @Override
    protected boolean filter(Device device) {
        return deviceIds.contains(device.getId());
    }
}
