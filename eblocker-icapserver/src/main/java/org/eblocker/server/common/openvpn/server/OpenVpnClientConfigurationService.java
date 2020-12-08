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

import com.auth0.jwt.internal.org.apache.commons.io.output.ByteArrayOutputStream;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.http.controller.impl.OpenVpnServerControllerImpl;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class OpenVpnClientConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(OpenVpnServerControllerImpl.class);

    private final OpenVpnServerService openVpnServerService;
    private final OpenVpnCa openVpnCa;
    private final String windowsClientTemplatePath;
    private final String unixClientTemplatePath;
    private final String openVpnServerPath;
    private final String macosClientTemplatePath;
    private String newLine = "\r\n";

    @Inject
    public OpenVpnClientConfigurationService(
        OpenVpnServerService openVpnServerService,
        OpenVpnCa openVpnCa,
        @Named("openvpn.server.path") String openVpnServerPath,
        @Named("openvpn.server.client.windows-template") String windowsClientTemplatePath,
        @Named("openvpn.server.client.unix-template") String unixClientTemplatePath,
        @Named("openvpn.server.client.macos-template") String macosClientTemplatePath) {

        this.openVpnServerPath = openVpnServerPath;
        this.windowsClientTemplatePath = windowsClientTemplatePath;
        this.unixClientTemplatePath = unixClientTemplatePath;
        this.openVpnServerService = openVpnServerService;
        this.openVpnCa = openVpnCa;
        this.macosClientTemplatePath = macosClientTemplatePath;
    }

    public byte[] getOvpnProfile(String deviceName, OperatingSystemType type) throws IOException {
        Path path;

        if (type == OperatingSystemType.WINDOWS) {
            path = FileSystems.getDefault().getPath(windowsClientTemplatePath);
            newLine = "\r\n";
        } else if (type == OperatingSystemType.MAC) {
            path = FileSystems.getDefault().getPath(macosClientTemplatePath);
            newLine = "\n";
        } else {
            path = FileSystems.getDefault().getPath(unixClientTemplatePath);
            newLine = "\n";
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // ** use user defined port with fallback to 1194 to avoid errors
        Integer mappedPortInRouter = openVpnServerService.getOpenVpnMappedPort();
        String remoteString = String.format("remote %s %d%s%s", openVpnServerService.getOpenVpnServerHost(), mappedPortInRouter, newLine, newLine);

        try {
            outputStream.write(remoteString.getBytes());
            outputStream.write(readFileWithNewLine(path));
            outputStream.write(createTag(extractLinesFromFile(openVpnCa.getCaCertificatePath()), "ca"));
            outputStream.write(createTag(extractLinesFromFile(openVpnCa.getClientCertificatePath(deviceName)), "cert"));
            outputStream.write(createTag(extractLinesFromFile(openVpnCa.getClientKeyPath(deviceName)), "key"));
            outputStream.write(createTag(extractLinesFromFile(Paths.get(openVpnServerPath, "ta.key")), "tls-auth"));
        } catch (Exception e) {
            log.error("Error creating ovpn-profile.", e);
            throw (e);
        } finally {
            outputStream.close();
        }

        outputStream.close();
        return outputStream.toByteArray();
    }

    private byte[] createTag(List<String> lines, String tag) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeBytes(String.format("<%s>%s", tag, newLine));
            for (String element : lines) {
                out.writeBytes(element);
            }
            out.writeBytes(String.format("</%s>%s", tag, newLine));
        } catch (IOException e) {
            log.debug("Error parsing file", e);
        }

        return baos.toByteArray();
    }

    private byte[] readFileWithNewLine(Path path) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (String line : Files.readAllLines(path)) {
                baos.write(line.trim().getBytes());
                baos.write(newLine.getBytes());
            }
        } catch (IOException e) {
            log.debug("Error parsing file", e);
        } finally {
            baos.close();
        }

        return baos.toByteArray();
    }

    private List<String> extractLinesFromFile(Path path) throws IOException {
        String beginDelimiter = "-----BEGIN";
        String endDelimiter = "-----END";
        List<String> result = new ArrayList<>();
        boolean validLine = false;

        List<String> lines = Files.readAllLines(path);

        for (String line : lines) {
            String currentLine = line.trim() + newLine;

            if (validLine) {
                result.add(currentLine);
            }

            if (line.startsWith(beginDelimiter)) {
                validLine = true;
                result.add(currentLine);
            }

            if (line.startsWith(endDelimiter)) {
                validLine = false;
            }
        }
        return result;
    }
}
