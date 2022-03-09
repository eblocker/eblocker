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
import com.google.inject.name.Named;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.eblocker.server.common.data.ConfigBackupReference;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DecryptionFailedException;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.eblocker.server.http.controller.ConfigurationBackupController;
import org.eblocker.server.http.service.ConfigurationBackupService;
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
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.nio.file.StandardCopyOption;

public class ConfigurationBackupControllerImpl implements ConfigurationBackupController {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupControllerImpl.class);
    public static final String FILE_PREFIX = "eblocker-config-";
    public static final String FILE_SUFFIX = ".eblcfg";
    private final ConfigurationBackupService backupService;
    private final Path tmpDir;

    @Inject
    public ConfigurationBackupControllerImpl(ConfigurationBackupService backupService, @Named("tmpDir") String tmpDir) {
        this.backupService = backupService;
        this.tmpDir = Paths.get(tmpDir);
    }

    /**
     * Writes a backup of the settings to a temporary file and returns a reference for immediate
     * download to the client.
     * @param request
     * @param response
     * @return
     */
    @Override
    public ConfigBackupReference exportConfiguration(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        try {
            Path tempFile = createTempFile();
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                backupService.exportConfiguration(outputStream, reference.getPassword());
                LOG.debug("Successfully exported configuration backup to {}", tempFile);
            }
            return new ConfigBackupReference(tempFile.getFileName().toString(), null, reference.isPasswordRequired());
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
        Path localFile = getVerifiedLocalPath(fileReference);
        String timestamp = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());
        response.addHeader("Content-Disposition", "attachment; filename=\"" + FILE_PREFIX + timestamp + FILE_SUFFIX + "\"");
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
            Path tempFile = createTempFile();
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
     * Imports a previously uploaded configuration backup
     * @param request
     * @param response
     */
    @Override
    public void importConfiguration(Request request, Response response) {
        ConfigBackupReference reference = request.getBodyAs(ConfigBackupReference.class);
        if (reference == null) {
            String message = "ConfigBackupReference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        Path localFile = getVerifiedLocalPath(reference.getFileReference());
        try (InputStream inputStream = Files.newInputStream(localFile)) {
            backupService.importConfiguration(inputStream, reference.getPassword());
            LOG.info("Successfully imported configuration backup");
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

    private Path createTempFile() throws IOException {
        Path tempFile = Files.createTempFile(tmpDir, FILE_PREFIX, FILE_SUFFIX);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private Path getVerifiedLocalPath(String fileReference) {
        if (fileReference == null || fileReference.isEmpty()) {
            String message = "Config backup file reference is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        Path filename = Paths.get(fileReference).getFileName(); // protect against relative paths with '..' components
        if (!filename.toString().startsWith(FILE_PREFIX) || !filename.toString().endsWith(FILE_SUFFIX)) {
            String message = "Invalid backup file name: " + filename;
            LOG.error(message);
            throw new BadRequestException(message);
        }
        Path localFile = tmpDir.resolve(filename);
        return localFile;
    }
}
