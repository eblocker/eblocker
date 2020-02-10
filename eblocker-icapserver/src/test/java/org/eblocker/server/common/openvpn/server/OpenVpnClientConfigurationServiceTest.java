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
package org.eblocker.server.common.openvpn.server;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class OpenVpnClientConfigurationServiceTest {
    private static final String OPENVPN_TEST_PATH = "test-data";
    private static final String deviceName = "device:001122334455";

    private OpenVpnClientConfigurationService controller;
    private OpenVpnServerService openVpnServerService;
    private String testPath;

    @Rule public ExpectedException thrown= ExpectedException.none();

    @Before
    public void setup() throws URISyntaxException {
        openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("vpn.hh.eblocker.com");
        Mockito.when(openVpnServerService.getOpenVpnMappedPort()).thenReturn(1194);
        testPath = new File(ClassLoader.getSystemResource(OPENVPN_TEST_PATH).toURI()).toString();

        String windowsClientConfigTemplate = FileSystems.getDefault().getPath(testPath, "openvpn-server-client.windows-template.conf").toString();
        String unixClientConfigTemplate = FileSystems.getDefault().getPath(testPath, "openvpn-server-client.unix-template.conf").toString();
        String macosClientConfigTemplate = FileSystems.getDefault().getPath(testPath, "openvpn-server-client.macos-template.conf").toString();

        controller = new OpenVpnClientConfigurationService (
                openVpnServerService,
                testPath,
                windowsClientConfigTemplate,
                unixClientConfigTemplate,
                macosClientConfigTemplate);
    }

    @Test
    public void downloadClientConfForWindows() throws IOException {
        byte[] bytes = controller.getOvpnProfile(deviceName, OperatingSystemType.WINDOWS);
        Path clientConf = FileSystems.getDefault().getPath(testPath, String.format("/%s-windows.ovpn", deviceName));

        assertArrayEquals(Files.readAllBytes(clientConf), bytes);
    }

    @Test
    public void downloadClientConfForUnix() throws IOException {
        byte[] bytes = controller.getOvpnProfile(deviceName, OperatingSystemType.OTHER);
        Path clientConf = FileSystems.getDefault().getPath(testPath, String.format("/%s-unix.ovpn", deviceName));

        assertArrayEquals(Files.readAllBytes(clientConf), bytes);
    }

    @Test
    public void downloadClientConfForMacos() throws IOException {
        byte[] bytes = controller.getOvpnProfile(deviceName, OperatingSystemType.MAC);
        Path clientConf = FileSystems.getDefault().getPath(testPath, String.format("/%s-macos.ovpn", deviceName));

        assertArrayEquals(Files.readAllBytes(clientConf), bytes);
    }

    @Test
    public void downloadClientConfError() throws IOException {
        thrown.expect(NoSuchFileException.class);
        controller.getOvpnProfile("asa", OperatingSystemType.OTHER);
    }
}
