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

import com.google.inject.Inject;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.EncryptedData;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.ssl.SslService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class HttpsKeysBackupProvider extends BackupProvider {
    public static final String HTTPS_KEYS_ENTRY = "eblocker-config/httpsKeys.json";
    private static final Logger LOG = LoggerFactory.getLogger(HttpsKeysBackupProvider.class);

    private final SslService sslService;

    @Inject
    public HttpsKeysBackupProvider(SslService sslService) {
        this.sslService = sslService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream, CryptoService cryptoService) throws IOException {
        HttpsKeysBackup backup;
        try {
            backup = exportHttpsKeys(cryptoService);
        } catch (CryptoException e) {
            throw new IOException("Could not encrypt HTTPS keys", e);
        }
        JarEntry entry = new JarEntry(HTTPS_KEYS_ENTRY);
        outputStream.putNextEntry(entry);
        outputStream.write(objectMapper.writeValueAsBytes(backup));
        outputStream.closeEntry();
    }

    private HttpsKeysBackup exportHttpsKeys(CryptoService cryptoService) throws CryptoException, IOException {
        HttpsKeysBackup backup = new HttpsKeysBackup();
        byte[] caBytes = sslService.exportCa();
        if (caBytes != null) {
            backup.setEncryptedCA(cryptoService.encrypt(caBytes));
        }
        byte[] renewalCaBytes = sslService.exportRenewalCa();
        if (renewalCaBytes != null) {
            backup.setEncryptedRenewalCA(cryptoService.encrypt(renewalCaBytes));
        }
        return backup;
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, CryptoService cryptoService, int schemaVersion) throws IOException {
        HttpsKeysBackup backup;
        byte[] caBytes = null;
        byte[] renewalCaBytes = null;
        JarEntry entry = inputStream.getNextJarEntry();
        if (entry.getName().equals(HTTPS_KEYS_ENTRY)) {
            backup = objectMapper.readValue(inputStream, HttpsKeysBackup.class);
            inputStream.closeEntry();
        } else {
            throw new EblockerException("Expected entry " + HTTPS_KEYS_ENTRY + ", got " + entry.getName());
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
            throw new IOException("Could not decrypt HTTPS keys", e);
        }
        sslService.importCas(caBytes, renewalCaBytes);
    }
}
