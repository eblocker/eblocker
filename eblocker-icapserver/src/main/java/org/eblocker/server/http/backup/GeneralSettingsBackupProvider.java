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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.http.service.DeviceScanningService;
import org.eblocker.server.http.service.DnsService;
import org.eblocker.server.http.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Backs up and restores general global settings.
 */
public class GeneralSettingsBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GeneralSettingsBackupProvider.class);
    public static final String GENERAL_ENTRY = "eblocker-config/general.json";

    private final DeviceScanningService deviceScanningService;
    private final DeviceFactory deviceFactory;
    private final SquidWarningService squidWarningService;
    private final SettingsService settingsService;
    private final DataSource dataSource;

    @Inject
    public GeneralSettingsBackupProvider(DeviceScanningService deviceScanningService,
                                         DeviceFactory deviceFactory,
                                         SquidWarningService squidWarningService,
                                         SettingsService settingsService,
                                         DataSource dataSource,
                                         @Assisted @Nullable CryptoService cryptoService) {
        super(cryptoService);
        this.deviceScanningService = deviceScanningService;
        this.deviceFactory = deviceFactory;
        this.squidWarningService = squidWarningService;
        this.settingsService = settingsService;
        this.dataSource = dataSource;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        GeneralSettingsBackup backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        writeNextEntry(outputStream, GENERAL_ENTRY, backupBytes);
    }

    private GeneralSettingsBackup createBackup() {
        GeneralSettingsBackup backup = new GeneralSettingsBackup();
        backup.setDeviceScanningInterval(deviceScanningService.getScanningInterval());
        backup.setAutoEnableNewDevices(deviceFactory.isAutoEnableNewDevices());
        backup.setSquidWarningServiceEnabled(squidWarningService.isEnabled());
        backup.setLocaleSettings(settingsService.getLocaleSettings());
        backup.setConsolePasswordHash(dataSource.getPasswordHash());
        return backup;
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, false);
    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, true);
    }

    private void importConfiguration(JarInputStream inputStream, int schemaVersion, boolean dryRun) throws IOException {
        getNextEntry(inputStream, GENERAL_ENTRY);

        GeneralSettingsBackup backup = objectMapper.readValue(inputStream, GeneralSettingsBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        // warn if backup contains password hash, but we cannot decrypt it:
        if (!canDecrypt() && backup.getConsolePasswordHash() != null) {
            addWarning(new BackupWarning(BackupWarning.Id.NO_PASSWORD_CONSOLE_PASSWORD_NOT_IMPORTED));
        }

        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private void restoreBackup(GeneralSettingsBackup backup) throws IOException {
        deviceScanningService.setScanningInterval(backup.getDeviceScanningInterval());
        deviceFactory.setAutoEnableNewDevices(backup.isAutoEnableNewDevices());
        squidWarningService.setRecordingFailedConnectionsEnabled(backup.isSquidWarningServiceEnabled());
        settingsService.setLocaleSettings(backup.getLocaleSettings());

        byte[] pwHash = backup.getConsolePasswordHash();
        if (pwHash != null) {
            dataSource.setPasswordHash(pwHash);
        } else {
            LOG.warn("Backup does not contain a console password hash.");
        }
    }
}
