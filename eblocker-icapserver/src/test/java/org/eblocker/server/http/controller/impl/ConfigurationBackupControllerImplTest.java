/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.controller.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.backup.ConfigBackupImportResult;
import org.eblocker.server.common.data.backup.ConfigBackupReference;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.controller.ConfigurationBackupController;
import org.eblocker.server.http.service.ConfigurationBackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationBackupControllerImplTest {
    private ConfigurationBackupController controller;
    private ConfigurationBackupService service;
    private ConfigBackupImportResult serviceImportResult;
    private Request request;
    private Response response;
    private Path tmpDir;

    @BeforeEach
    public void setUp() throws Exception {
        serviceImportResult = new ConfigBackupImportResult();
        tmpDir = Files.createTempDirectory(null);
        service = Mockito.mock(ConfigurationBackupService.class);
        controller = new ConfigurationBackupControllerImpl(service, tmpDir.toString());
        request = Mockito.mock(Request.class);
        response = Mockito.mock(Response.class);
        Mockito.when(service.importConfiguration(Mockito.any(), Mockito.any())).thenReturn(serviceImportResult);
    }

    @AfterEach
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    public void exportReferenceMissing() {
        assertThrows(BadRequestException.class, () ->
                controller.exportConfiguration(request, response));
    }

    @Test
    public void exportConfiguration() throws IOException {
        Mockito.when(request.getBodyAs(ConfigBackupReference.class)).thenReturn(new ConfigBackupReference());
        ConfigBackupReference result = controller.exportConfiguration(request, response);
        assertNotNull(result.getFileReference());
        Mockito.verify(service).exportConfiguration(Mockito.any(OutputStream.class), Mockito.isNull());
    }

    @Test
    public void exportConfigurationWithPassword() throws IOException {
        String password = "top secret!";
        Mockito.when(request.getBodyAs(ConfigBackupReference.class)).thenReturn(new ConfigBackupReference(null, password, false));
        ConfigBackupReference result = controller.exportConfiguration(request, response);
        assertNotNull(result.getFileReference());
        Mockito.verify(service).exportConfiguration(Mockito.any(OutputStream.class), Mockito.eq(password));
    }

    @Test
    public void exportConfigurationFailure() throws IOException {
        Mockito.when(request.getBodyAs(ConfigBackupReference.class)).thenReturn(new ConfigBackupReference());
        Mockito.doThrow(new IOException("Could not export")).when(service).exportConfiguration(Mockito.any(OutputStream.class), Mockito.isNull());
        assertThrows(EblockerException.class, () ->
            controller.exportConfiguration(request, response));
    }

    @Test
    public void downloadReferenceMissing() {
        assertThrows(BadRequestException.class, () ->
                controller.downloadConfiguration(request, response));
    }

    @Test
    public void downloadInvalidFile() throws IOException {
        // only temporary files with the correct prefix and suffix should be downloadable
        Path tmpFile = Files.createTempFile(tmpDir, "other-", ".file");
        Mockito.when(request.getHeader("configBackupFileReference")).thenReturn(tmpFile.getFileName().toString());
        assertThrows(BadRequestException.class, () ->
            controller.downloadConfiguration(request, response));
    }

    @Test
    public void downloadConfiguration() throws IOException {
        Path tmpFile = Files.createTempFile(tmpDir, ConfigurationBackupControllerImpl.FILE_PREFIX, ConfigurationBackupControllerImpl.FILE_SUFFIX);
        byte[] backupData = "Configuration backup data".getBytes();
        Files.write(tmpFile, backupData);
        Mockito.when(request.getHeader("configBackupFileReference")).thenReturn(tmpFile.getFileName().toString());
        ByteBuf result = controller.downloadConfiguration(request, response);
        ByteBuf expected = Unpooled.wrappedBuffer(backupData);
        assertEquals(expected, result);
    }

    @Test
    public void uploadConfiguration() throws IOException {
        byte[] backupData = "Configuration backup data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(backupData);
        Mockito.when(request.getBodyAsStream()).thenReturn(inputStream);
        Mockito.when(service.requiresPassword(Mockito.any(InputStream.class))).thenReturn(true);
        ConfigBackupReference result = controller.uploadConfiguration(request, response);
        assertTrue(result.isPasswordRequired());
        byte[] resultData = Files.readAllBytes(tmpDir.resolve(result.getFileReference()));
        assertArrayEquals(backupData, resultData);
        assertTrue(result.getFileReference().startsWith(ConfigurationBackupControllerImpl.FILE_PREFIX));
        assertTrue(result.getFileReference().endsWith(ConfigurationBackupControllerImpl.FILE_SUFFIX));
    }

    @Test
    public void importReferenceMissing() {
        assertThrows(BadRequestException.class, () ->
            controller.importConfiguration(request, response));
    }

    @Test
    public void importConfiguration() throws IOException {
        String password = "top secret!";
        final List<BackupWarning> warnings = List.of(BackupWarning.UPNP_PORT_FORWARDING_FAILURE);
        serviceImportResult.addWarnings(warnings);
        Path tmpFile = Files.createTempFile(tmpDir, ConfigurationBackupControllerImpl.FILE_PREFIX, ConfigurationBackupControllerImpl.FILE_SUFFIX);
        byte[] backupData = "Configuration backup data".getBytes();
        Files.write(tmpFile, backupData);
        ConfigBackupReference reference = new ConfigBackupReference(tmpFile.getFileName().toString(), password, false);
        Mockito.when(request.getBodyAs(ConfigBackupReference.class)).thenReturn(reference);
        ConfigBackupImportResult result = controller.importConfiguration(request, response);
        assertEquals(warnings, result.getWarnings());
    }
}