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
package org.eblocker.server.icap.resources;

import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourceHandler {
    private static final Logger log = LoggerFactory.getLogger(ResourceHandler.class);

    public static final String CLASSPATH_PREFIX = "classpath:";
    public static final String FILE_PREFIX = "file:";

    private ResourceHandler() {
    }

    public static boolean exists(EblockerResource resource) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return getClassPathInputStream(path.substring(CLASSPATH_PREFIX.length())) != null;
        }
        if (path.startsWith(FILE_PREFIX)) {
            return Files.exists(Paths.get(path.substring(FILE_PREFIX.length())));
        }
        if (path.startsWith("/")) {
            return Files.exists(Paths.get(path));
        }
        // Mainly for backward compatibility:
        return getClassPathInputStream(path) != null;
    }

    public static String load(EblockerResource resource) {
        try {
            InputStream in = getInputStream(resource);
            return IOUtils.toString(in, resource.getCharset());
        } catch (IOException e) {
            throw new EblockerException("Cannot open resource file " + resource.getPath() + " to load resource " + resource.getName(), e);
        }
    }

    public static List<String> readLines(EblockerResource resource) {
        InputStream inputStream = getInputStream(resource);
        try {
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error while reading all lines from this resource {}.", resource.getPath(), e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return null;
    }

    public static Set<String> readLinesAsSet(EblockerResource resource) {
        return new HashSet<>(readLines(resource));
    }

    public static InputStream getInputStream(EblockerResource resource) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return getClassPathInputStream(path.substring(CLASSPATH_PREFIX.length()));
        }
        if (path.startsWith(FILE_PREFIX)) {
            return getFileInputStream(path.substring(FILE_PREFIX.length()));
        }
        if (path.startsWith("/")) {
            return getFileInputStream(path);
        }
        // Mainly for backward compatibility:
        return getClassPathInputStream(path);
    }

    private static InputStream getFileInputStream(String path) {
        try {
            return new FileInputStream(path);

        } catch (FileNotFoundException e) {
            throw new EblockerException("Cannot open file resource " + path + " for reading: ", e);
        }
    }

    public static InputStream getClassPathInputStream(String path) {
        ClassLoader classLLoader = ClassLoader.getSystemClassLoader();
        InputStream in = classLLoader.getResourceAsStream(path);
        if (in == null) {
            String msg = "Cannot load classpath resource " + path;
            throw new EblockerException(msg);
        }
        return in;
    }

    public static void replaceContent(EblockerResource resource, Iterable<String> lines) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
        }
        if (path.startsWith(FILE_PREFIX)) {
            replaceFileContent(path.substring(FILE_PREFIX.length()), lines);
            return;
        }
        if (path.startsWith("/")) {
            replaceFileContent(path, lines);
            return;
        }
        // Mainly for backward compatibility:
        throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
    }

    public static void create(EblockerResource resource) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            throw new IllegalArgumentException("Cannot create classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
        }
        if (path.startsWith(FILE_PREFIX)) {
            createFile(path.substring(FILE_PREFIX.length()));
            return;
        }
        if (path.startsWith("/")) {
            createFile(path);
            return;
        }
        // Mainly for backward compatibility:
        throw new IllegalArgumentException("Cannot create classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
    }

    private static void createFile(String path) {
        try {
            Files.createFile(Paths.get(path));
        } catch (IOException e) {
            String msg = "Cannot create file resource " + path;
            log.error(msg, e);
            throw new EblockerException(msg);
        }
    }

    private static void replaceFileContent(String path, Iterable<String> lines) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8)) {
            //FIXME atomic write?!
            //Files.write(Paths.get(path),lines, StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            String msg = "Cannot replace content of file resource " + path + " with list of strings:" + e.toString();
            log.error(msg, e);
            throw new EblockerException(msg);
        }
    }

    public static void replaceContent(EblockerResource resource, Path temp) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
        }
        if (path.startsWith(FILE_PREFIX)) {
            replaceFileContent(path.substring(FILE_PREFIX.length()), temp);
            return;
        }
        if (path.startsWith("/")) {
            replaceFileContent(path, temp);
            return;
        }
        // Mainly for backward compatibility:
        throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
    }

    private static void replaceFileContent(String path, Path temp) {
        try {
            Files.move(temp, Paths.get(path), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            String msg = "Cannot replace content of file resource " + path + " with " + temp.toString() + ": " + e.getMessage();
            log.error(msg, e);
            throw new EblockerException(msg);
        }
    }

    public static Date getDate(EblockerResource resource) {
        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return null;
        }
        if (path.startsWith(FILE_PREFIX)) {
            return getLastModificationDate(path.substring(FILE_PREFIX.length()));
        }
        if (path.startsWith("/")) {
            return getLastModificationDate(path);
        }
        // Mainly for backward compatibility:
        return null;
    }

    private static Date getLastModificationDate(String path) {
        FileTime fileTime;
        try {
            fileTime = Files.getLastModifiedTime(Paths.get(path));
        } catch (IOException e) {
            log.warn("Cannot open file resource " + path + " to determine last modified date: ", e);
            return null;
        }
        return new Date(fileTime.toMillis());
    }

    /**
     * Write a String to a resource file
     *
     * @param resource
     * @param content  String to write to resource
     */
    public static void write(EblockerResource resource, String content) {

        String path = resource.getPath();
        if (path.startsWith(CLASSPATH_PREFIX)) {
            throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");
        }
        if (path.startsWith(FILE_PREFIX)) {
            writeString(path.substring(FILE_PREFIX.length()), content);
            return;
        }
        if (path.startsWith("/")) {
            writeString(path, content);
            return;
        }
        // Mainly for backward compatibility:
        throw new IllegalArgumentException("Cannot write to classpath resource " + resource.getName() + " [" + resource.getPath() + "]");

    }

    private static void writeString(String path, String content) {
        if (path != null && content != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8)) {
                writer.write(content);
            } catch (IOException e) {
                String msg = "Could not write the string: " + content + " ; to the file here " + path;
                log.error(msg, e);
                throw new EblockerException(msg);
            }
        }
    }

    public static void writeLines(EblockerResource resource, Collection<String> lines) {
        StringBuilder builder = new StringBuilder();
        lines.forEach(line -> builder.append(line).append('\n'));
        write(resource, builder.toString());
    }
}
