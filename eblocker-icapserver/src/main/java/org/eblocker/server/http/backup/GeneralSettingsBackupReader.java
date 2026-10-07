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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Reads general settings from a backup during migration via USB drive.
 */
public class GeneralSettingsBackupReader extends BackupSerializer {
    /**
     * Skips entries in a backup file until it finds the entry "eblocker-config/general.json".
     * @param backupFile backup file to read entries from.
     * @return GeneralSettingsBackup object
     * @throws IOException is thrown if the backup file cannot be read or the JSON object cannot be parsed.
     */
    public GeneralSettingsBackup readGeneralSettings(Path backupFile) throws IOException {
        JarEntry entry;
        try (InputStream inputStream = Files.newInputStream(backupFile)) {
            try (JarInputStream jarStream = new JarInputStream(inputStream)) {
                while ((entry = jarStream.getNextJarEntry()) != null) {
                    if (GeneralSettingsBackupProvider.GENERAL_ENTRY.equals(entry.getName())) {
                        return objectMapper.readValue(jarStream, GeneralSettingsBackup.class);
                    }
                }
                return null;
            }
        }
    }
}
