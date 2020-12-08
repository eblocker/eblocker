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
import org.eblocker.server.common.data.CompressionMode;
import org.eblocker.server.common.data.DataSource;

public class SchemaMigrationVersion21 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion21(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "20";
    }

    @Override
    public String getTargetVersion() {
        return "21";
    }

    @Override
    public void migrate() {
        dataSource.setDntHeaderState(true);
        dataSource.setCompressionMode(CompressionMode.VPN_CLIENTS_ONLY);
        dataSource.setVersion("21");
    }

}
