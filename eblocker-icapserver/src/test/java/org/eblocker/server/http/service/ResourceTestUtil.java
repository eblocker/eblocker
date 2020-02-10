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
package org.eblocker.server.http.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ResourceTestUtil {

    private static Path tempDir;

    private static Path getTempDir() throws IOException {
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("eblocker-test-");
        }
        return tempDir;
    }

    public static Path provideResourceAsFile(String resource) throws IOException {
        String prefix = resource;
        if (resource.contains("/")) {
            prefix = resource.substring(resource.lastIndexOf("/")+1);
        }
        Path path = Files.createTempFile(getTempDir(), prefix, ".tmp");
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(resource);
        if (in != null) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return path;
    }
}
