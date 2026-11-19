/*
 * Copyright 2026 eBlocker Open Source GmbH
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
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.http.service.DeviceScanningService;
import org.eblocker.server.http.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneralSettingsBackupProviderTest extends BackupProviderTestBase {
    GeneralSettingsBackupProvider provider, providerNoPassword;
    private DeviceScanningService deviceScanningService;
    private DeviceFactory deviceFactory;
    private SquidWarningService squidWarningService;
    private SettingsService settingsService;
    private DataSource dataSource;
    private final LocaleSettings LOCALE = new LocaleSettings("English (United States)", "US", "en", "America/New_York", false);
    private final byte[] passwordHash = new byte[] {1, 23, 42, 54, 3};

    @BeforeEach
    void setUp() throws IOException {
        deviceScanningService = Mockito.mock(DeviceScanningService.class);
        deviceFactory = Mockito.mock(DeviceFactory.class);
        squidWarningService = Mockito.mock(SquidWarningService.class);
        settingsService = Mockito.mock(SettingsService.class);
        dataSource = Mockito.mock(DataSource.class);
        provider = new GeneralSettingsBackupProvider(deviceScanningService, deviceFactory, squidWarningService, settingsService, dataSource, createCryptoService("top secret!!!"));
        providerNoPassword = new GeneralSettingsBackupProvider(deviceScanningService, deviceFactory, squidWarningService, settingsService, dataSource, null);
        Mockito.when(deviceScanningService.getScanningInterval()).thenReturn(600L);
        Mockito.when(deviceFactory.isAutoEnableNewDevices()).thenReturn(true);
        Mockito.when(squidWarningService.isEnabled()).thenReturn(true);
        Mockito.when(settingsService.getLocaleSettings()).thenReturn(LOCALE);
    }

    @Test
    void testRoundtrip() throws IOException {
        exportVerifyImport(provider);
        Mockito.verify(deviceScanningService).setScanningInterval(600L);
        Mockito.verify(deviceFactory).setAutoEnableNewDevices(true);
        Mockito.verify(squidWarningService).setRecordingFailedConnectionsEnabled(true);
        Mockito.verify(settingsService).setLocaleSettings(LOCALE);
    }

    @Test
    void restoreConsolePassword() throws IOException {
        Mockito.when(dataSource.getPasswordHash()).thenReturn(passwordHash);
        exportVerifyImport(provider);
        Mockito.verify(dataSource).setPasswordHash(passwordHash);
        assertEquals(0, provider.getWarnings().size());
    }

    @Test
    void cannotRestoreConsolePasswordWithoutBackupPassword() throws IOException {
        Mockito.when(dataSource.getPasswordHash()).thenReturn(passwordHash);
        byte[] backup = exportBackup(provider);
        importBackup(backup, providerNoPassword);
        Mockito.verify(dataSource, Mockito.never()).setPasswordHash(passwordHash);
        Mockito.verify(dataSource, Mockito.never()).deletePasswordHash();
        List<BackupWarning> warnings = providerNoPassword.getWarnings();
        assertEquals(List.of(new BackupWarning(BackupWarning.Id.NO_PASSWORD_CONSOLE_PASSWORD_NOT_IMPORTED)), warnings);
    }
}
