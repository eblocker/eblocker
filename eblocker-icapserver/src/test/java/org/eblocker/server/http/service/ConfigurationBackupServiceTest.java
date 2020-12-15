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
package org.eblocker.server.http.service;

import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.backup.AppModulesBackupProvider;
import org.eblocker.server.http.backup.BackupProvider;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DevicesBackupProvider;
import org.eblocker.server.http.backup.TorConfigBackupProvider;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class ConfigurationBackupServiceTest {
    DataSource dataSource = Mockito.mock(DataSource.class);

    @Test
    public void testExportImport() throws IOException {
        TestBackupProvider provider = new TestBackupProvider();
        ConfigurationBackupService service = new ConfigurationBackupService(dataSource, provider);

        exportImport(service);

        provider.verify();
    }

    @Test(expected = UnsupportedBackupVersionException.class)
    public void testUnsupportedVersion() throws IOException {
        AppModulesBackupProvider ambp = Mockito.mock(AppModulesBackupProvider.class);
        DevicesBackupProvider dbp = Mockito.mock(DevicesBackupProvider.class);
        TorConfigBackupProvider tor = Mockito.mock(TorConfigBackupProvider.class);
        ConfigurationBackupService service = new ConfigurationBackupService(dataSource, ambp, dbp, tor) {
            @Override
            void addMainAttributes(Attributes attributes) {
                super.addMainAttributes(attributes);
                attributes.putValue(CURRENT_VERSION_KEY, "-666");
            }
        };
        exportImport(service);
    }

    @Test(expected = CorruptedBackupException.class)
    public void testMissingManifest() throws IOException {
        ConfigurationBackupService service = new ConfigurationBackupService(dataSource) {
            @Override
            public void exportConfiguration(OutputStream outputStream) {
                try (JarOutputStream jar = new JarOutputStream(outputStream)) {
                    // Create an empty JAR file without a manifest
                } catch (IOException e) {
                    throw new RuntimeException("Error setting up the test service!");
                }
            }
        };
        exportImport(service);
    }

    /**
     * Export data from the service to an in-memory Jar file
     * and import it again
     */
    private void exportImport(ConfigurationBackupService service) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        service.importConfiguration(inputStream);
    }

    /**
     * The exported and imported data is a simple text file containing a string
     */
    public class TestBackupProvider extends BackupProvider {
        public static final String ENTRY_NAME = "test-backup-provider.txt";
        private String exportedData = "Test 1234";
        private String importedData = null;

        public void exportConfiguration(JarOutputStream outputStream) throws IOException {
            JarEntry entry = new JarEntry(ENTRY_NAME);
            outputStream.putNextEntry(entry);
            IOUtils.write(exportedData, outputStream, StandardCharsets.UTF_8);
            outputStream.closeEntry();
        }

        public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
            JarEntry entry = inputStream.getNextJarEntry();
            if (entry.getName().equals(ENTRY_NAME)) {
                importedData = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
        }

        public void verify() {
            Assert.assertEquals(exportedData, importedData);
        }
    }
}
