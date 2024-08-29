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
package org.eblocker.server.common.data.dns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.JedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class JedisDnsDataSource implements DnsDataSource {

    private static final Logger log = LoggerFactory.getLogger(JedisDnsDataSource.class);

    private static final String KEY_PREFIX_EVENTS = "dns_stats:";
    private static final String KEY_PATTERN_EVENTS = "dns_stats:[a-z]*";

    private final JedisPool jedisPool;

    @Inject
    public JedisDnsDataSource(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public List<ResolverEvent> getEventsByResolver(String resolver) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, List<ResolverEvent>> eventsByResolver = getEventsByResolver(jedis, Collections.singleton(KEY_PREFIX_EVENTS + resolver));
            return eventsByResolver.get(resolver);
        }
    }

    @Override
    public void deleteEventsBefore(Instant t) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = JedisUtils.scanKeys(jedis, KEY_PATTERN_EVENTS);

            Map<String, List<ResolverEvent>> eventsByResolver = getEventsByResolver(jedis, keys);
            Pipeline pipeline = jedis.pipelined();
            for (Map.Entry<String, List<ResolverEvent>> e : eventsByResolver.entrySet()) {
                int index = findLastIndexBefore(t, e.getValue());
                for (int i = 0; i <= index; ++i) {
                    pipeline.lpop(KEY_PREFIX_EVENTS + e.getKey());
                }
            }
            pipeline.sync();

        }
    }

    private Map<String, List<ResolverEvent>> getEventsByResolver(Jedis jedis, Collection<String> keys) {
        Pipeline pipeline = jedis.pipelined();
        Map<String, Response<List<String>>> responsesByKeys = keys.stream()
                .collect(Collectors.toMap(key -> key.split(":")[1], key -> pipeline.lrange(key, 0, -1)));
        pipeline.sync();

        return responsesByKeys.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get().stream().map(ResolverEvent::new).collect(Collectors.toList())));
    }

    private Integer findLastIndexBefore(Instant t, List<ResolverEvent> events) {
        int i = 0;
        while (i < events.size() && events.get(i).getInstant().isBefore(t)) {
            ++i;
        }
        return i - 1;
    }
}
