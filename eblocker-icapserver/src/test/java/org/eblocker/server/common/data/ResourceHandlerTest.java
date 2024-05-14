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
package org.eblocker.server.common.data;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ResourceHandlerTest {
    private Path testFile, testFile2;

    @BeforeEach
    public void setup() throws IOException {
        testFile = Files.createTempFile("testfile", null);
        testFile2 = Path.of(testFile.toString() + "2");
    }

    @Test
    public void testWritingListOfStringsToFile() {
        SimpleResource existingFile = new SimpleResource(testFile.toString());
        List<String> strings = List.of("Line 0", "Line 1", "Line 2");

        ResourceHandler.replaceContent(existingFile, strings);

        Assertions.assertEquals(strings, ResourceHandler.readLines(existingFile));
    }

    @Test
    public void testExist() {
        SimpleResource existingResource = new SimpleResource("classpath:test-data/resource-handler-test.txt");
        Assertions.assertTrue(ResourceHandler.exists(existingResource));

        SimpleResource missingResource = new SimpleResource("classpath:test-data/no-such-resource.txt");
        Assertions.assertFalse(ResourceHandler.exists(missingResource));

    }

    @Test
    public void testCreate() throws IOException {
        SimpleResource resource = new SimpleResource(testFile2.toString());
        Assertions.assertFalse(ResourceHandler.exists(resource));

        ResourceHandler.create(resource);
        Assertions.assertTrue(ResourceHandler.exists(resource));

        // clean up
        Files.delete(testFile2);
        Assertions.assertFalse(ResourceHandler.exists(resource));
    }

    @AfterEach
    public void cleanUp() throws IOException {
        Files.deleteIfExists(testFile);
    }
}
