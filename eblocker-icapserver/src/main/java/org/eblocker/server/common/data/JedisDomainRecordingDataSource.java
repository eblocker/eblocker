/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.recorder.RecordedDomainBin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persists recorded domain requests. Requests are saved in bins which expire after a day.
 */
@Singleton
public class JedisDomainRecordingDataSource implements DomainRecordingDataSource {
    private static final Logger LOG = LoggerFactory.getLogger(JedisDomainRecordingDataSource.class);
    private static final String KEY_PREFIX = "recorded_domains:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final long binLifetimeInSeconds;

    @Inject
    public JedisDomainRecordingDataSource(JedisPool jedisPool,
                                          ObjectMapper objectMapper,
                                          @Named("domainRecorder.binLifetimeInSeconds") long binLifetimeInSeconds) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.binLifetimeInSeconds = binLifetimeInSeconds;
    }

    @Override
    public Set<RecordedDomainBin> getBins(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = JedisUtils.scanKeys(jedis, getKeyPattern(deviceId));
            return keys.stream()
                    .map(key -> getBin(jedis, key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
        }
    }

    private String getKeyPattern(String deviceId) {
        return KEY_PREFIX + deviceId + ":*";
    }

    private Optional<RecordedDomainBin> getBin(Jedis jedis, String key) {
        try {
            String json = jedis.get(key);
            return Optional.of(objectMapper.readValue(json, RecordedDomainBin.class));
        } catch (Exception e) {
            LOG.error("Could not get RecordedDomainBin for key {}. Already expired?", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(String deviceId, RecordedDomainBin bin) {
        String key = KEY_PREFIX + deviceId + ":" + bin.getBegin().toEpochMilli();
        Instant expiresAt = getExpiration(bin);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, objectMapper.writeValueAsString(bin));
            jedis.pexpireAt(key, expiresAt.toEpochMilli());
        } catch (JsonProcessingException e) {
            LOG.error("Could not save recorded domains for device {}", deviceId, e);
        }
    }

    public Instant getExpiration(RecordedDomainBin bin) {
        return bin.getEnd().plusSeconds(binLifetimeInSeconds);
    }

    @Override
    public void removeBins(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = JedisUtils.scanKeys(jedis, getKeyPattern(deviceId));
            for (String key : keys) {
                jedis.del(key);
            }
        }
    }
}
