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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.http.backup.AppModulesBackupProvider;
import org.eblocker.server.http.backup.BackupProvider;
import org.eblocker.server.http.backup.CorruptedBackupException;
import org.eblocker.server.http.backup.DevicesBackupProvider;
import org.eblocker.server.http.backup.TorConfigBackupProvider;
import org.eblocker.server.http.backup.UnsupportedBackupVersionException;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * This service exports/imports configuration to/from a JAR file.
 *
 * The configuration version is written to the JAR's manifest file.
 */
public class ConfigurationBackupService {
    private static final int OLD_VERSION_1_ONLY_APP_MODULES = 1;
    private static final int OLD_VERSION_2_APP_MODULES_AND_DEVICES = 2;
    private static final int OLD_VERSION_3_APP_MODULES_DEVICES_TOR = 3;
    private static final int CURRENT_VERSION = 3;
    static final String CURRENT_VERSION_KEY = "eBlocker-Backup-Version";
    static final String CURRENT_SCHEMA_VERSION = "Schema-Version";
    static final String FALLBACK_SCHEMA_VERSION = "0";

    private Map<Integer, List<BackupProvider>> versionizedBackupProviders;
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBackupService.class);
    private DataSource dataSource;

    @Inject
    public ConfigurationBackupService(DataSource dataSource, AppModulesBackupProvider appModulesBackupProvider,
            DevicesBackupProvider devicesBackupProvider, TorConfigBackupProvider torConfigBackupProvider) {
        this(dataSource);

        versionizedBackupProviders.put(OLD_VERSION_1_ONLY_APP_MODULES,
                new ArrayList<BackupProvider>(Arrays.asList(appModulesBackupProvider)));

        versionizedBackupProviders.put(OLD_VERSION_2_APP_MODULES_AND_DEVICES,
                new ArrayList<BackupProvider>(Arrays.asList(appModulesBackupProvider, devicesBackupProvider)));

        versionizedBackupProviders.put(OLD_VERSION_3_APP_MODULES_DEVICES_TOR,
                new ArrayList<BackupProvider>(
                        Arrays.asList(appModulesBackupProvider, devicesBackupProvider, torConfigBackupProvider)));
    }

    public ConfigurationBackupService(DataSource dataSource, BackupProvider backupProvider) {
        this(dataSource);
        versionizedBackupProviders.put(CURRENT_VERSION, new ArrayList<BackupProvider>(Arrays.asList(backupProvider)));
    }

    public ConfigurationBackupService(DataSource dataSource) {
        versionizedBackupProviders = new HashMap<>();
        this.dataSource = dataSource;
    }

    public void exportConfiguration(OutputStream outputStream) throws IOException {
        Manifest manifest = new Manifest();
        addMainAttributes(manifest.getMainAttributes());

        try (JarOutputStream jarStream = new JarOutputStream(outputStream, manifest)) {
            for (BackupProvider provider : versionizedBackupProviders.get(CURRENT_VERSION)) {
                provider.exportConfiguration(jarStream);
            }
        }
    }

    public void importConfiguration(InputStream inputStream) throws IOException {
        try (JarInputStream jarStream = new JarInputStream(inputStream)) {
            Manifest manifest = jarStream.getManifest();
            if (manifest == null) {
                throw new CorruptedBackupException("Missing manifest file");
            }
            int version = Integer.parseInt(manifest.getMainAttributes().getValue(CURRENT_VERSION_KEY));
            if (!versionizedBackupProviders.containsKey(version)) {
                throw new UnsupportedBackupVersionException(version);
            }
            int schemaVersion = Integer.parseInt((String)
                    manifest.getMainAttributes().getOrDefault(CURRENT_SCHEMA_VERSION, FALLBACK_SCHEMA_VERSION));
            for (BackupProvider provider : versionizedBackupProviders.get(version)) {
                provider.importConfiguration(jarStream, schemaVersion);
            }
        }
    }

    void addMainAttributes(Attributes attributes) {
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue(CURRENT_VERSION_KEY, Integer.toString(CURRENT_VERSION));
        attributes.putValue(CURRENT_SCHEMA_VERSION, dataSource.getVersion());
    }
}
