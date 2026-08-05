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

import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HttpsKeysBackupProviderTest extends BackupProviderTestBase {
    private HttpsKeysBackupProvider provider;
    private HttpsKeysBackupProvider providerNoPassword;
    private HttpsKeysBackupProvider providerBadPassword;
    private SslService sslService;

    @BeforeEach
    public void setUp() throws Exception {
        sslService = Mockito.mock(SslService.class);
        provider = new HttpsKeysBackupProvider(sslService, createCryptoService("top secret"));
        providerNoPassword = new HttpsKeysBackupProvider(sslService, null);
        providerBadPassword = new HttpsKeysBackupProvider(sslService, createCryptoService("wrong!"));
    }

    @Test
    public void testExportImport() throws IOException {
        byte[] caBytes = "This is the CA".getBytes();
        byte[] renewalCaBytes = "This is the renewal CA".getBytes();
        Mockito.when(sslService.exportCa()).thenReturn(caBytes);
        Mockito.when(sslService.exportRenewalCa()).thenReturn(renewalCaBytes);

        exportVerifyImport(provider);

        Mockito.verify(sslService).importCas(caBytes, renewalCaBytes);
        assertEquals(0, provider.getWarnings().size());
    }

    @Test
    public void noPasswordForExport() throws IOException {
        byte[] caBytes = "This is the CA".getBytes();
        Mockito.when(sslService.exportCa()).thenReturn(caBytes);
        assertThrows(EncryptionUnavailableException.class, () -> exportBackup(providerNoPassword));
    }

    @Test
    public void noPasswordForImport() throws IOException {
        byte[] caBytes = "This is the CA".getBytes();
        Mockito.when(sslService.exportCa()).thenReturn(caBytes);

        byte[] backup = exportBackup(provider);
        assertEquals(0, provider.getWarnings().size());

        importBackup(backup, providerNoPassword);
        assertEquals(List.of(new BackupWarning(BackupWarning.Id.NO_PASSWORD_HTTPS_CA_NOT_IMPORTED)), providerNoPassword.getWarnings());
    }

    @Test
    public void wrongPassword() throws IOException {
        byte[] caBytes = "This is the CA".getBytes();
        Mockito.when(sslService.exportCa()).thenReturn(caBytes);

        byte[] backup = exportBackup(provider);

        assertThrows(DecryptionFailedException.class, () -> {
            importBackup(backup, providerBadPassword);
        });
    }

    @Test
    public void enableHttps() throws IOException {
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        byte[] backup = exportBackup(provider);
        importBackup(backup, provider);
        Mockito.verify(sslService).enableSsl();
    }

    @Test
    public void disableHttps() throws IOException {
        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        byte[] backup = exportBackup(provider);
        importBackup(backup, provider);
        Mockito.verify(sslService).disableSsl();
    }

    @Test
    public void importVersion4() throws IOException {
        // before version 5 the httpsEnabledState was not set
        byte[] backup = ResourceHandler.getClassPathInputStream("test-data/backup/HttpsKeysBackupV4.zip").readAllBytes();
        importBackup(backup, provider);
        Mockito.verify(sslService, Mockito.never()).enableSsl();
        Mockito.verify(sslService, Mockito.never()).disableSsl();
    }
}
