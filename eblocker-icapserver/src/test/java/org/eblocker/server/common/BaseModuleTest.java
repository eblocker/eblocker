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
package org.eblocker.server.common;

import org.eblocker.crypto.keys.KeyWrapper;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BaseModuleTest {

    @Test
    public void test_provideSystemKeyWrapper() throws Exception {
        BaseModule baseModule = new BaseModule();

        // Not existing key - key should be generated
        String systemKeyFile = createResource("system.", ".key", true);
        KeyWrapper key1 = baseModule.provideSystemKeyWrapper(systemKeyFile);
        assertNotNull(key1);

        // Existing key - key should be loaded
        KeyWrapper key2 = baseModule.provideSystemKeyWrapper(systemKeyFile);
        assertNotNull(key2);
        assertArrayEquals(key1.get(), key2.get());

        // Empty key file  - new key should be generated
        String emptyFile = createResource("system.", ".key", false);
        KeyWrapper key3 = baseModule.provideSystemKeyWrapper(emptyFile);
        assertNotNull(key3);
        assertEquals(key1.get().length, key3.get().length);
        boolean different = false;
        for (int i = 0; i < key1.get().length; i++) {
            different = different || key1.get()[i] != key3.get()[i];
        }
        assertTrue(different);
    }

    private String createResource(String prefix, String postfix, boolean delete) {
        try {
            Path path = Files.createTempFile(prefix, postfix);
            if (delete) {
                Files.delete(path);
            }
            return path.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create resource file: " + e.getMessage(), e);
        }
    }

}
