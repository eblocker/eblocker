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
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Backs up network settings.
 */
public class NetworkBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(BlockersBackupProvider.class);
    public static final String NETWORK_ENTRY = "eblocker-config/network.json";

    private final NetworkServices networkServices;

    @Inject
    public NetworkBackupProvider(NetworkServices networkServices) {
        this.networkServices = networkServices;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        NetworkConfiguration backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        writeNextEntry(outputStream, NETWORK_ENTRY, backupBytes);
    }

    private NetworkConfiguration createBackup() {
        return networkServices.getCurrentNetworkConfiguration();
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
        getNextEntry(inputStream, NETWORK_ENTRY);
        NetworkConfiguration backup = objectMapper.readValue(inputStream, NetworkConfiguration.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }
        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private void restoreBackup(NetworkConfiguration backup) {
        LOG.info("Network settings are not imported in eOS 3");
    }
}
