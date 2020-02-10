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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.icap.resources.DefaultEblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import com.google.inject.Inject;

public class DeviceFactory {
    private static final Logger log = LoggerFactory.getLogger(DeviceFactory.class);
    private static final String DEFAULT_NAME_DEVICE_DE = "Gerät";
    private static final String DEFAULT_NAME_DEVICE_KEY_DE = "de";
    private static final String DEFAULT_NAME_DEVICE_DEFAULT = "Device";
    private static final String DEFAULT_NAME_NUMBER_PREFIX = " (No. ";
    private static final String DEFAULT_NAME_NUMBER_POSTFIX = ")";

    private final MacPrefix macPrefix = new MacPrefix();
    private final DataSource dataSource;
    private final MacPrefix disabledByDefault;

    @Inject
    public DeviceFactory(DataSource dataSource) throws IOException {
        this.dataSource = dataSource;

        disabledByDefault = new MacPrefix();
        disabledByDefault.addInputStream(
                ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES_DISABLED_BY_DEFAULT));

        try {
            macPrefix.addInputStream(ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES));
        } catch (IOException e) {
            log.error("Could not read MAC prefixes", e);
        }
    }

    public Device createDevice(String deviceId, List<IpAddress> ipAddresses, boolean fixed) {
        Device device = new Device();
        device.setId(deviceId);
        device.setIpAddresses(ipAddresses);
        String hardwareAddressPrefix = device.getHardwareAddressPrefix();
        device.setEnabled(!isDisabledByDefault(hardwareAddressPrefix));
        device.setIpAddressFixed(fixed);
        device.setName(createNameForNewDevice(hardwareAddressPrefix));
        return device;
    }

    public String createNameForNewDevice(String hardwareAddressPrefix) {
        String vendor = macPrefix.getVendor(hardwareAddressPrefix);
        if (vendor==null || "".equals(vendor)){
            // No vendor found, use "Device" instead
            if (DEFAULT_NAME_DEVICE_KEY_DE.equalsIgnoreCase(dataSource.getCurrentLanguage().getId())) {
                vendor = DEFAULT_NAME_DEVICE_DE;
            } else {
                vendor = DEFAULT_NAME_DEVICE_DEFAULT;
            }
        }
        vendor = new StringBuilder().append(vendor).append(DEFAULT_NAME_NUMBER_PREFIX).toString();
        // Find small yet unique integer
        Set<String> existingDeviceNames = dataSource.getDevices().stream().map(iterDev -> iterDev.getName())
                .collect(Collectors.toSet());
        for (int deviceNumber = 1; deviceNumber < Integer.MAX_VALUE; deviceNumber++) {
            String potentialName = new StringBuilder().append(vendor).append(deviceNumber)
                    .append(DEFAULT_NAME_NUMBER_POSTFIX).toString();
            if (existingDeviceNames.contains(potentialName)) {
                continue;
            }
            return potentialName;
        }
        return "";
    }

   private boolean isDisabledByDefault(String macAdressPrefix){
       return disabledByDefault.getVendor(macAdressPrefix) != null;
   }

}
