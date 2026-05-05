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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.backup.ConfigBackupImportResult;
import org.eblocker.server.http.backup.BackupAttributes;
import org.eblocker.server.http.backup.BackupProvider;
import org.eblocker.server.http.backup.BackupProviderFactory;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * This service exports/imports configuration to/from a JAR file.
 * <p>
 * The configuration version is written to the JAR's manifest file.
 */
public class ConfigurationBackupService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private static final int VERSION_1_ONLY_APP_MODULES = 1;
    private static final int VERSION_2_APP_MODULES_AND_DEVICES = 2;
    private static final int VERSION_3_APP_MODULES_DEVICES_TOR = 3;
    private static final int VERSION_4_WITH_KEYS = 4;
    private static final int VERSION_5_FULL = 5;
    private static final int CURRENT_VERSION = VERSION_5_FULL;
    private static final int MIN_VERSION = VERSION_1_ONLY_APP_MODULES;
    private static final int MAX_VERSION = CURRENT_VERSION;
    private static final byte[] salt = {-58, -73, 41, -28, 37, 23, -61, 93, 47, -57, -45, 23, -77, 97, 102, 49};
    private final BackupProviderFactory providerFactory;

    private DataSource dataSource;

    @Inject
    public ConfigurationBackupService(DataSource dataSource, BackupProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
        this.dataSource = dataSource;
    }

    private List<BackupProvider> createBackupProviders(int version, @Nullable CryptoService cryptoService) {
        switch (version) {
            case VERSION_1_ONLY_APP_MODULES:
                return List.of(providerFactory.createAppModulesBackupProvider());

            case VERSION_2_APP_MODULES_AND_DEVICES:
                return List.of(
                        providerFactory.createAppModulesBackupProvider(),
                        providerFactory.createDevicesLegacyBackupProvider());

            case VERSION_3_APP_MODULES_DEVICES_TOR:
                return List.of(
                        providerFactory.createAppModulesBackupProvider(),
                        providerFactory.createDevicesLegacyBackupProvider(),
                        providerFactory.createTorConfigBackupProvider());

            case VERSION_4_WITH_KEYS:
                return List.of(
                        providerFactory.createHttpsKeysBackupProvider(cryptoService), // fail early if the password is wrong!
                        providerFactory.createAppModulesBackupProvider(),
                        providerFactory.createDevicesLegacyBackupProvider(),
                        providerFactory.createTorConfigBackupProvider());

            case VERSION_5_FULL:
                return List.of(
                        providerFactory.createHttpsKeysBackupProvider(cryptoService),
                        providerFactory.createAppModulesBackupProvider(),
                        providerFactory.createTorConfigBackupProvider(),
                        providerFactory.createOpenVpnServerBackupProvider(cryptoService),
                        providerFactory.createOpenVpnClientBackupProvider(cryptoService),
                        providerFactory.createRegistrationBackupProvider(cryptoService),
                        providerFactory.createUsersBackupProvider());

            default:
                throw new UnsupportedBackupVersionException(version);
        }
    }

    /**
     * Exports the configuration to the given OutputStream. The user can provide a password in order to
     * export private keys.
     * @param outputStream
     * @param password
     * @throws IOException
     */
    public void exportConfiguration(OutputStream outputStream, String password) throws IOException {
        Manifest manifest = new Manifest();
        BackupAttributes attribs = getBackupAttributes(password != null);
        attribs.addToAttributes(manifest.getMainAttributes());

        CryptoService cryptoService = createCryptoService(password);

        try (JarOutputStream jarStream = new JarOutputStream(outputStream, manifest)) {
            for (BackupProvider provider : createBackupProviders(CURRENT_VERSION, cryptoService)) {
                provider.exportConfiguration(jarStream);
            }
        }
    }

    /**
     * Reads the manifest from the given InputStream and returns whether a password is required
     * to read the contained private keys
     * @param inputStream
     * @return
     * @throws IOException
     */
    public boolean requiresPassword(InputStream inputStream) throws IOException {
        try (JarInputStream jarStream = new JarInputStream(inputStream)) {
            Manifest manifest = jarStream.getManifest();
            BackupAttributes attribs = getVerifiedAttributes(manifest);
            return attribs.isPasswordRequired();
        }
    }

    /**
     * Verifies the configuration from the given InputStream. If the user has provided a password,
     * private keys are verified.
     * @param inputStream
     * @param password
     * @throws IOException
     */
    public ConfigBackupImportResult verifyConfiguration(InputStream inputStream, String password) throws IOException {
        ConfigBackupImportResult result = new ConfigBackupImportResult();
        try (JarInputStream jarStream = new JarInputStream(inputStream)) {
            Manifest manifest = jarStream.getManifest();
            BackupAttributes attribs = getVerifiedAttributes(manifest);
            CryptoService cryptoService = createCryptoService(password);

            for (BackupProvider provider : createBackupProviders(attribs.getVersion(), cryptoService)) {
                provider.verifyConfiguration(jarStream, attribs.getSchemaVersion());
                result.addWarnings(provider.getWarnings());
            }
        }
        return result;
    }

    /**
     * Imports the configuration from the given InputStream. If the user has provided a password,
     * private keys are imported.
     * @param inputStream
     * @param password
     * @throws IOException
     */
    public ConfigBackupImportResult importConfiguration(InputStream inputStream, String password) throws IOException {
        ConfigBackupImportResult result = new ConfigBackupImportResult();
        try (JarInputStream jarStream = new JarInputStream(inputStream)) {
            Manifest manifest = jarStream.getManifest();
            BackupAttributes attribs = getVerifiedAttributes(manifest);
            CryptoService cryptoService = createCryptoService(password);

            for (BackupProvider provider : createBackupProviders(attribs.getVersion(), cryptoService)) {
                long start = System.currentTimeMillis();
                provider.importConfiguration(jarStream, attribs.getSchemaVersion());
                long elapsed = System.currentTimeMillis() - start;

                result.addWarnings(provider.getWarnings());

                STATUS.info("Configuration backup imported by {} in {}ms", provider.getClass().getSimpleName(), elapsed);
            }
        }
        return result;
    }

    CryptoService createCryptoService(String password) throws IOException {
        if (password == null) {
            return null;
        }
        try {
            return CryptoServiceFactory.getInstance().setSaltedPassword(password.toCharArray(), salt).build();
        } catch (Exception e) {
            LOG.error("Could not create CryptoService");
            throw new IOException("Could not create CryptoService", e);
        }
    }

    private BackupAttributes getVerifiedAttributes(Manifest manifest) {
        if (manifest == null) {
            throw new CorruptedBackupException("Missing manifest file");
        }
        BackupAttributes attribs = new BackupAttributes(manifest.getMainAttributes());
        int version = attribs.getVersion();
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new UnsupportedBackupVersionException(version);
        }
        return attribs;
    }

    BackupAttributes getBackupAttributes(boolean passwordRequired) {
        return new BackupAttributes(CURRENT_VERSION, Integer.parseInt(dataSource.getVersion()), passwordRequired);
    }

}
