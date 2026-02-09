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

import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.backup.AppModulesBackupProvider;
import org.eblocker.server.http.backup.BackupAttributes;
import org.eblocker.server.http.backup.BackupProviderFactory;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DevicesBackupProvider;
import org.eblocker.server.http.backup.HttpsKeysBackupProvider;
import org.eblocker.server.http.backup.TorConfigBackupProvider;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class ConfigurationBackupServiceTest {
    DataSource dataSource;
    ConfigurationBackupService service;
    AppModulesBackupProvider appModulesBP;
    DevicesBackupProvider devicesBP;
    TorConfigBackupProvider torConfigBP;
    HttpsKeysBackupProvider httpsKeysBP;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        appModulesBP = Mockito.mock(AppModulesBackupProvider.class);
        devicesBP = Mockito.mock(DevicesBackupProvider.class);
        torConfigBP = Mockito.mock(TorConfigBackupProvider.class);
        httpsKeysBP = Mockito.mock(HttpsKeysBackupProvider.class);

        BackupProviderFactory providerFactory = new BackupProviderFactory() {
            @Override
            public AppModulesBackupProvider createAppModulesBackupProvider() {
                return appModulesBP;
            }

            @Override
            public DevicesBackupProvider createDevicesBackupProvider() {
                return devicesBP;
            }

            @Override
            public TorConfigBackupProvider createTorConfigBackupProvider() {
                return torConfigBP;
            }

            @Override
            public HttpsKeysBackupProvider createHttpsKeysBackupProvider(CryptoService cryptoService) {
                return httpsKeysBP;
            }
        };
        service = new ConfigurationBackupService(dataSource, providerFactory);
        Mockito.when(dataSource.getVersion()).thenReturn("42");
    }

    @Test
    public void testExportImport() throws IOException {
        exportImport(service);
    }

    @Test(expected = UnsupportedBackupVersionException.class)
    public void testUnsupportedVersion() throws IOException {
        Manifest manifest = new Manifest();
        BackupAttributes attribs = new BackupAttributes(-666, 42, false);
        attribs.addToAttributes(manifest.getMainAttributes());
        byte[] backup = createJar(manifest);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(backup);
        service.importConfiguration(inputStream);
    }

    @Test(expected = CorruptedBackupException.class)
    public void testMissingManifest() throws IOException {
        byte[] backup = createJar(null);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(backup);
        service.importConfiguration(inputStream);
    }

    @Test
    public void testRequiresPassword() throws IOException {

        // without password:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, null);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        Assert.assertFalse(service.requiresPassword(inputStream));

        // with password:
        outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, "top secret");
        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        Assert.assertTrue(service.requiresPassword(inputStream));
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

    private byte[] createJar(Manifest manifest) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JarOutputStream jarStream;
        if (manifest == null) {
            jarStream = new JarOutputStream(outputStream);
        } else {
            jarStream = new JarOutputStream(outputStream, manifest);
        }
        jarStream.close();
        return outputStream.toByteArray();
    }
}
