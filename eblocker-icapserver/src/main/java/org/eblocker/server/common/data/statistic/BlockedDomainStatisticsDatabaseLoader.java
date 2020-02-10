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
package org.eblocker.server.common.data.statistic;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BlockedDomainStatisticsDatabaseLoader {

    private static final Logger log = LoggerFactory.getLogger(BlockedDomainStatisticsDatabaseLoader.class);

    public DB createOrOpen(String dbPath) {
        try {
            return createOpen(dbPath);
        } catch (IOError e) {
            log.error("failed to load database", e);
            if (Files.exists(Paths.get(dbPath))) {
                try {
                    Files.delete(Paths.get(dbPath));
                    return createOpen(dbPath);
                } catch (IOError | IOException ie) {
                    log.error("failed to create fresh database", ie);
                }
            }
            log.error("statistics for blocked domains will be unavailable!");
            return null;
        }
    }

    private static DB createOpen(String dbPath) {
        return DBMaker
            .newFileDB(new File(dbPath))
            .cacheDisable()
            .closeOnJvmShutdown()
            .make();
    }

}
