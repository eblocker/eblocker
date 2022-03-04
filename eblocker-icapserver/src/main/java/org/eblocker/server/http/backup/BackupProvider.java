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
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.IpAddressModule;

import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * A BackupProvider exports/imports configuration data to/from one or more
 * files in the configuration JAR file.
 */
public abstract class BackupProvider {
    final ObjectMapper objectMapper; // provide a non-closing ObjectMapper for derived classes

    public BackupProvider() {
        // It is important that the ObjectMapper does not close the stream,
        // because that would close the JAR file.
        JsonFactory jsonFactory = new MappingJsonFactory();
        jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        objectMapper = new ObjectMapper(jsonFactory);
        initializeMapper(objectMapper);
        objectMapper.registerModule(new IpAddressModule());
    }

    /**
     * Use settings similar to those of RestExpress's JacksonJsonProcessor
     *
     * @param objectMapper
     */
    protected void initializeMapper(ObjectMapper objectMapper) {
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
     * @param outputStream
     * @param cryptoService is null if the user has not provided a password
     * @throws IOException
     */
    public abstract void exportConfiguration(JarOutputStream outputStream, CryptoService cryptoService) throws IOException;

    /**
     * Import the configuration from the given JarInputStream.
     * @param inputStream
     * @param cryptoService is null if the user has not provided a password
     * @param schemaVersion
     * @throws IOException
     */
    public abstract void importConfiguration(JarInputStream inputStream, CryptoService cryptoService, int schemaVersion) throws IOException;
}
