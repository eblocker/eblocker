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
package org.eblocker.server.common.openvpn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.openvpn.VpnLoginCredentials;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationVersion0;
import org.eblocker.server.common.openvpn.configuration.Option;
import org.eblocker.server.common.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;

public class OpenVpnProfileFiles {
    private static final String CONFIGURATION_FILE_NAME_FORMAT = "/parsedConfiguration%d.json";
    private static final String CONFIG_FILE_NAME_FORMAT = "/conf%d.ovpn";
    private static final String CONFIG_OPTION_FILE_NAME_FORMAT = "/option.%s";
    private static final String CREDENTIALS_FILE_NAME_FORMAT = "/credentials.txt";
    private static final String LOG_FILE_FORMAT = "/openvpn%d.log";

    private String profilesPath;
    private ObjectMapper objectMapper;

    @Inject
    public OpenVpnProfileFiles(@Named("openvpn.profiles.folder.path") String profilesPath, ObjectMapper objectMapper) {
        if (profilesPath.endsWith("/")) {
            profilesPath = profilesPath.substring(0, profilesPath.length() - 1);
        }
        this.profilesPath = profilesPath;
        this.objectMapper = objectMapper;
    }

    public String getDirectory(int id) {
        return profilesPath + "/" + id;
    }

    public String getParsedConfiguration(int id) {
        return getDirectory(id) + String.format(CONFIGURATION_FILE_NAME_FORMAT, id);
    }

    public String getConfig(int id) {
        return getDirectory(id) + String.format(CONFIG_FILE_NAME_FORMAT, id);
    }

    public String getOptionFile(int id, String option) {
        return getDirectory(id) + String.format(CONFIG_OPTION_FILE_NAME_FORMAT, option);
    }

    public String getCredentials(int id) {
        return getDirectory(id) + String.format(CREDENTIALS_FILE_NAME_FORMAT, id);
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

    public boolean hasParsedConfiguration(int id) {
        return Files.exists(Paths.get(getParsedConfiguration(id)));
    }

    public OpenVpnConfiguration readParsedConfiguration(int id) throws IOException {
        return objectMapper.readValue(new FileInputStream(getParsedConfiguration(id)), OpenVpnConfiguration.class);
    }

    public OpenVpnConfigurationVersion0 readParsedConfigurationVersion0(int id) throws IOException {
        return objectMapper.readValue(new FileInputStream(getParsedConfiguration(id)), OpenVpnConfigurationVersion0.class);
    }

    public void writeParsedConfiguration(int id, OpenVpnConfiguration configuration) throws IOException {
        objectMapper.writeValue(new FileOutputStream(getParsedConfiguration(id)), configuration);
    }

    public void writeConfigFile(int id, List<Option> options) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(getConfig(id));
        options.stream().forEach(writer::println);
        writer.flush();
        writer.close();
    }

    public void writeConfigOptionFile(int id, String option, byte[] content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(getOptionFile(id, option))) {
            fos.write(content);
        }
    }

    public File writeTransientCredentialsFile(VpnProfile vpnProfile) {
        VpnLoginCredentials credentials = vpnProfile.getLoginCredentials();
        if (credentials != null) {
            try {
                Path path = Paths.get(getCredentials(vpnProfile.getId()));
                if (!Files.exists(path)) {
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
                }
                File file = path.toFile();
                file.deleteOnExit();
                PrintWriter writer = new PrintWriter(file);
                writer.println(credentials.getUsername());
                writer.println(credentials.getPassword());
                writer.flush();
                writer.close();
                return file;
            } catch (IOException e) {
                throw new IllegalStateException("failed to write credentials file", e);
            }
        }
        return null;
    }

    public List<String> readLogFile(int id) throws IOException {
        Path path = Paths.get(getLogFile(id));
        if (Files.exists(path)) {
            return Files.readAllLines(path);
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
