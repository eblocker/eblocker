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
package org.eblocker.server.http.backup;

import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

@SuppressWarnings("squid:S2187")
public class BackupProviderTestBase {
    private static final byte[] salt = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    protected void exportVerifyImport(BackupProvider provider) throws IOException {
        byte[] exported = exportBackup(provider);
        verifyBackup(exported, provider);
        importBackup(exported, provider);
    }

    protected byte[] exportBackup(BackupProvider provider) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            provider.exportConfiguration(jarOutputStream);
        }
        return outputStream.toByteArray();
    }

    protected void verifyBackup(byte[] data, BackupProvider provider) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        try (JarInputStream jarInputStream = new JarInputStream(inputStream)) {
            provider.verifyConfiguration(jarInputStream, 42);
        }
    }

    protected void importBackup(byte[] data, BackupProvider provider) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        try (JarInputStream jarInputStream = new JarInputStream(inputStream)) {
            provider.importConfiguration(jarInputStream, 42);
        }
    }

    protected CryptoService createCryptoService(String password) throws IOException {
        if (password == null) {
            return null;
        }
        try {
            return CryptoServiceFactory.getInstance().setSaltedPassword(password.toCharArray(), salt).build();
        } catch (Exception e) {
            throw new IOException("Could not create CryptoService", e);
        }
    }
}
