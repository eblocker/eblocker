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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceHandlerTest {

    private final static String TEST_FILE_PATH = "/tmp/testfile";
    private final static String TEST_FILE_PATH2 = "/tmp/testfile2";

    @Before
    public void setup() {
        if (!Files.exists(Paths.get(TEST_FILE_PATH))) {
            try {
                Files.createFile(Paths.get(TEST_FILE_PATH));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testWritingListOfStringsToFile() {
        SimpleResource existingFile = new SimpleResource(TEST_FILE_PATH);
        List<String> strings = new LinkedList<String>();
        strings.add("Line 0");
        strings.add("Line 1");
        strings.add("Line 2");
        strings.add("Line 3");

        ResourceHandler.replaceContent(existingFile, strings);
    }

    @Test
    public void testCreate() throws IOException {
        SimpleResource resource = new SimpleResource(TEST_FILE_PATH2);
        assertFalse(ResourceHandler.exists(resource));

        ResourceHandler.create(resource);
        assertTrue(ResourceHandler.exists(resource));

        // clean up
        Files.delete(Paths.get(TEST_FILE_PATH2));
        assertFalse(ResourceHandler.exists(resource));
    }

    @After
    public void cleanUp() {
        try {
            Files.delete(Paths.get(TEST_FILE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
