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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackupProviderTest {
    private JarInputStream testJar;

    @BeforeEach
    public void makeTestJar() throws IOException {
        testJar = makeJarWith((jarOut) -> {
            for (int i = 1; i <= 2; i++) {
                try {
                    byte[] content = ("Content " + i).getBytes(StandardCharsets.UTF_8);
                    BackupProvider.writeNextEntry(jarOut, "MyEntry " + i, content);
                } catch (IOException e) {
                    throw new RuntimeException("Could not write MyEntry " + i, e);
                }
            }
        });
    }

    private JarInputStream makeJarWith(Consumer<JarOutputStream> consumer) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        JarOutputStream jarOut = new JarOutputStream(bytesOut);
        consumer.accept(jarOut);
        jarOut.close();
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytesOut.toByteArray());
        return new JarInputStream(byteStream);
    }

    @AfterEach
    public void closeTestJar() throws IOException {
        testJar.close();
    }

    @Test
    public void readAllEntries() throws IOException {
        for (int i = 1; i <= 2; i++) {
            BackupProvider.getNextEntry(testJar, "MyEntry " + i);
            byte[] content = testJar.readAllBytes();
            assertArrayEquals(("Content " + i).getBytes(StandardCharsets.UTF_8), content);
        }
    }

    @Test
    public void continueAfterFailure() throws IOException {
        // The first entry does not match...
        assertThrows(CorruptedBackupException.class, () -> {
            BackupProvider.getNextEntry(testJar, "MyEntry 404");
        });
        // ... but we can still read the second entry.
        BackupProvider.getNextEntry(testJar, "MyEntry 2");
        byte[] content = testJar.readAllBytes();
        assertArrayEquals(("Content 2").getBytes(StandardCharsets.UTF_8), content);
    }

    @Test
    public void noMoreEntries() throws IOException {
        readAllEntries();
        // There is no third entry
        assertThrows(CorruptedBackupException.class, () -> {
            BackupProvider.getNextEntry(testJar, "MyEntry 3");
        });
    }

    @Test
    public void unexpectedEntry() {
        assertThrows(CorruptedBackupException.class, () -> {
            BackupProvider.getNextEntry(testJar, "MyEntry 404");
        });
    }

    @Test
    public void rejectDirectories() throws IOException {
        JarInputStream jarIn = makeJarWith((jarOut) -> {
            JarEntry entry = new JarEntry("MyDirectory/");
            try {
                jarOut.putNextEntry(entry);
                jarOut.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException("Could not add MyDirectory", e);
            }
        });
        assertThrows(CorruptedBackupException.class, () -> {
            BackupProvider.getNextEntry(jarIn, "MyDirectory/");
        });
    }
}