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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.Optional;

public class SchemaMigrationVersion2 implements SchemaMigration {

    private final DataSource dataSource;
    private final int appModulesMinId;

    @Inject
    public SchemaMigrationVersion2(DataSource dataSource, @Named("appmodules.id.custom.min") int appModulesMinId) {
        this.dataSource = dataSource;
        this.appModulesMinId = appModulesMinId;
    }

    @Override
    public String getSourceVersion() {
        return "1";
    }

    @Override
    public String getTargetVersion() {
        return "2";
    }

    /**
     * Initialize redis id counter
     */
    public void migrate() {
        // intialize app module sequence
        Optional<Integer> appModulesDbMaxId = dataSource.getAll(AppWhitelistModule.class).stream().map(AppWhitelistModule::getId).max(Integer::compare);
        int appModulesMaxId = appModulesDbMaxId.isPresent() ? appModulesDbMaxId.get() : 0;
        if (appModulesMaxId < appModulesMinId) {
            appModulesMaxId = appModulesMinId;
        }
        dataSource.setIdSequence(AppWhitelistModule.class, appModulesMaxId);
        dataSource.setVersion("2");
    }
}
