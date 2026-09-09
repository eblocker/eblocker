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
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.backup.ConfigBackupImportResult;
import org.eblocker.server.http.backup.AppModulesBackupProvider;
import org.eblocker.server.http.backup.BackupAttributes;
import org.eblocker.server.http.backup.BackupProviderFactory;
import org.eblocker.server.http.backup.BlockersBackupProvider;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DevicesLegacyBackupProvider;
import org.eblocker.server.http.backup.DnsBackupProvider;
import org.eblocker.server.http.backup.GeneralSettingsBackup;
import org.eblocker.server.http.backup.GeneralSettingsBackupProvider;
import org.eblocker.server.http.backup.HttpsKeysBackupProvider;
import org.eblocker.server.http.backup.NetworkBackupProvider;
import org.eblocker.server.http.backup.OpenVpnClientBackupProvider;
import org.eblocker.server.http.backup.OpenVpnServerBackupProvider;
import org.eblocker.server.http.backup.RegistrationBackupProvider;
import org.eblocker.server.http.backup.TorConfigBackupProvider;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.eblocker.server.http.backup.UsersBackupProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationBackupServiceTest {
    private DataSource dataSource;
    private ConfigurationBackupService service;
    private GeneralSettingsBackupProvider generalSettingsBP;
    private AppModulesBackupProvider appModulesBP;
    private DevicesLegacyBackupProvider devicesLegacyBP;
    private TorConfigBackupProvider torConfigBP;
    private HttpsKeysBackupProvider httpsKeysBP;
    private OpenVpnServerBackupProvider openVpnServerBP;
    private OpenVpnClientBackupProvider openVpnClientBP;
    private RegistrationBackupProvider registrationBP;
    private UsersBackupProvider usersBP;
    private BlockersBackupProvider blockersBP;
    private DnsBackupProvider dnsBP;
    private NetworkBackupProvider networkBP;

    private static final String password = "top secret!";

    @BeforeEach
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        generalSettingsBP = Mockito.mock(GeneralSettingsBackupProvider.class);
        appModulesBP = Mockito.mock(AppModulesBackupProvider.class);
        devicesLegacyBP = Mockito.mock(DevicesLegacyBackupProvider.class);
        torConfigBP = Mockito.mock(TorConfigBackupProvider.class);
        httpsKeysBP = Mockito.mock(HttpsKeysBackupProvider.class);
        openVpnServerBP = Mockito.mock(OpenVpnServerBackupProvider.class);
        openVpnClientBP = Mockito.mock(OpenVpnClientBackupProvider.class);
        registrationBP = Mockito.mock(RegistrationBackupProvider.class);
        usersBP = Mockito.mock(UsersBackupProvider.class);
        blockersBP = Mockito.mock(BlockersBackupProvider.class);
        dnsBP = Mockito.mock(DnsBackupProvider.class);
        networkBP = Mockito.mock(NetworkBackupProvider.class);

        BackupProviderFactory providerFactory = new BackupProviderFactory() {
            @Override
            public GeneralSettingsBackupProvider createGeneralSettingsBackupProvider() {
                return generalSettingsBP;
            }

            @Override
            public AppModulesBackupProvider createAppModulesBackupProvider() {
                return appModulesBP;
            }

            @Override
            public DevicesLegacyBackupProvider createDevicesLegacyBackupProvider() {
                return devicesLegacyBP;
            }

            @Override
            public TorConfigBackupProvider createTorConfigBackupProvider() {
                return torConfigBP;
            }

            @Override
            public HttpsKeysBackupProvider createHttpsKeysBackupProvider(CryptoService cryptoService) {
                return httpsKeysBP;
            }

            @Override
            public OpenVpnServerBackupProvider createOpenVpnServerBackupProvider(CryptoService cryptoService) {
                return openVpnServerBP;
            }

            @Override
            public OpenVpnClientBackupProvider createOpenVpnClientBackupProvider(CryptoService cryptoService) {
                return openVpnClientBP;
            }

            @Override
            public RegistrationBackupProvider createRegistrationBackupProvider(CryptoService cryptoService) {
                return registrationBP;
            }

            @Override
            public UsersBackupProvider createUsersBackupProvider() {
                return usersBP;
            }

            @Override
            public BlockersBackupProvider createBlockersBackupProvider() {
                return blockersBP;
            }

            @Override
            public DnsBackupProvider createDnsBackupProvider() {
                return dnsBP;
            }

            @Override
            public NetworkBackupProvider createNetworkBackupProvider() {
                return networkBP;
            }
        };
        service = new ConfigurationBackupService(dataSource, providerFactory);
        Mockito.when(dataSource.getVersion()).thenReturn("42");
    }

    @Test
    public void testExportImport() throws IOException {
        exportImport(service);
    }

    @Test
    public void testUnsupportedVersion() throws IOException {
        Manifest manifest = new Manifest();
        BackupAttributes attribs = new BackupAttributes(-666, 42, false);
        attribs.addToAttributes(manifest.getMainAttributes());
        byte[] backup = createJar(manifest);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(backup);
        assertThrows(UnsupportedBackupVersionException.class, () -> service.importConfiguration(inputStream, null));
    }

    @Test
    public void testMissingManifest() throws IOException {
        byte[] backup = createJar(null);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(backup);
        assertThrows(CorruptedBackupException.class, () -> service.importConfiguration(inputStream, null));
    }

    @Test
    public void testRequiresPassword() throws IOException {

        // without password:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, null);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        assertFalse(service.requiresPassword(inputStream));

        // with password:
        outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, password);
        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        assertTrue(service.requiresPassword(inputStream));
    }

    @Test
    public void testWarnings() throws IOException {
        final List<BackupWarning> warnings = List.of(new BackupWarning(BackupWarning.Id.UPNP_PORT_FORWARDING_FAILURE));
        Mockito.when(openVpnServerBP.getWarnings()).thenReturn(warnings);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, password);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ConfigBackupImportResult result = service.importConfiguration(inputStream, password);
        assertTrue(result.hasWarnings());
        assertEquals(warnings, result.getWarnings());
    }

    /**
     * Export data from the service to an in-memory Jar file
     * and import it again
     */
    private void exportImport(ConfigurationBackupService service) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportConfiguration(outputStream, password);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        service.importConfiguration(inputStream, password);
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
