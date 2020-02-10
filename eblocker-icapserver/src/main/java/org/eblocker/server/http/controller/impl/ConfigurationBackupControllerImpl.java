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

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.eblocker.server.http.controller.ConfigurationBackupController;
import org.eblocker.server.http.service.ConfigurationBackupService;
import com.google.inject.Inject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

public class ConfigurationBackupControllerImpl implements ConfigurationBackupController {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupControllerImpl.class);
    private static final int CURRENT_VERSION = 1;
    private final ConfigurationBackupService backupService;

    @Inject
    public ConfigurationBackupControllerImpl(ConfigurationBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    public ByteBuf exportConfiguration(Request request, Response response) {
        response.addHeader("Content-Disposition", "attachment; filename=\"eblocker-config.eblcfg\"");
        response.setContentType("application/octet-stream");
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(10000);
        try (OutputStream outputStream = new ByteBufOutputStream(buffer)) {
            backupService.exportConfiguration(outputStream);
            LOG.info("Successfully exported configuration backup");
        } catch (Exception e) {
            LOG.error("Could not write backup", e);
            buffer.release();
            throw new EblockerException("Could not write backup");
        }
        return  buffer;
    }

    @Override
    public void importConfiguration(Request request, Response response) {
        try (InputStream inputStream = request.getBodyAsStream()) {
            backupService.importConfiguration(inputStream);
            LOG.info("Successfully imported configuration backup");
        } catch (CorruptedBackupException e) {
            LOG.error("Could not import corrupted backup", e);
            throw new BadRequestException("adminconsole.config_backup.error.corrupted");
        } catch (UnsupportedBackupVersionException e) {
            LOG.error("Could not import backup with unsupported version", e);
            throw new BadRequestException("adminconsole.config_backup.error.unsupported_version");
        } catch (Exception e) {
            LOG.error("Could not import backup", e);
            throw new EblockerException("adminconsole.config_backup.error.import_failure");
        }
    }
}
