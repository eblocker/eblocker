/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.wireguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;

@Singleton
public class WireGuardProfileFiles {
    private static final String IMPORTED_CONFIG_FILE_NAME_FORMAT = "/importedConfiguration%d.conf";
    private static final String PARSED_CONFIGURATION_FILE_NAME_FORMAT = "/parsedConfiguration%d.json";
    private static final String RUNTIME_CONFIG_FILE_NAME_FORMAT = "/wg%d.conf";
    private static final String LOG_FILE_FORMAT = "/wireguard%d.log";

    private final String profilesPath;
    private final ObjectMapper objectMapper;

    @Inject
    public WireGuardProfileFiles(@Named("wireguard.profiles.folder.path") String profilesPath, ObjectMapper objectMapper) {
        if (profilesPath.endsWith("/")) {
            profilesPath = profilesPath.substring(0, profilesPath.length() - 1);
        }
        this.profilesPath = profilesPath;
        this.objectMapper = objectMapper;
    }

    public String getProfilesPath() {
        return profilesPath;
    }

    public String getDirectory(int id) {
        return profilesPath + "/" + id;
    }

    public String getImportedConfig(int id) {
        return getDirectory(id) + String.format(IMPORTED_CONFIG_FILE_NAME_FORMAT, id);
    }

    public String getParsedConfiguration(int id) {
        return getDirectory(id) + String.format(PARSED_CONFIGURATION_FILE_NAME_FORMAT, id);
    }

    public String getRuntimeConfig(int id) {
        return getDirectory(id) + String.format(RUNTIME_CONFIG_FILE_NAME_FORMAT, id);
    }

    public String getLogFile(int id) {
        return getDirectory(id) + String.format(LOG_FILE_FORMAT, id);
    }

    public void createProfileDirectory(int id) throws IOException {
        Path directory = Paths.get(getDirectory(id));
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        }
    }

    public void removeProfileDirectory(int id) throws IOException {
        FileUtils.deleteDirectory(Paths.get(getDirectory(id)));
    }

    public void writeImportedConfig(int id, String config) throws IOException {
        Files.writeString(Paths.get(getImportedConfig(id)), config, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public String readImportedConfig(int id) throws IOException {
        return Files.readString(Paths.get(getImportedConfig(id)), StandardCharsets.UTF_8);
    }

    public boolean hasParsedConfiguration(int id) {
        return Files.exists(Paths.get(getParsedConfiguration(id)));
    }

    public WireGuardConfiguration readParsedConfiguration(int id) throws IOException {
        return objectMapper.readValue(new FileInputStream(getParsedConfiguration(id)), WireGuardConfiguration.class);
    }

    public void writeParsedConfiguration(int id, WireGuardConfiguration configuration) throws IOException {
        objectMapper.writeValue(new FileOutputStream(getParsedConfiguration(id)), configuration);
    }

    public void writeRuntimeConfig(int id, String config) throws IOException {
        Path path = Paths.get(getRuntimeConfig(id));
        if (!Files.exists(path)) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        }
        Files.writeString(path, config, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.setPosixFilePermissions(path, Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    public List<String> readLogFile(int id) throws IOException {
        Path path = Paths.get(getLogFile(id));
        if (Files.exists(path)) {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        }
        return Collections.emptyList();
    }

    public void truncateLogFile(int id) throws IOException {
        Files.write(Paths.get(getLogFile(id)), new byte[0],
                new OpenOption[]{
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE });
    }
}
