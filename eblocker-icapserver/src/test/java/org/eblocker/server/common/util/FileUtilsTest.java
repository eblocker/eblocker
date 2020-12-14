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
package org.eblocker.server.common.util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileUtilsTest {

    private List<Path> createdPaths = new ArrayList<>();

    @After
    public void removeCreatedPaths() {
        Collections.reverse(createdPaths);
        createdPaths.forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    public void testRecursiveDelete() throws IOException {
        List<Path> createdPaths = new ArrayList<>();

        // create a directory and populate it with files and sub-directory
        Path directory = Files.createTempDirectory("file-utils-test");
        createdPaths.add(directory);

        Path file = Files.createFile(Paths.get(directory.toString() + "/file"));
        createdPaths.add(file);

        Path innerDirectory = Files.createDirectory(Paths.get(directory.toString() + "/inner"));
        createdPaths.add(innerDirectory);

        Path innerFile = Files.createFile(Paths.get(directory.toString() + "/inner/file"));
        createdPaths.add(innerFile);

        // delete directory and check all files are gone
        FileUtils.deleteDirectory(directory);
        createdPaths.forEach(p -> Assert.assertFalse(Files.exists(p)));
    }

}
