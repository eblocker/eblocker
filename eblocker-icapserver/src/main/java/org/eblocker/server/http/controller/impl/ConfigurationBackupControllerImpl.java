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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.eblocker.server.common.data.BlockDevice;
import org.eblocker.server.common.data.backup.ConfigBackupExportResult;
import org.eblocker.server.common.data.backup.ConfigBackupImportResult;
import org.eblocker.server.common.data.backup.ConfigBackupReference;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DecryptionFailedException;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.eblocker.server.http.controller.ConfigurationBackupController;
import org.eblocker.server.http.service.ConfigurationBackupFileService;
import org.eblocker.server.http.service.ConfigurationBackupService;
import org.eblocker.server.http.service.DiskInfoService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.StandardCopyOption;

public class ConfigurationBackupControllerImpl implements ConfigurationBackupController {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupControllerImpl.class);
    private final ConfigurationBackupService backupService;
    private final ConfigurationBackupFileService backupFileService;
    private final DiskInfoService diskInfoService;
    private final EventLogger eventLogger;

    @Inject
    public ConfigurationBackupControllerImpl(ConfigurationBackupService backupService,
                                             ConfigurationBackupFileService backupFileService,
                                             DiskInfoService diskInfoService,
                                             EventLogger eventLogger
                                             ) {
        this.backupService = backupService;
        this.backupFileService = backupFileService;
        this.diskInfoService = diskInfoService;
        this.eventLogger = eventLogger;
    }

    /**
     * Writes a backup of the settings to a temporary file and returns a reference for immediate
     * download to the client.
     * @param request
     * @param response
     * @return
     */
    @Override
    public ConfigBackupExportResult exportConfiguration(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        try {
            Path tempFile = backupFileService.createTempFile();
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                ConfigBackupExportResult result = backupService.exportConfiguration(outputStream, reference.getPassword());
                LOG.debug("Successfully exported configuration backup to {}", tempFile);
                result.setConfigBackupReference(new ConfigBackupReference(tempFile.getFileName().toString(), null, reference.isPasswordRequired()));
                return result;
            }
        } catch (Exception e) {
            LOG.error("Could not export configuration to local file", e);
            throw new EblockerException("adminconsole.config_backup.error.export_failure");
        }
    }

    /**
     * Returns a previously exported configuration backup
     * @param request
     * @param response
     * @return
     */
    @Override
    public ByteBuf downloadConfiguration(Request request, Response response) {
        String fileReference = request.getHeader("configBackupFileReference");
        Path localFile = backupFileService.getVerifiedLocalPath(fileReference);
        String timestampedFilename = backupFileService.getTimestampedFilename(null);
        response.addHeader("Content-Disposition", "attachment; filename=\"" + timestampedFilename + "\"");
        response.setContentType("application/octet-stream");
        try {
            byte[] bytes = Files.readAllBytes(localFile);
            LOG.debug("Read {} bytes of configuration backup from {}", bytes.length, localFile);
            return Unpooled.wrappedBuffer(bytes);
        } catch (Exception e) {
            LOG.error("Could not read exported backup file '{}' from disk", localFile, e);
            throw new EblockerException("adminconsole.config_backup.error.download_failure");
        }
    }

    /**
     * Saves an uploaded configuration backup and returns whether a password is required to recover keys
     * @param request
     * @param response
     * @return
     */
    @Override
    public ConfigBackupReference uploadConfiguration(Request request, Response response) {
        try (InputStream inputStream = request.getBodyAsStream()) {
            Path tempFile = backupFileService.createTempFile();
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            LOG.debug("Wrote uploaded backup file to: {}", tempFile);
            try (InputStream localInputStream = Files.newInputStream(tempFile)) {
                boolean passwordRequired = backupService.requiresPassword(localInputStream);
                return new ConfigBackupReference(tempFile.getFileName().toString(), null, passwordRequired);
            }
        } catch (Exception e) {
            LOG.error("Could not write uploaded backup to disk", e);
            throw new EblockerException("adminconsole.config_backup.error.upload_failure");
        }
    }

    /**
     * Verifies a previously uploaded configuration backup
     * @param request
     * @param response
     */
    @Override
    public ConfigBackupImportResult verifyConfiguration(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        Path localFile = backupFileService.getVerifiedLocalPath(reference.getFileReference());
        try (InputStream inputStream = Files.newInputStream(localFile)) {
            ConfigBackupImportResult result = backupService.verifyConfiguration(inputStream, reference.getPassword());
            if (result.hasWarnings()) {
                LOG.warn("Verified configuration backup with warnings: {}", result.getWarnings());
            } else {
                LOG.info("Successfully verified configuration backup");
            }
            return result;
        } catch (CorruptedBackupException e) {
            LOG.error("Could not verify corrupted backup", e);
            throw new BadRequestException("adminconsole.config_backup.error.corrupted");
        } catch (UnsupportedBackupVersionException e) {
            LOG.error("Could not verify backup with unsupported version", e);
            throw new BadRequestException("adminconsole.config_backup.error.unsupported_version");
        } catch (DecryptionFailedException e) {
            LOG.error("Could not verify backup due to invalid password", e);
            throw new BadRequestException("adminconsole.config_backup.error.invalid_password");
        } catch (Exception e) {
            LOG.error("Could not verify backup", e);
            throw new EblockerException("adminconsole.config_backup.error.verification_failure");
        }
    }

    /**
     * Imports a previously uploaded configuration backup
     * @param request
     * @param response
     */
    @Override
    public ConfigBackupImportResult importConfiguration(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        Path localFile = backupFileService.getVerifiedLocalPath(reference.getFileReference());
        try (InputStream inputStream = Files.newInputStream(localFile)) {
            ConfigBackupImportResult result = backupService.importConfiguration(inputStream, reference.getPassword());
            if (result.hasWarnings()) {
                LOG.warn("Imported configuration backup with warnings: {}", result.getWarnings());
            } else {
                LOG.info("Successfully imported configuration backup");
            }
            String clientFileName = reference.getClientFileName();
            eventLogger.log(Events.configurationBackupRestored(clientFileName != null ? clientFileName : "unknown file"));
            return result;
        } catch (CorruptedBackupException e) {
            LOG.error("Could not import corrupted backup", e);
            throw new BadRequestException("adminconsole.config_backup.error.corrupted");
        } catch (UnsupportedBackupVersionException e) {
            LOG.error("Could not import backup with unsupported version", e);
            throw new BadRequestException("adminconsole.config_backup.error.unsupported_version");
        } catch (DecryptionFailedException e) {
            LOG.error("Could not import backup due to invalid password", e);
            throw new BadRequestException("adminconsole.config_backup.error.invalid_password");
        } catch (Exception e) {
            LOG.error("Could not import backup", e);
            throw new EblockerException("adminconsole.config_backup.error.import_failure");
        }
    }

    @Override
    public String getMountedPartitionName(Request request, Response response) {
        try {
            BlockDevice mountedPartition = diskInfoService.getMountedPartition();
            if (mountedPartition != null) {
                return mountedPartition.getFriendlyName();
            }
        } catch (IOException e) {
            LOG.error("Could not search mounted partition", e);
            throw new EblockerException("adminconsole.config_backup.error.disk_error");
        }
        return null;
    }

    @Override
    public void moveToExternalDisk(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }

        try {
            backupFileService.moveToExternalDisk(reference.getFileReference());
        } catch (IOException e) {
            LOG.error("Could not move backup {} to external disk", reference.getFileReference(), e);
            throw new EblockerException("adminconsole.config_backup.error.disk_error");
        }

        try {
            backupFileService.unmountExternalDisk();
        } catch (IOException | InterruptedException e) {
            LOG.error("Could not unmount external disk", e);
            throw new EblockerException("adminconsole.config_backup.error.disk_error");
        }
    }
}
