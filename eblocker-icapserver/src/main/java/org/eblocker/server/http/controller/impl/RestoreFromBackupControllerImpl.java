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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.common.data.backup.ConfigBackupImportResult;
import org.eblocker.server.common.data.backup.RestoreBackupCredentials;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DecryptionFailedException;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.eblocker.server.http.controller.RestoreFromBackupController;
import org.eblocker.server.http.service.ConfigurationBackupService;
import org.eblocker.server.http.service.RestoreFromBackupService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;

/**
 * Restores a backup from a local mounted drive.
 * This is used to migrate the settings to a new eOS image.
 */
public class RestoreFromBackupControllerImpl implements RestoreFromBackupController {
    private static final Logger LOG = LoggerFactory.getLogger(RestoreFromBackupControllerImpl.class);
    private final RestoreFromBackupService restoreFromBackupService;
    private final ConfigurationBackupService configurationBackupService;
    private final EventLogger eventLogger;

    @Inject
    public RestoreFromBackupControllerImpl(RestoreFromBackupService restoreFromBackupService,
                                           ConfigurationBackupService configurationBackupService,
                                           EventLogger eventLogger) {
        this.restoreFromBackupService = restoreFromBackupService;
        this.configurationBackupService = configurationBackupService;
        this.eventLogger = eventLogger;
    }

    @Override
    public boolean isBackupAvailable(Request request, Response response) {
        return restoreFromBackupService.isBackupAvailable();
    }

    @Override
    public ConfigBackupImportResult importConfiguration(Request request, Response response) {
        String password = getPassword(request);
        boolean imported = false;
        try (InputStream inputStream = Files.newInputStream(restoreFromBackupService.getBackupPath())) {
            ConfigBackupImportResult result = configurationBackupService.importConfiguration(inputStream, password);
            if (result.hasWarnings()) {
                LOG.warn("Imported configuration backup with warnings: {}", result.getWarnings());
            } else {
                LOG.info("Successfully imported configuration backup");
            }
            imported = true;
            eventLogger.log(Events.configurationBackupRestored("RESTORE-FILE"));
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
        } finally {
            restoreFromBackupService.renameBackup(imported);
        }
    }

    @Override
    public ConfigBackupImportResult verifyConfiguration(Request request, Response response) {
        String password = getPassword(request);
        try (InputStream inputStream = Files.newInputStream(restoreFromBackupService.getBackupPath())) {
            ConfigBackupImportResult result = configurationBackupService.verifyConfiguration(inputStream, password);
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

    private String getPassword(Request request) {
        RestoreBackupCredentials credentials = request.getBodyAs(RestoreBackupCredentials.class);
        if (credentials == null) {
            String message = "Credentials object is missing from request";
            LOG.error(message);
            throw new BadRequestException(message);
        }
        return credentials.getPassword();
    }
}
