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
import com.google.inject.name.Named;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Provides utility functions for handling backup files.
 */
public class ConfigurationBackupFileService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupFileService.class);
    public static final String FILE_PREFIX = "eblocker-config";
    public static final String FILE_SUFFIX = ".eblcfg";

    private final Path tmpDir;

    @Inject
    public ConfigurationBackupFileService(@Named("tmpDir") String tmpDir) {
        this.tmpDir = Paths.get(tmpDir);
    }

    public Path createTempFile() throws IOException {
        Path tempFile = Files.createTempFile(tmpDir, FILE_PREFIX, FILE_SUFFIX);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    public Path getVerifiedLocalPath(String fileReference) {
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

    public String getTimestampedFilename(@Nullable String optionalLabel) {
        String beforeTimestamp = "-";
        if (optionalLabel != null) {
            beforeTimestamp += optionalLabel + "-";
        }
        String timestamp = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());
        return FILE_PREFIX + beforeTimestamp + timestamp + FILE_SUFFIX;
    }
}
