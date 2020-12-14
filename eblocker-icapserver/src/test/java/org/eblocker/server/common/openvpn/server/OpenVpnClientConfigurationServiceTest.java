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

import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertArrayEquals;

public class OpenVpnClientConfigurationServiceTest {
    private static final String OPENVPN_TEST_PATH = "test-data";
    private static final String deviceName = "device:001122334455";

    private OpenVpnClientConfigurationService controller;
    private OpenVpnServerService openVpnServerService;
    private OpenVpnCa openVpnCa;
    private String testPath;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() throws URISyntaxException {
        openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("vpn.hh.eblocker.com");
        Mockito.when(openVpnServerService.getOpenVpnMappedPort()).thenReturn(1194);
        testPath = new File(ClassLoader.getSystemResource(OPENVPN_TEST_PATH).toURI()).toString();

        openVpnCa = Mockito.mock(OpenVpnCa.class);
        Mockito.when(openVpnCa.getCaCertificatePath()).thenReturn(Paths.get(testPath, "ca.crt"));
        prepareClientPaths(openVpnCa, deviceName);
        prepareClientPaths(openVpnCa, "asa");

        String windowsClientConfigTemplate = Paths.get(testPath, "openvpn-server-client.windows-template.conf").toString();
        String unixClientConfigTemplate = Paths.get(testPath, "openvpn-server-client.unix-template.conf").toString();
        String macosClientConfigTemplate = Paths.get(testPath, "openvpn-server-client.macos-template.conf").toString();

        controller = new OpenVpnClientConfigurationService(
            openVpnServerService,
            openVpnCa,
            testPath,
            windowsClientConfigTemplate,
            unixClientConfigTemplate,
            macosClientConfigTemplate);
    }

    private void prepareClientPaths(OpenVpnCa openVpnCa, String deviceName) {
        Mockito.when(openVpnCa.getClientCertificatePath(deviceName)).thenReturn(Paths.get(testPath, deviceName + ".crt"));
        Mockito.when(openVpnCa.getClientKeyPath(deviceName)).thenReturn(Paths.get(testPath, deviceName + ".key"));
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
