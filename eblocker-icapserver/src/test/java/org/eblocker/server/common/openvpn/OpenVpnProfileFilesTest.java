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

import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnLoginCredentials;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.Option;
import org.eblocker.server.common.openvpn.configuration.SimpleOption;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OpenVpnProfileFilesTest {

    private String profilesPath;
    private OpenVpnProfileFiles profileFiles;

    @Before
    public void setup() throws IOException {
        Path path = Files.createTempDirectory("unit-test-openvpn");
        profilesPath = path.toString();
        profileFiles = new OpenVpnProfileFiles(profilesPath, new ObjectMapper());
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(profilesPath));
    }

    @Test
    public void testFileLocations() {
        Set<String> locations = new HashSet<>();
        for(int i = 0; i < 4; ++i) {
            String profilePath = profilesPath + "/" + i;
            Assert.assertEquals(profilePath, profileFiles.getDirectory(i));

           checkLocation(locations, profilePath, profileFiles.getConfig(i));
            checkLocation(locations, profilePath, profileFiles.getCredentials(i));
            checkLocation(locations, profilePath, profileFiles.getParsedConfiguration(i));
            checkLocation(locations, profilePath, profileFiles.getLogFile(i));
        }
    }

    private void checkLocation(Set<String> locations, String profilePath, String location) {
        Assert.assertTrue(location.startsWith(profilePath));
        Assert.assertTrue(locations.add(location));
    }

    @Test
    public void testCreateDeleteProfileDirectory() throws IOException {
        String path = profileFiles.getDirectory(0);
        Assert.assertFalse(Files.exists(Paths.get(path)));

        profileFiles.createProfileDirectory(0);
        Assert.assertTrue(Files.exists(Paths.get(path)));

        profileFiles.removeProfileDirectory(0);
        Assert.assertFalse(Files.exists(Paths.get(path)));
    }

    @Test
    public void testWriteConfigFile() throws IOException {
        profileFiles.createProfileDirectory(0);

        Assert.assertFalse(Files.exists(Paths.get(profileFiles.getConfig(0))));

        List<Option> options = new ArrayList<>();
        options.add(new SimpleOption(1, "option-no-args"));
        options.add(new SimpleOption(2, "option-with-args", new String[]{"0", "1", "2", "3"}));
        profileFiles.writeConfigFile(0, options);

        Assert.assertTrue(Files.exists(Paths.get(profileFiles.getConfig(0))));

        List<String> content = ResourceHandler.readLines(new SimpleResource(profileFiles.getConfig(0)));
        Assert.assertEquals("option-no-args", content.get(0));
        Assert.assertEquals("option-with-args 0 1 2 3", content.get(1));
    }

    @Test
    public void testWriteOptionFile() throws IOException {
        profileFiles.createProfileDirectory(0);

        Assert.assertFalse(Files.exists(Paths.get(profileFiles.getOptionFile(0, "a"))));

        byte[] content = new byte[] { 0, 1, 2, 3 };
        profileFiles.writeConfigOptionFile(0, "a", content);

        Assert.assertTrue(Files.exists(Paths.get(profileFiles.getOptionFile(0, "a"))));
        Assert.assertArrayEquals(content, Files.readAllBytes(Paths.get(profileFiles.getOptionFile(0, "a"))));
    }

    @Test
    public void testWriteCredentials() throws IOException {
        profileFiles.createProfileDirectory(0);

        Assert.assertFalse(Files.exists(Paths.get(profileFiles.getCredentials(0))));

        VpnLoginCredentials credentials = new VpnLoginCredentials();
        credentials.setUsername("username");
        credentials.setPassword("password");
        VpnProfile profile = new OpenVpnProfile(0, "unit-test");
        profile.setLoginCredentials(credentials);
        profileFiles.writeTransientCredentialsFile(profile);

        Assert.assertTrue(Files.exists(Paths.get(profileFiles.getCredentials(0))));

        List<String> content = ResourceHandler.readLines(new SimpleResource(profileFiles.getCredentials(0)));
        Assert.assertEquals("username", content.get(0));
        Assert.assertEquals("password", content.get(1));
    }

    @Test
    public void testOverwriteCredentials() throws IOException {
        profileFiles.createProfileDirectory(0);

        Files.createFile(Paths.get(profileFiles.getCredentials(0)));
        Assert.assertTrue(Files.exists(Paths.get(profileFiles.getCredentials(0))));

        VpnLoginCredentials credentials = new VpnLoginCredentials();
        credentials.setUsername("username2");
        credentials.setPassword("password2");
        VpnProfile profile = new OpenVpnProfile(0, "unit-test");
        profile.setLoginCredentials(credentials);
        profileFiles.writeTransientCredentialsFile(profile);

        Assert.assertTrue(Files.exists(Paths.get(profileFiles.getCredentials(0))));

        List<String> content = ResourceHandler.readLines(new SimpleResource(profileFiles.getCredentials(0)));
        Assert.assertEquals("username2", content.get(0));
        Assert.assertEquals("password2", content.get(1));
    }

    @Test
    public void testParsedConfiguration() throws IOException {
        profileFiles.createProfileDirectory(0);

        Assert.assertFalse(Files.exists(Paths.get(profileFiles.getParsedConfiguration(0))));
        Assert.assertFalse(profileFiles.hasParsedConfiguration(0));

        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        profileFiles.writeParsedConfiguration(0, configuration);
        Assert.assertTrue(profileFiles.hasParsedConfiguration(0));

        OpenVpnConfiguration readConfiguration = profileFiles.readParsedConfiguration(0);
        Assert.assertNotNull(readConfiguration);
    }

    @Test
    public void testReadLogFile() throws IOException {
        profileFiles.createProfileDirectory(0);

        // log file does not exist
        Path logPath = Paths.get(profileFiles.getLogFile(0));
        Assert.assertFalse(Files.exists(logPath));

        // create test log
        List<String> lines = Arrays.asList("log line 0", "log line 1", "log line 2");
        Files.write(logPath, lines);

        List<String> readLines = profileFiles.readLogFile(0);
        Assert.assertEquals(lines, readLines);
    }
}
