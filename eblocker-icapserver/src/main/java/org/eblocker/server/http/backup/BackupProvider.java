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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.json.JsonEncryptionModule;
import org.eblocker.server.common.data.IpAddressModule;
import org.eblocker.server.common.data.backup.BackupWarning;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * A BackupProvider exports/imports configuration data to/from one or more
 * files in the configuration JAR file.
 *
 * A new BackupProvider is created for each export, import and verification action.
 *
 * It stores any warnings that may have occurred during an action. For example,
 * if the password was not provided for the import (because the user forgot it),
 * all providers that restore encrypted data should add a warning that they could
 * not restore it.
 */
public abstract class BackupProvider {
    final ObjectMapper objectMapper; // provide a non-closing ObjectMapper for derived classes
    final private List<BackupWarning> warnings = new ArrayList<>();
    protected boolean encryptionEnabled;

    public BackupProvider() {
        // It is important that the ObjectMapper does not close the stream,
        // because that would close the JAR file.
        JsonFactory jsonFactory = new MappingJsonFactory();
        jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        objectMapper = new ObjectMapper(jsonFactory);
        initializeMapper(objectMapper);
        objectMapper.registerModule(new IpAddressModule());
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Create a backup provider with optional support for @JsonEncrypt annotation.
     * @param cryptoService if not null, a JsonEncryptionModule is registered in the ObjectMapper
     */
    public BackupProvider(@Nullable CryptoService cryptoService) {
        this();
        if (cryptoService != null) {
            encryptionEnabled = true;
            objectMapper.registerModule(new JsonEncryptionModule(objectMapper, cryptoService));
        }
    }

    /**
     * Use settings similar to those of RestExpress's JacksonJsonProcessor
     *
     */
    private void initializeMapper(ObjectMapper objectMapper) {
        objectMapper
                // Ignore additional/unknown properties in a payload.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                // Use fields directly.
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

                // Ignore accessor and mutator methods (use fields per above).
                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    /**
     * Export the configuration to the given JarOutputStream.
     */
    public abstract void exportConfiguration(JarOutputStream outputStream) throws IOException;

    /**
     * Import the configuration from the given JarInputStream.
     */
    public abstract void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException;

    /**
     * Verify the configuration from the given JarInputStream.
     */
    public abstract void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException;

    /**
     * Write the next entry into the given JarOutputStream.
     * @param outputStream JarOutputStream
     * @param name name of the next entry
     * @param content content of the next entry
     * @throws IOException
     */
    protected static void writeNextEntry(JarOutputStream outputStream, String name, byte[] content) throws IOException {
        JarEntry entry = new JarEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    /**
     * Read the next entry from a JarInputStream and make sure it has the expected name and is not a directory
     * @param jarStream JarInputStream
     * @param name expected name of the next entry. If it does not match, a CorruptedBackupException is thrown.
     * @throws IOException
     */
    protected static void getNextEntry(JarInputStream jarStream, String name) throws IOException {
        JarEntry entry = jarStream.getNextJarEntry();
        if (entry == null) {
            throw new CorruptedBackupException("Expected entry " + name + ", but stream does not have a next entry.");
        }
        if (!entry.getName().equals(name)) {
            throw new CorruptedBackupException("Expected entry " + name + ", got " + entry.getName());
        }
        if (entry.isDirectory()) {
            throw new CorruptedBackupException("Entry " + name + " is a directory.");
        }
    }

    protected void addWarning(BackupWarning warning) {
        warnings.add(warning);
    }

    public List<BackupWarning> getWarnings() {
        return warnings;
    }

    public boolean canEncrypt() {
        return encryptionEnabled;
    }

    public boolean canDecrypt() {
        return encryptionEnabled;
    }
}
