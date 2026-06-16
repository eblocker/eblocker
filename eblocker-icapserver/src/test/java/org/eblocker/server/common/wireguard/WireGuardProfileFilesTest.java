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
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardPeer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WireGuardProfileFilesTest {
    private String profilesPath;
    private WireGuardProfileFiles profileFiles;

    @Before
    public void setup() throws IOException {
        Path path = Files.createTempDirectory("unit-test-wireguard");
        profilesPath = path.toString();
        profileFiles = new WireGuardProfileFiles(profilesPath + "/", new ObjectMapper());
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(profilesPath));
    }

    @Test
    public void normalizesAndCreatesUniqueProfileFileLocations() {
        Assert.assertEquals(profilesPath, profileFiles.getProfilesPath());

        Set<String> locations = new HashSet<>();
        for (int i = 0; i < 4; ++i) {
            String profilePath = profilesPath + "/" + i;
            Assert.assertEquals(profilePath, profileFiles.getDirectory(i));

            checkLocation(locations, profilePath, profileFiles.getImportedConfig(i));
            checkLocation(locations, profilePath, profileFiles.getParsedConfiguration(i));
            checkLocation(locations, profilePath, profileFiles.getRuntimeConfig(i));
            checkLocation(locations, profilePath, profileFiles.getLogFile(i));
        }
    }

    @Test
    public void createsAndDeletesProfileDirectory() throws IOException {
        String directory = profileFiles.getDirectory(7);
        Assert.assertFalse(Files.exists(Paths.get(directory)));

        profileFiles.createProfileDirectory(7);
        Assert.assertTrue(Files.isDirectory(Paths.get(directory)));

        profileFiles.removeProfileDirectory(7);
        Assert.assertFalse(Files.exists(Paths.get(directory)));
    }

    @Test
    public void writesAndReadsImportedConfig() throws IOException {
        profileFiles.createProfileDirectory(1);

        String config = "[Interface]\nPrivateKey = private=\n";
        profileFiles.writeImportedConfig(1, config);

        Assert.assertEquals(config, profileFiles.readImportedConfig(1));
    }

    @Test
    public void writesRuntimeConfigWithOwnerOnlyPermissions() throws IOException {
        profileFiles.createProfileDirectory(2);

        String config = "[Interface]\nPrivateKey = secret=\n";
        profileFiles.writeRuntimeConfig(2, config);

        Path runtimeConfig = Paths.get(profileFiles.getRuntimeConfig(2));
        Assert.assertEquals(config, Files.readString(runtimeConfig));
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(runtimeConfig);
        Assert.assertEquals(new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)), permissions);
    }

    @Test
    public void writesAndReadsParsedConfiguration() throws IOException {
        profileFiles.createProfileDirectory(3);
        WireGuardConfiguration configuration = new WireGuardConfiguration(
                "private=",
                Arrays.asList("10.0.0.2/32"),
                Arrays.asList("10.0.0.1"),
                1420,
                Arrays.asList(new WireGuardPeer("public=", "psk=", "vpn.example.net:51820", Arrays.asList("0.0.0.0/0"), 25)));

        Assert.assertFalse(profileFiles.hasParsedConfiguration(3));
        profileFiles.writeParsedConfiguration(3, configuration);
        Assert.assertTrue(profileFiles.hasParsedConfiguration(3));

        WireGuardConfiguration read = profileFiles.readParsedConfiguration(3);
        Assert.assertEquals("private=", read.getPrivateKey());
        Assert.assertEquals(Arrays.asList("10.0.0.2/32"), read.getAddresses());
        Assert.assertEquals(Arrays.asList("10.0.0.1"), read.getDnsServers());
        Assert.assertEquals(Integer.valueOf(1420), read.getMtu());
        Assert.assertEquals(1, read.getPeers().size());
        Assert.assertEquals("public=", read.getPeers().get(0).getPublicKey());
    }

    @Test
    public void readsMissingLogAsEmptyList() throws IOException {
        profileFiles.createProfileDirectory(4);

        Assert.assertEquals(List.of(), profileFiles.readLogFile(4));
    }

    @Test
    public void readsExistingLogFile() throws IOException {
        profileFiles.createProfileDirectory(5);
        List<String> lines = Arrays.asList("line 1", "line 2");
        Files.write(Paths.get(profileFiles.getLogFile(5)), lines);

        Assert.assertEquals(lines, profileFiles.readLogFile(5));
    }

    private void checkLocation(Set<String> locations, String profilePath, String location) {
        Assert.assertTrue(location.startsWith(profilePath));
        Assert.assertTrue(locations.add(location));
    }
}
