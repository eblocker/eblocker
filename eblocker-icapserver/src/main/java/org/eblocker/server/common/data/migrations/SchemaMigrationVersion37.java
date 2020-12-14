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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

public class SchemaMigrationVersion37 implements SchemaMigration {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion37.class);

    private final DataSource dataSource;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    @Inject
    public SchemaMigrationVersion37(DataSource dataSource, JedisPool jedisPool, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceVersion() {
        return "36";
    }

    @Override
    public String getTargetVersion() {
        return "37";
    }

    @Override
    public void migrate() {
        try (Jedis jedis = jedisPool.getResource()) {
            update(jedis, "OpenVpnClientState:[0-9]*", this::updateMetaData);
        }

        dataSource.setVersion("37");
    }

    private void update(Jedis jedis, String keyPattern, Function<String, String> updateFunction) {
        Set<String> keys = jedis.keys(keyPattern);
        for (String key : keys) {
            String json = jedis.get(key);
            String updatedJson = updateFunction.apply(json);
            if (!json.equals(updatedJson)) {
                jedis.set(key, updatedJson);
            }
        }
    }

    private String updateMetaData(String json) {
        try {
            ObjectNode metaDataNode = (ObjectNode) objectMapper.readTree(json);
            JsonNode activeFlagNode = metaDataNode.remove("active");
            OpenVpnClientState.State state = mapClientState(activeFlagNode);
            metaDataNode.put("state", state.name());
            return objectMapper.writeValueAsString(metaDataNode);
        } catch (IOException e) {
            log.error("failed to update {}", json, e);
            return json;
        }
    }

    private OpenVpnClientState.State mapClientState(JsonNode node) {
        if (node == null || node.isNull() || !node.isBoolean() || !node.booleanValue()) {
            return OpenVpnClientState.State.INACTIVE;
        }
        return OpenVpnClientState.State.ACTIVE;
    }
}
