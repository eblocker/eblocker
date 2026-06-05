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
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.registration.DeviceRegistrationExport;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class RegistrationBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationBackupProvider.class);
    public static final String REGISTRATION_ENTRY = "eblocker-config/registration.json";
    private final DeviceRegistrationProperties deviceRegistrationProperties;

    @Inject
    public RegistrationBackupProvider(DeviceRegistrationProperties deviceRegistrationProperties,
                                      @Assisted @Nullable CryptoService cryptoService) {
        super(cryptoService);
        this.deviceRegistrationProperties = deviceRegistrationProperties;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        DeviceRegistrationExport export;

        if (!canEncrypt()) {
            LOG.error("Cannot encrypt registration");
            throw new EncryptionUnavailableException("Cannot encrypt registration");
        }

        try {
            export = deviceRegistrationProperties.exportRegistration();
        } catch (CryptoException e) {
            LOG.error("Failed to export registration", e);
            addWarning(new BackupWarning(BackupWarning.Id.LICENSE_CRYPTO_FAILURE));
            export = new DeviceRegistrationExport();
        }
        writeNextEntry(outputStream, REGISTRATION_ENTRY, objectMapper.writeValueAsBytes(export));
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
        getNextEntry(inputStream, REGISTRATION_ENTRY);
        DeviceRegistrationExport backup = objectMapper.readValue(inputStream, DeviceRegistrationExport.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        if (!canDecrypt()) {
            addWarning(new BackupWarning(BackupWarning.Id.NO_PASSWORD_REGISTRATION_NOT_IMPORTED));
            return;
        }

        if (!dryRun) {
            try {
                restoreBackup(backup);
            } catch (CryptoException e) {
                LOG.error("Failed to restore backup", e);
                addWarning(new BackupWarning(BackupWarning.Id.LICENSE_CRYPTO_FAILURE));
            }
        }
    }

    private void restoreBackup(DeviceRegistrationExport backup) throws CryptoException, IOException {
        deviceRegistrationProperties.importRegistration(backup);
    }
}
