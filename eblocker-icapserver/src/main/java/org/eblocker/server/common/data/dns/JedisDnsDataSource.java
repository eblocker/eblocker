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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class JedisDnsDataSource implements DnsDataSource {

    private static final Logger log = LoggerFactory.getLogger(JedisDnsDataSource.class);

    private static final String KEY_PREFIX_EVENTS = "dns_stats:";
    private static final String KEY_PATTERN_EVENTS = "dns_stats:[a-z]*";
    private static final String DNS_QUERY_QUEUE = "dns_query";
    private static final String DNS_RESOLUTION_QUEUE_PREFIX = "dns_response:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    @Inject
    public JedisDnsDataSource(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
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
            List<String> keys = scanKeys(jedis, KEY_PATTERN_EVENTS);

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

    @Override
    public void addDnsQueryQueue(String id, String nameServer, List<DnsQuery> queries) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = id + "," + nameServer + "," + queries.stream().map(DnsQuery::toString).collect(Collectors.joining(","));
            jedis.lpush(DNS_QUERY_QUEUE, value);
        }
    }

    @Override
    public DnsDataSourceDnsResponse popDnsResolutionQueue(String id, int timeout) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> values = jedis.blpop(timeout, DNS_RESOLUTION_QUEUE_PREFIX + id);
            if (values == null || values.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(values.get(1), DnsDataSourceDnsResponse.class);
        } catch (IOException e) {
            log.error("failed reading result: {}", DNS_RESOLUTION_QUEUE_PREFIX + id, e);
            return null;
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

    private List<String> scanKeys(Jedis jedis, String pattern) {
        List<String> keys = new ArrayList<>();

        ScanParams params = new ScanParams().count(10).match(pattern);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            cursor = result.getStringCursor();
            keys.addAll(result.getResult());
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        return keys;
    }
}
