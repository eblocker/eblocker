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

/**
 * Creates EblockerDnsServerState based on current config
 * Disables TOR for Gateway nodes, in case it has been enabled in prior version.
 */
public class SchemaMigrationVersion18 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion18(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "17";
    }

    @Override
    public String getTargetVersion() {
        return "18";
    }

    @Override
    public void migrate() {
        setMessageSeverityForDevices();
        disableTorForGatewayDevices();
        dataSource.setVersion("18");
    }

    private void setMessageSeverityForDevices() {
        for (Device device : dataSource.getDevices()) {
            device.setShowPauseDialog(true);
            device.setShowPauseDialogDoNotShowAgain(true);
            dataSource.save(device);
        }
    }

    private void disableTorForGatewayDevices() {
        for (Device device : dataSource.getDevices()) {
            if (device.isGateway()) {
                device.setUseAnonymizationService(false);
                device.setRouteThroughTor(false);
                device.setUseVPNProfileID(null);
                device.setIsVpnClient(false);
                dataSource.save(device);
            }
        }
    }

}
