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
import org.eblocker.server.http.service.ConfigurationBackupService;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SuppressWarnings("squid:S2187")
public class BackupProviderTestBase {
    protected void exportAndImportWith(DataSource dataSource, BackupProvider provider) throws IOException {
        exportAndImportWith(dataSource, provider, null);
    }

    protected void exportAndImportWith(DataSource dataSource, BackupProvider provider, String password) throws IOException {
        ConfigurationBackupService service = new ConfigurationBackupService(dataSource, provider);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, password);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        service.importConfiguration(inputStream, password);
    }
}
