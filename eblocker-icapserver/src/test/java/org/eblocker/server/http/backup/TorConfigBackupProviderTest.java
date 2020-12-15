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
import org.eblocker.server.common.network.TorController;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TorConfigBackupProviderTest extends BackupProviderTestBase {
    private TorController torController;
    private TorConfigBackupProvider provider;
    private DataSource dataSource;

    @Before
    public void setUp() {
        torController = Mockito.mock(TorController.class);
        provider = new TorConfigBackupProvider(torController);
        dataSource = Mockito.mock(DataSource.class);
    }

    @Test
    public void testExportImport() throws IOException {
        // Countries before Backup
        Set<String> countries = new HashSet<>();
        countries.add("xy");

        Mockito.when(torController.getCurrentExitNodeCountries()).thenReturn(countries);

        exportAndImportWithTorConfigBackupProvider(dataSource, provider);

        Set<String> expectedCountries = new HashSet<>();
        expectedCountries.add("xy");
        Mockito.verify(torController).setAllowedExitNodesCountries(expectedCountries);
    }

    @Test
    public void testExportImportEmptyList() throws IOException {
        // Empty list means the user has set the selection to automatic
        Set<String> countries = new HashSet<>();

        Mockito.when(torController.getCurrentExitNodeCountries()).thenReturn(countries);

        exportAndImportWithTorConfigBackupProvider(dataSource, provider);

        Mockito.verify(torController).setAllowedExitNodesCountries(Collections.EMPTY_SET);
    }
}
