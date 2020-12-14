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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;

public class SchemaMigrationVersion29 implements SchemaMigration {

    private final DataSource dataSource;
    private final int port;

    @Inject
    public SchemaMigrationVersion29(DataSource dataSource, @Named("openvpn.server.port") int portForwardingPort) {
        this.dataSource = dataSource;
        this.port = portForwardingPort;
    }

    @Override
    public String getSourceVersion() {
        return "28";
    }

    @Override
    public String getTargetVersion() {
        return "29";
    }

    @Override
    public void migrate() {
        if (dataSource.getOpenVpnServerState()) {
            // Server is running
            dataSource.setOpenVpnPortForwardingMode(PortForwardingMode.MANUAL);
        } else {
            dataSource.setOpenVpnPortForwardingMode(PortForwardingMode.AUTO);
        }
        dataSource.setOpenVpnMappedPort(port);

        dataSource.setVersion("29");
    }
}
