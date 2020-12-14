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
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class TailReaderTest {

    private final long SLEEP = 100;
    private Path path;

    @Before
    public void setUp() throws IOException {
        path = Files.createTempFile("tail-reader-test", "");
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(path);
    }

    @Test(timeout = 5000)
    public void testTailingNoSeek() throws IOException {
        Files.write(path, new byte[]{ (byte) 0x7f });

        Reader in = new TailReader(path.toString(), false, SLEEP);

        startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

        Assert.assertEquals(127, in.read());
        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());
    }

    @Test(timeout = 5000)
    public void testTailingSeek() throws IOException {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xff);
        Files.write(path, bytes);

        Reader in = new TailReader(path.toString(), true, SLEEP);

        startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());
    }

    @Test(timeout = 5000)
    public void testTruncate() throws IOException {
        TailReader in = new TailReader(path.toString(), false, SLEEP);

        startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());

        Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);

        startGenerator(new byte[]{ 5, 6, 7, 8, 9 }, 150);

        Assert.assertEquals(5, in.read());
        Assert.assertEquals(6, in.read());
        Assert.assertEquals(7, in.read());
        Assert.assertEquals(8, in.read());
        Assert.assertEquals(9, in.read());
    }

    @Test(timeout = 5000)
    public void testRollover() throws IOException {
        Reader in = new TailReader(path.toString(), false, SLEEP);

        startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());

        Files.write(path, new byte[0], StandardOpenOption.CREATE);

        startGenerator(new byte[]{ 5, 6, 7, 8, 9 }, 150);

        Assert.assertEquals(5, in.read());
        Assert.assertEquals(6, in.read());
        Assert.assertEquals(7, in.read());
        Assert.assertEquals(8, in.read());
        Assert.assertEquals(9, in.read());
    }

    @Test(timeout = 5000)
    public void testRolloverNoInstantCreate() throws IOException {
        Reader in = new TailReader(path.toString(), false, SLEEP);

        new Thread(() -> {
            try {
                startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

                Thread.sleep(1000);
                Files.delete(path);

                Thread.sleep(1000);
                Files.write(path, new byte[0]);

                startGenerator(new byte[]{ 5, 6, 7, 8, 9, 10 }, 150);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());
        Assert.assertEquals(5, in.read());
        Assert.assertEquals(6, in.read());
        Assert.assertEquals(7, in.read());
        Assert.assertEquals(8, in.read());
        Assert.assertEquals(9, in.read());
    }

    @Test(timeout = 5000)
    public void testNoFileOnStart() throws IOException, InterruptedException {
        Files.delete(path);

        Reader in = new TailReader(path.toString(), false, SLEEP);

        Thread.sleep(1000);
        startGenerator(new byte[]{ 0, 1, 2, 3, 4 }, 150);

        Assert.assertEquals(0, in.read());
        Assert.assertEquals(1, in.read());
        Assert.assertEquals(2, in.read());
        Assert.assertEquals(3, in.read());
        Assert.assertEquals(4, in.read());
    }

    @Test(timeout = 5000)
    public void testLineReading() throws IOException {
        try (BufferedReader br = new BufferedReader(new TailReader(path.toString(), false, SLEEP))) {
            startGenerator("hello\nworld\nanother\nline\ngood bye\n".getBytes(), 53);
            Assert.assertEquals("hello", br.readLine());
            Assert.assertEquals("world", br.readLine());
            Assert.assertEquals("another", br.readLine());
            Assert.assertEquals("line", br.readLine());
            Assert.assertEquals("good bye", br.readLine());
        }
    }

    private void startGenerator(byte[] bytes, long sleep) {
        new Thread(() -> {
            try (FileOutputStream out = new FileOutputStream(path.toFile(), true)) {
                for (byte b : bytes) {
                    out.write(b);
                    out.flush();
                    Thread.sleep(sleep);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}

