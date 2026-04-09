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

import com.google.common.base.Charsets;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.registration.DeviceRegistrationExport;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationBackupProviderTest extends BackupProviderTestBase {
    private RegistrationBackupProvider provider;
    private RegistrationBackupProvider providerNoPassword;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private static final String keyStorePassword = "secret_password";

    @BeforeEach
    void setUp() throws Exception {
        setUpProperties();
        provider = new RegistrationBackupProvider(deviceRegistrationProperties, createCryptoService("top secret!!!"));
        providerNoPassword = new RegistrationBackupProvider(deviceRegistrationProperties, null);
    }

    private void setUpProperties() throws Exception {
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        DeviceRegistrationExport export = new DeviceRegistrationExport();
        export.setKeyStorePassword("secret password".toCharArray());
        Mockito.when(deviceRegistrationProperties.exportRegistration()).thenReturn(export);
    }

    @Test
    void keyStorePasswordIsEncrypted() throws IOException {
        byte[] backup = exportBackup(provider);
        JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(backup));
        jarInputStream.getNextEntry();
        String rawJson = new String(jarInputStream.readAllBytes(), Charsets.UTF_8);
        // JSON contains the key of the password, but not the password itself:
        assertTrue(rawJson.contains("keyStorePassword"));
        assertFalse(rawJson.contains(keyStorePassword));
    }

    @Test
    public void noPasswordForExport() {
        assertThrows(EncryptionUnavailableException.class, () -> exportBackup(providerNoPassword));
    }

    @Test
    public void noPasswordForImport() throws Exception {
        byte[] backup = exportBackup(provider);
        importBackup(backup, providerNoPassword);
        assertEquals(List.of(BackupWarning.NO_PASSWORD_REGISTRATION_NOT_IMPORTED), providerNoPassword.getWarnings());
    }

    @Test
    public void testExportVerifyImport() throws IOException {
        exportVerifyImport(provider);
    }
}