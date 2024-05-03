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
package org.eblocker.server.common.data.migrations;

import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.MacPrefix;
import org.eblocker.server.icap.resources.DefaultEblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class SchemaMigrationVersion24 implements SchemaMigration {

    private final DataSource dataSource;
    private final DeviceFactory deviceFactory;
    private final MacPrefix macPrefix;
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion24.class);

    @Inject
    public SchemaMigrationVersion24(
            DataSource dataSource, DeviceFactory deviceFactory) {
        this.dataSource = dataSource;
        this.deviceFactory = deviceFactory;
        this.macPrefix = new MacPrefix();
        try (InputStream inputStream = ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES)) {
            macPrefix.addInputStream(inputStream);
        } catch (IOException e) {
            log.error("Could not read MAC prefixes", e);
        }
    }

    @Override
    public String getSourceVersion() {
        return "23";
    }

    @Override
    public String getTargetVersion() {
        return "24";
    }

    @Override
    public void migrate() {
        for (Device device : dataSource.getDevices()) {
            if (device.getName() == null || "".equals(device.getName())) {
                device.setName(deviceFactory.createNameForNewDevice(device.getHardwareAddressPrefix()));
                dataSource.save(device);
            }
        }

        dataSource.setVersion("24");
    }

}
