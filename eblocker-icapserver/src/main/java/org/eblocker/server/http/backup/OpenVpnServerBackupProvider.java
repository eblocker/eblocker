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

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.openvpn.server.OpenVpnCa;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Backs up and restores eBlocker Mobile settings for OpenVPN.
 */
public class OpenVpnServerBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpenVpnServerBackupProvider.class);
    public static final String OPENVPN_SERVER_ENTRY = "eblocker-config/openVpnServer.json";
    public static final String SHARED_SECRET_FILE_NAME = "ta.key";

    private final OpenVpnServerService service;
    private final OpenVpnCa ca;
    private final String openVpnServerPath;
    private final String caPath;

    @AssistedInject
    public OpenVpnServerBackupProvider(OpenVpnServerService service,
                                       OpenVpnCa ca,
                                       @Named("openvpn.server.path") String openVpnServerPath,
                                       @Named("openvpn.server.ca.path") String caPath,
                                       @Assisted @Nullable CryptoService cryptoService) {
        super(cryptoService);
        this.service = service;
        this.ca = ca;
        this.openVpnServerPath = openVpnServerPath;
        this.caPath = caPath;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        OpenVpnServerBackup backup = createBackup();
        writeNextEntry(outputStream, OPENVPN_SERVER_ENTRY, objectMapper.writeValueAsBytes(backup));
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
        getNextEntry(inputStream, OPENVPN_SERVER_ENTRY);
        OpenVpnServerBackup backup = objectMapper.readValue(inputStream, OpenVpnServerBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        if (!canDecrypt()) {
            addWarning(BackupWarning.NO_PASSWORD_OPENVPN_SERVER_NOT_IMPORTED);
            return;
        }

        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private OpenVpnServerBackup createBackup() throws IOException {
        OpenVpnServerBackup backup = new OpenVpnServerBackup();
        backup.setEnabled(service.isOpenVpnServerEnabled());
        VpnServerStatus serverStatus = service.getOpenVpnServerStatus();
        backup.setServerStatus(serverStatus);
        if (serverStatus.isFirstStart()) {
            // Server was never used or reset, so keys are not backed up
            return backup;
        }
        if (!canEncrypt()) {
            LOG.error("Cannot encrypt OpenVPN server keys");
            throw new EncryptionUnavailableException("Cannot encrypt OpenVPN server keys");
        }
        backup.setCaKeys(ca.exportCertificatesAndKeys());
        backup.setSharedSecret(readSharedSecret());
        return backup;
    }

    private void restoreBackup(OpenVpnServerBackup backup) throws IOException {
        service.resetOpenVpnServer();

        VpnServerStatus serverStatus = backup.getServerStatus();
        service.setOpenVpnServerStatus(serverStatus);
        ca.importCertificatesAndKeys(backup.getCaKeys());
        writeSharedSecret(backup.getSharedSecret());
        service.restoreOpenVpnServer();
        try {
            if (serverStatus.isRunning()) {
                service.enablePortForwarding();
            } else {
                service.disablePortForwarding();
            }
        } catch (Exception e) {
            LOG.warn("Could not update port forwarding during backup restore", e);
            addWarning(BackupWarning.UPNP_PORT_FORWARDING_FAILURE);
        }
    }

    private void writeSharedSecret(@Nullable String sharedSecret) throws IOException {
        if (sharedSecret == null) {
            return;
        }
        Path path = Paths.get(caPath, SHARED_SECRET_FILE_NAME);
        Files.writeString(path, sharedSecret, StandardCharsets.US_ASCII);
    }

    @Nullable
    private String readSharedSecret() throws IOException {
        Path path = Paths.get(openVpnServerPath, SHARED_SECRET_FILE_NAME);
        if (path.toFile().exists()) {
            return Files.readString(path, StandardCharsets.US_ASCII);
        } else {
            return null;
        }
    }
}
