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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TailReader extends Reader {

    private final String fileName;
    private final long sleep;
    private FileReader reader;
    private int read;
    private volatile boolean quit;

    public TailReader(String fileName, boolean seekEndOfFile, long sleep) throws IOException {
        this.fileName = fileName;
        this.sleep = sleep;
        quit = false;

        if (seekEndOfFile) {
            checkFile();
            if (reader != null) {
                reader.skip(Files.size(Paths.get(fileName)));
            }
        }
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        while (!quit) {
            int n = tryRead(cbuf, off, len);
            if (n > 0) {
                return n;
            } else if (n == -1) {
                checkFile();
            }
            sleep();
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
        quit = true;
    }

    private int tryRead(char[] cbuf, int off, int len) throws IOException {
        if (reader != null) {
            int n = reader.read(cbuf, off, len);
            if (n > 0) {
                read += n;
                return n;
            }
        }
        return -1;
    }

    private void checkFile() throws IOException {
        try {
            Path path = Paths.get(fileName);
            if (Files.exists(path) && (reader == null || Files.size(Paths.get(fileName)) < read)) {
                if (reader != null) {
                    reader.close();
                }
                reader = new FileReader(fileName);
                read = 0;
            }
        } catch (FileNotFoundException e) { //NOSONAR
            // might happen if file is deleted just after Files.exists && Files.size, just try again later
        }
    }

    private void sleep() {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            quit = true;
        }
    }
}
