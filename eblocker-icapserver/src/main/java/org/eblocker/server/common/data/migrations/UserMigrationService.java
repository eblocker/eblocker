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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

@SubSystemService(value = SubSystem.SERVICES)
public class UserMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(UserMigrationService.class);
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    @Inject
    public UserMigrationService(JedisPool jedisPool,
                                ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    /**
     * For new parental control we need to remove the dashboard cards from the user and replace them
     * by dashboard card IDs. Since the getter for the dashboard cards is used in the SchemaMigrations,
     * we cannot remove it w/o breaking the migration. So we deprecate the old user module and introduce
     * a new one in the latest SchemaMigration (version 38), in which we migrate UserModuleOld to UserModule.
     * <p>
     * To load the old UserModule into the deprecated type (UserModuleOld), we have to access the datastore
     * directly. Hence the Jedis operations.
     */
    public List<UserModuleOld> getAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            List<UserModuleOld> allOldUsers = new ArrayList<>();

            // It must be UserModule here, because the old users have been saved as "UserModule:..."
            jedis.keys(UserModule.class.getSimpleName() + ":" + "[0-9]*").forEach(s -> {
                try {
                    // here it must be UserModuleOld, because the old UserModules won't fit into the new UserModule model
                    allOldUsers.add(objectMapper.readValue(jedis.get(s), UserModuleOld.class));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return allOldUsers;
        }
    }

    public UserModuleOld get(Class<UserModuleOld> entityClass, Integer id) {
        String stringId = "UserModule:" + id;
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(stringId);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, entityClass);

        } catch (IOException e) {
            logger.error("Could not get UserModule.", e);
            return null;
        }
    }

    public void delete(Class<UserModuleOld> entityClass, Integer id) {
        String key = "UserModule:" + id;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    public UserModuleOld save(UserModuleOld oldUser, Integer id) {
        try {
            try (Jedis jedis = jedisPool.getResource()) {
                String json = objectMapper.writeValueAsString(oldUser);
                jedis.set(UserModule.class.getSimpleName() + ":" + id, json);
                return oldUser;
            }
        } catch (JsonProcessingException e) {
            logger.error("Could not save UserModule.", e);
            return null;
        }
    }

}


