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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.backup.GeneralSettingsBackup;
import org.eblocker.server.http.backup.RestoreBackupReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service for importing settings from a locally mounted drive.
 *
 * It first restores the network and language settings, so the user
 * has access to the settings and can enter the backup's password to
 * import the rest of the settings.
 */
@Singleton
@SubSystemService(value = SubSystem.RESTORE_FROM_BACKUP, initPriority = 100)
public class RestoreFromBackupService {
    private static final Logger LOG = LoggerFactory.getLogger(RestoreFromBackupService.class);
    private boolean backupAvailable = false;
    private final ConfigurationBackupFileService backupFileService;
    private final RestoreBackupReader restoreBackupReader;
    private final NetworkStateMachine networkStateMachine;
    private final SettingsService settingsService;
    private final Path mountpoint;
    private final Path backupPath;

    @Inject
    public RestoreFromBackupService(
            ConfigurationBackupFileService backupFileService,
            RestoreBackupReader restoreBackupReader,
            NetworkStateMachine networkStateMachine,
            SettingsService settingsService,
            @Named("external.disk.mountpoint") String mountpoint,
            @Named("external.disk.backup.filename") String backupFilename
            ) {
        this.backupFileService = backupFileService;
        this.restoreBackupReader = restoreBackupReader;
        this.networkStateMachine = networkStateMachine;
        this.settingsService = settingsService;
        this.mountpoint = Paths.get(mountpoint);
        this.backupPath = this.mountpoint.resolve(backupFilename);
    }

    @SubSystemInit
    public void initialize() {
        if (Files.exists(backupPath)) {
            backupAvailable = true;
        } else {
            LOG.info("No backup file found at {}. Not restoring from backup.", backupPath);
        }

        if (backupAvailable) {
            try {
                // Restore network configuration
                NetworkConfiguration networkConfiguration = restoreBackupReader.readNetworkConfiguration(backupPath);
                if (networkConfiguration != null) {
                    networkStateMachine.updateConfiguration(networkConfiguration);
                } else {
                    LOG.error("Could not read network configuration from backup file {}", backupPath);
                }
            } catch (IOException e) {
                LOG.error("Could not import network configuration from backup file {}", backupPath, e);
            }

            try {
                // Restore language / timezone
                GeneralSettingsBackup generalSettings = restoreBackupReader.readGeneralSettings(backupPath);
                if (generalSettings != null) {
                    settingsService.setLocaleSettings(generalSettings.getLocaleSettings());
                } else {
                    LOG.error("Could not read locale settings from backup file {}", backupPath);
                }
            } catch (IOException e) {
                LOG.error("Could not import locale settings from backup file {}", backupPath, e);
            }
        }
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public boolean isBackupAvailable() {
        return backupAvailable;
    }

    public boolean cancelImport() {
        LOG.warn("Backup import from external disk was cancelled by admin!");
        backupAvailable = false;
        try {
            backupFileService.unmountExternalDisk();
        } catch (IOException | InterruptedException e) {
            LOG.error("Could not unmount external disk while cancelling import.");
            return false;
        }
        return true;
    }

    /**
     * Moves the backup out of the way, so it is not imported again
     * @param imported true if the import was successful, false if an error occurred.
     * @return true if the file was moved, false if there was an error
     */
    public boolean renameBackup(boolean imported) {
        String targetFilename = backupFileService.getTimestampedFilename(imported ? "imported" : "failed");
        Path target = mountpoint.resolve(targetFilename);
        try {
            Files.move(backupPath, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("Could not rename {} to {}", backupPath, target, e);
            return false;
        }
    }
}
