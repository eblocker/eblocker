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
package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.data.DataSource;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Migrates eblocker-dns enabled flag from redis to file for version 1.7
 */
public class SchemaMigrationVersion14 implements SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion14.class);

    private final DataSource dataSource;
    private final JedisPool jedisPool;
    private final Path enabledFlagFilePath;

    @Inject
    public SchemaMigrationVersion14(
        DataSource dataSource,
        JedisPool jedisPool,
        @Named("dns.server.enabled.file") String enabledFlagFile
    ) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
        this.enabledFlagFilePath = Paths.get(enabledFlagFile);
    }

    @Override
    public String getSourceVersion() {
        return "13";
    }

    @Override
    public String getTargetVersion() {
        return "14";
    }

    @Override
    public void migrate() {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean enabled = Boolean.parseBoolean(jedis.get(DefaultEntities.DNS_ENABLED_KEY));
            boolean flagFileExists = Files.exists(enabledFlagFilePath);
            if (enabled && !flagFileExists) {
                createFlagFile();
            } else if (!enabled && flagFileExists) {
                deleteFlagFile();
            }
        }
        dataSource.setVersion("14");
    }

    private void createFlagFile() {
        try {
            Files.createFile(enabledFlagFilePath);
        } catch (IOException e) {
            log.error("failed to create file {}", enabledFlagFilePath, e);
        }
    }

    private void deleteFlagFile() {
        try {
            Files.delete(enabledFlagFilePath);
        } catch (IOException e) {
            log.error("failed to delete file {}", enabledFlagFilePath, e);
        }
    }
}
