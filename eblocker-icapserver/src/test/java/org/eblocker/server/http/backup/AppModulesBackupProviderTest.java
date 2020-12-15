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
package org.eblocker.server.http.backup;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.service.AppModuleService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppModulesBackupProviderTest extends BackupProviderTestBase {
    private AppModuleService service;
    private AppModulesBackupProvider provider;
    private DataSource dataSource;

    @Before
    public void setUp() {
        service = Mockito.mock(AppModuleService.class);
        provider = new AppModulesBackupProvider(service);
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getVersion()).thenReturn("40");
    }

    @Test
    public void testExportImport() throws IOException {
        AppWhitelistModule builtInUnmodified = createModule(42, "Unmodified Built-in App", false, true, false);
        AppWhitelistModule builtInModified = createModule(43, "Modified Built-in App", true, true, true);
        AppWhitelistModule userDefined = createModule(10042, "User-defined App", true, false, true);
        AppWhitelistModule builtInTemp = createModule(0, "Built-in Temporary App", false, true, true);

        List<AppWhitelistModule> allModules = Arrays.asList(builtInTemp, builtInUnmodified, builtInModified, userDefined);
        Mockito.when(service.getAll()).thenReturn(allModules);

        exportAndImportWith(dataSource, provider);

        List<AppWhitelistModule> modulesToBeImported = Arrays.asList(builtInModified, userDefined);
        Map<Integer, Boolean> enabledStates = allModules.stream().collect(Collectors.toMap(AppWhitelistModule::getId, AppWhitelistModule::isEnabled));
        Mockito.verify(service).restoreModified(modulesToBeImported, enabledStates);
    }

    private AppWhitelistModule createModule(int moduleId, String name, boolean enabled, boolean builtIn, boolean modified) {
        return new AppWhitelistModule(
                moduleId,
                name,
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                false,
                enabled,
                builtIn,
                modified,
                null,
                false,
                false
        );

    }
}
