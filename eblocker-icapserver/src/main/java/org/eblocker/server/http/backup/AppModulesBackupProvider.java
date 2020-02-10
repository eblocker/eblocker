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

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.service.AppModuleService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class AppModulesBackupProvider extends BackupProvider {
    public static final String APP_MODULES_ENTRY = "eblocker-config/appModules.json";
    private final AppModuleService appModuleService;
    private static final Logger LOG = LoggerFactory.getLogger(AppModulesBackupProvider.class);

    @Inject
    public AppModulesBackupProvider(AppModuleService appModuleService) {
        this.appModuleService = appModuleService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        List<AppWhitelistModule> allModules = appModuleService.getAll();
        List<AppWhitelistModule> modifiedModules = allModules.stream()
            .filter(AppWhitelistModule::isModified)
            .filter(module -> module.getId() != appModuleService.getTempAppModuleId())
            .collect(Collectors.toList());
        Map<Integer, Boolean> enabledStates = allModules.stream()
            .collect(Collectors.toMap(AppWhitelistModule::getId, AppWhitelistModule::isEnabled));
        AppModulesBackup backup = new AppModulesBackup(modifiedModules, enabledStates);

        JarEntry entry = new JarEntry(APP_MODULES_ENTRY);
        outputStream.putNextEntry(entry);
        outputStream.write(objectMapper.writeValueAsBytes(backup));
        outputStream.closeEntry();
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        AppModulesBackup backup = null;
        JarEntry entry = inputStream.getNextJarEntry();
        if (entry.getName().equals(APP_MODULES_ENTRY)) {
            backup = objectMapper.readValue(inputStream, AppModulesBackup.class);
            inputStream.closeEntry();
        } else {
            throw new EblockerException("Expected entry " + APP_MODULES_ENTRY + ", got " + entry.getName());
        }
        if (backup != null) {
            // FUTURE: When the format or anything related to the App Modules
            // changes, apply the migrations to them here, using the parameter
            // schemaVersion
            appModuleService.restoreModified(backup.getModules(), backup.getEnabledStates());
        } else {
            throw new EblockerException("Deserialized backup object is null");
        }
    }
}
