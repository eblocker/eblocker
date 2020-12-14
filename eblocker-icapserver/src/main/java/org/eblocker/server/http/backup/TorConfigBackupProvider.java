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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.TorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class TorConfigBackupProvider extends BackupProvider {
    public static final String TOR_ENTRY = "eblocker-config/tor.json";
    private final TorController torController;
    private static final Logger LOG = LoggerFactory.getLogger(TorConfigBackupProvider.class);

    @Inject
    public TorConfigBackupProvider(TorController torController) {
        this.torController = torController;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        Set<String> torCountries = torController.getCurrentExitNodeCountries();

        JarEntry entry = new JarEntry(TOR_ENTRY);
        outputStream.putNextEntry(entry);
        outputStream.write(objectMapper.writeValueAsBytes(torCountries));
        outputStream.closeEntry();
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        Set<String> restoredTorCountries = null;
        JarEntry entry = inputStream.getNextJarEntry();
        if (entry.getName().equals(TOR_ENTRY)) {
            restoredTorCountries = objectMapper.readValue(inputStream, new TypeReference<Set<String>>() {
            });
            inputStream.closeEntry();
        } else {
            throw new EblockerException("Expected entry " + TOR_ENTRY + ", got " + entry.getName());
        }
        if (restoredTorCountries != null && !restoredTorCountries.isEmpty()) {
            restoreTorCountries(restoredTorCountries);
        } else {
            // When the exit node is chosen automatically, the list is empty
            restoreTorCountries(Collections.emptySet());
        }
    }

    private void restoreTorCountries(Set<String> countries) {
        torController.setAllowedExitNodesCountries(countries);
    }
}
