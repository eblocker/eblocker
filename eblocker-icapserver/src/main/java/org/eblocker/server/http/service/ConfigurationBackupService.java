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
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.backup.AppModulesBackupProvider;
import org.eblocker.server.http.backup.BackupProvider;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DevicesBackupProvider;
import org.eblocker.server.http.backup.HttpsKeysBackupProvider;
import org.eblocker.server.http.backup.TorConfigBackupProvider;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * This service exports/imports configuration to/from a JAR file.
 * <p>
 * The configuration version is written to the JAR's manifest file.
 */
public class ConfigurationBackupService {
    private static final int VERSION_1_ONLY_APP_MODULES = 1;
    private static final int VERSION_2_APP_MODULES_AND_DEVICES = 2;
    private static final int VERSION_3_APP_MODULES_DEVICES_TOR = 3;
    private static final int VERSION_4_WITH_KEYS = 4;
    private static final int CURRENT_VERSION = 4;
    private static final byte[] salt = {-58, -73, 41, -28, 37, 23, -61, 93, 47, -57, -45, 23, -77, 97, 102, 49};
    static final String CURRENT_VERSION_KEY = "eBlocker-Backup-Version";
    static final String CURRENT_SCHEMA_VERSION = "Schema-Version";
    static final String FALLBACK_SCHEMA_VERSION = "0";
    static final String PASSWORD_REQUIRED = "Password-Required";
    static final String PASSWORD_REQUIRED_DEFAULT = "false";

    private Map<Integer, List<BackupProvider>> versionizedBackupProviders;
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupService.class);
    private DataSource dataSource;

    @Inject
    public ConfigurationBackupService(DataSource dataSource, AppModulesBackupProvider appModulesBackupProvider,
                                      DevicesBackupProvider devicesBackupProvider, TorConfigBackupProvider torConfigBackupProvider,
                                      HttpsKeysBackupProvider httpsKeysBackupProvider) {
        this(dataSource);

        versionizedBackupProviders.put(VERSION_1_ONLY_APP_MODULES,
                List.of(appModulesBackupProvider));

        versionizedBackupProviders.put(VERSION_2_APP_MODULES_AND_DEVICES,
                List.of(appModulesBackupProvider, devicesBackupProvider));

        versionizedBackupProviders.put(VERSION_3_APP_MODULES_DEVICES_TOR,
                List.of(appModulesBackupProvider, devicesBackupProvider, torConfigBackupProvider));

        versionizedBackupProviders.put(VERSION_4_WITH_KEYS,
                List.of(httpsKeysBackupProvider, appModulesBackupProvider, devicesBackupProvider, torConfigBackupProvider)); // fail early if the password is wrong!
    }

    public ConfigurationBackupService(DataSource dataSource, BackupProvider backupProvider) {
        this(dataSource);
        versionizedBackupProviders.put(CURRENT_VERSION, List.of(backupProvider));
    }

    public ConfigurationBackupService(DataSource dataSource) {
        versionizedBackupProviders = new HashMap<>();
        this.dataSource = dataSource;
    }

    public void exportConfiguration(OutputStream outputStream) throws IOException {
        exportConfiguration(outputStream, null);
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
            for (BackupProvider provider : versionizedBackupProviders.get(CURRENT_VERSION)) {
                provider.exportConfiguration(jarStream, cryptoService);
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
            if (manifest == null) {
                throw new CorruptedBackupException("Missing manifest file");
            }
            BackupAttributes attribs = new BackupAttributes(manifest.getMainAttributes());
            return attribs.passwordRequired;
        }
    }

    public void importConfiguration(InputStream inputStream) throws IOException {
        importConfiguration(inputStream, null);
    }

    /**
     * Imports the configuration from the given InputStream. If the user has provided a password,
     * private keys are imported.
     * @param inputStream
     * @param password
     * @throws IOException
     */
    public void importConfiguration(InputStream inputStream, String password) throws IOException {
        try (JarInputStream jarStream = new JarInputStream(inputStream)) {
            Manifest manifest = jarStream.getManifest();
            if (manifest == null) {
                throw new CorruptedBackupException("Missing manifest file");
            }
            BackupAttributes attribs = new BackupAttributes(manifest.getMainAttributes());

            CryptoService cryptoService = createCryptoService(password);

            for (BackupProvider provider : versionizedBackupProviders.get(attribs.version)) {
                provider.importConfiguration(jarStream, cryptoService, attribs.schemaVersion);
            }
        }
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

    BackupAttributes getBackupAttributes(boolean passwordRequired) {
        return new BackupAttributes(CURRENT_VERSION, Integer.parseInt(dataSource.getVersion()), passwordRequired);

    }

    public class BackupAttributes {
        private int version;
        private int schemaVersion;
        private boolean passwordRequired = false;

        public BackupAttributes(int version, int schemaVersion, boolean passwordRequired) {
            this.version = version;
            this.schemaVersion = schemaVersion;
            this.passwordRequired = passwordRequired;
        }

        private BackupAttributes(Attributes attributes) {
            version = Integer.parseInt(attributes.getValue(CURRENT_VERSION_KEY));
            if (!versionizedBackupProviders.containsKey(version)) {
                throw new UnsupportedBackupVersionException(version);
            }
            schemaVersion = Integer.parseInt(getOrDefault(attributes, CURRENT_SCHEMA_VERSION, FALLBACK_SCHEMA_VERSION));
            passwordRequired = Boolean.parseBoolean(getOrDefault(attributes, PASSWORD_REQUIRED, PASSWORD_REQUIRED_DEFAULT));
        }

        private String getOrDefault(Attributes attributes, String key, String defaultValue) {
            String value = attributes.getValue(key);
            return value != null ? value : defaultValue;
        }

        private void addToAttributes(Attributes attributes) {
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue(CURRENT_VERSION_KEY, String.valueOf(version));
            attributes.putValue(CURRENT_SCHEMA_VERSION, String.valueOf(schemaVersion));
            attributes.putValue(PASSWORD_REQUIRED, String.valueOf(passwordRequired));
        }
    }
}
