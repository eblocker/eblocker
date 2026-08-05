/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.EncryptedData;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.ssl.SslService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Backup of HTTPS keys and certificates.
 */
public class HttpsKeysBackupProvider extends BackupProvider {
    public static final String HTTPS_KEYS_ENTRY = "eblocker-config/httpsKeys.json";
    private static final Logger LOG = LoggerFactory.getLogger(HttpsKeysBackupProvider.class);

    private final SslService sslService;
    private final CryptoService cryptoService;

    /**
     * The provider has its own CryptoService because it does not use the @JsonEncrypt annotation.
     * So it does not call the super(cryptoService) constructor.
     * @param sslService
     * @param cryptoService
     */
    @AssistedInject
    public HttpsKeysBackupProvider(SslService sslService, @Assisted @Nullable CryptoService cryptoService) {
        this.sslService = sslService;
        this.cryptoService = cryptoService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        HttpsKeysBackup backup;
        try {
            backup = exportHttpsKeys(cryptoService);
        } catch (CryptoException e) {
            throw new IOException("Could not encrypt HTTPS keys", e);
        }
        writeNextEntry(outputStream, HTTPS_KEYS_ENTRY, objectMapper.writeValueAsBytes(backup));
    }

    private HttpsKeysBackup exportHttpsKeys(CryptoService cryptoService) throws CryptoException, IOException {
        HttpsKeysBackup backup = new HttpsKeysBackup();
        if (cryptoService != null) {
            byte[] caBytes = sslService.exportCa();
            if (caBytes != null) {
                backup.setEncryptedCA(cryptoService.encrypt(caBytes));
            }
            byte[] renewalCaBytes = sslService.exportRenewalCa();
            if (renewalCaBytes != null) {
                backup.setEncryptedRenewalCA(cryptoService.encrypt(renewalCaBytes));
            }
            backup.setHttpsEnabledState(sslService.isSslEnabled());
        } else {
            LOG.error("cryptoService is null. Cannot encrypt HTTPS keys");
            throw new EncryptionUnavailableException("No password provided, cannot encrypt HTTPS keys");
        }
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
        byte[] caBytes = null;
        byte[] renewalCaBytes = null;
        getNextEntry(inputStream, HTTPS_KEYS_ENTRY);
        HttpsKeysBackup backup = objectMapper.readValue(inputStream, HttpsKeysBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }
        if (cryptoService == null) {
            LOG.warn("No password provided, so CAs are not imported");
            addWarning(new BackupWarning(BackupWarning.Id.NO_PASSWORD_HTTPS_CA_NOT_IMPORTED));
            return;
        }
        try {
            EncryptedData data = backup.getEncryptedCA();
            if (data != null) {
                caBytes = cryptoService.decrypt(data);
                LOG.info("Decrypted {} bytes of the CA", caBytes.length);
            }
            data = backup.getEncryptedRenewalCA();
            if (data != null) {
                renewalCaBytes = cryptoService.decrypt(data);
                LOG.info("Decrypted {} bytes of the renewal CA", renewalCaBytes.length);
            }
        } catch (CryptoException e) {
            LOG.error("Could not decrypt HTTPS keys", e);
            throw new DecryptionFailedException("Could not decrypt HTTPS keys");
        }
        if (!dryRun) {
            sslService.importCas(caBytes, renewalCaBytes);

            Boolean httpsEnabledState = backup.getHttpsEnabledState();
            if (httpsEnabledState != null) { // only available in backup version 5 and later
                if (httpsEnabledState) {
                    sslService.enableSsl();
                } else {
                    sslService.disableSsl();
                }
            }
        }
    }
}
