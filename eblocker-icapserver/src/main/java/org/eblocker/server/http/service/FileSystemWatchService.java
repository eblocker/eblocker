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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Singleton
public class FileSystemWatchService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemWatchService.class);

    private WatchService watchService;
    private Set<Path> watchedDirectories = new HashSet<>();
    private Map<WatchKey, Path> watchKeys = new HashMap<>();
    private Map<Path, Consumer<Path>> callbacksByPath = new HashMap<>();

    @Inject
    public FileSystemWatchService(@Named("unlimitedCachePoolExecutor") Executor executor) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        executor.execute(this::watch);
    }

    public synchronized void watch(Path path, Consumer<Path> callback, WatchEvent.Modifier... modifier) throws IOException {
        Path directory = path.getParent();
        if (!watchedDirectories.contains(directory)) {
            log.debug("registering directory {}", directory);
            WatchKey key = directory.register(watchService, new WatchEvent.Kind[]{ StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY }, modifier);
            watchKeys.put(key, directory);
            watchedDirectories.add(directory);
        }
        callbacksByPath.put(path, callback);
        log.debug("registered callback for {}", path);
    }

    @SuppressWarnings("unchecked")
    private void watch() {
        try {
            WatchKey watchKey;
            while ((watchKey = watchService.take()) != null) {
                Path directory = watchKeys.get(watchKey);
                watchKey.pollEvents().forEach(e -> handleEvent(directory, (WatchEvent<Path>) e));
                watchKey.reset();
            }
        } catch (InterruptedException e) {
            log.info("thread interrupted, exiting", e);
            Thread.currentThread().interrupt();
        }
    }

    private void handleEvent(Path directory, WatchEvent<Path> event) {
        Path file = event.context();
        Path fullPath = directory.resolve(file);
        Consumer<Path> callback = callbacksByPath.get(fullPath);
        if (callback != null) {
            log.debug("found callback for {}", fullPath);
            try {
                callback.accept(fullPath);
            } catch (Exception e) {
                log.error("exception in callback for {}", fullPath, e);
            }
        } else {
            log.debug("no callback registered for {}", fullPath);
        }
    }
}
