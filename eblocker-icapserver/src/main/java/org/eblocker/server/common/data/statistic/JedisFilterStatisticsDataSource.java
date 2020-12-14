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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.io.Closeable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Singleton
public class JedisFilterStatisticsDataSource implements FilterStatisticsDataSource {

    private static final Logger log = LoggerFactory.getLogger(JedisFilterStatisticsDataSource.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMddHHmm");

    private static final String KEY_PATTERN_COUNTER = "*_stats:[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]:*";
    private static final String KEY_PATTERN_COUNTER_FORMAT = "%s_stats:[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]:%s:*";

    private static final String KEY_TOTAL_RESET = "stats_total_reset";
    private static final String KEY_PATTERN_COUNTER_TOTAL = "stats_total:*";
    private static final String KEY_PATTERN_COUNTER_TOTAL_FORMAT = "stats_total:%s:%s%s";

    private final JedisPool jedisPool;

    @Inject
    public JedisFilterStatisticsDataSource(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public Stream<StatisticsCounter> getCounters() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = scanKeys(jedis, KEY_PATTERN_COUNTER, e -> true);
            return StreamSupport.stream(new StatisticsCounterSpliterator(jedisPool, keys), false);
        }
    }

    @Override
    public Stream<StatisticsCounter> getCounters(String type, IpAddress ipAddress, Instant begin, Instant end) {
        if (type == null) {
            type = "*";
        }

        String queryIpAddress = ipAddress == null ? "*" : ipAddress.toString().replaceAll(":", "_");

        Predicate<String> keyPredicate = key -> true;
        if (begin != null && end != null) {
            keyPredicate = new KeyDateTimePredicate(begin, end);
        }

        String keyPattern = String.format(KEY_PATTERN_COUNTER_FORMAT, type, queryIpAddress);
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = scanKeys(jedis, keyPattern, keyPredicate);
            StatisticsCounterSpliterator spliterator = new StatisticsCounterSpliterator(jedisPool, keys);
            Stream<StatisticsCounter> stream = StreamSupport.stream(spliterator, false);
            return stream.onClose(spliterator::close);
        }
    }

    @Override
    public void incrementCounters(Collection<StatisticsCounter> counters) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            counters.stream()
                    .map(this::mapCounterToKeyValue)
                    .forEach(t -> pipeline.incrBy(t.key, t.value));
            pipeline.sync();
        } catch (Exception e) {
            log.error("failed to increment statistics counters:", e);
        }
    }

    @Override
    public void deleteCountersBefore(Instant instant) {
        try (Jedis jedis = jedisPool.getResource()) {
            Predicate<String> keyPredicate = new KeyDateTimePredicate(Instant.EPOCH, instant);
            Set<String> keys = scanKeys(jedis, KEY_PATTERN_COUNTER, keyPredicate);
            Pipeline pipeline = jedis.pipelined();
            keys.forEach(pipeline::del);
            pipeline.sync();
        } catch (Exception e) {
            log.error("failed to delete counters", e);
        }
    }

    @Override
    public List<TotalCounter> getTotalCounters(String type) {
        try (Jedis jedis = jedisPool.getResource()) {
            String[] keys = jedis.keys(KEY_PATTERN_COUNTER_TOTAL).toArray(new String[0]);
            if (keys.length == 0) {
                return Collections.emptyList();
            }
            List<String> values = jedis.mget(keys);
            List<TotalCounter> totalCounters = new ArrayList<>();
            for (int i = 0; i < keys.length; ++i) {
                String[] splitKey = keys[i].split(":");
                if (type == null || type.equals(splitKey[1])) {
                    String reason = splitKey.length > 3 ? splitKey[3] : null;
                    int value = Integer.parseInt(values.get(i));
                    totalCounters.add(new TotalCounter(splitKey[1], splitKey[2], reason, value));
                }
            }
            return totalCounters;
        }
    }

    @Override
    public void incrementTotalCounters(Collection<TotalCounter> totalCounters) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (TotalCounter counter : totalCounters) {
                String reason = counter.getReason() != null ? ":" + counter.getReason() : "";
                pipeline.incrBy(String.format(KEY_PATTERN_COUNTER_TOTAL_FORMAT, counter.getType(), counter.getName(), reason), counter.getValue());
            }
            pipeline.sync();
        }
    }

    @Override
    public Instant getLastResetTotalCounters() {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(KEY_TOTAL_RESET);
            return Strings.isNullOrEmpty(value) ? null : Instant.ofEpochMilli(Long.parseLong(value));
        }
    }

    @Override
    public void resetTotalCounters() {
        try (Jedis jedis = jedisPool.getResource()) {
            String[] keys = jedis.keys(KEY_PATTERN_COUNTER_TOTAL).toArray(new String[0]);
            if (keys.length != 0) {
                jedis.del(keys);
            }
            jedis.set(KEY_TOTAL_RESET, String.valueOf(System.currentTimeMillis()));
        }
    }

    private Set<String> scanKeys(Jedis jedis, String pattern, Predicate<String> keyPredicate) {
        Set<String> keys = new HashSet<>();

        ScanParams params = new ScanParams().count(100).match(pattern);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            cursor = result.getStringCursor();
            result.getResult().stream().filter(keyPredicate).forEach(keys::add);
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        return keys;
    }

    private StatisticsCounter mapKeyValueToCounter(String key, String value) {
        String[] splitKey = key.split(":");
        String type = splitKey[0].replace("_stats", "");
        Instant instant = LocalDateTime.parse(splitKey[1], DATE_TIME_FORMATTER).atZone(ZoneId.systemDefault()).toInstant();
        IpAddress ipAddress = IpAddress.parse(splitKey[2].replaceAll("_", ":"));
        String name = splitKey[3];
        String reason = splitKey.length == 5 ? splitKey[4] : null;
        int count = Integer.parseInt(value);
        return new StatisticsCounter(instant, type, ipAddress, name, reason, count);
    }

    private Tuple<String, Integer> mapCounterToKeyValue(StatisticsCounter counter) {
        Tuple<String, Integer> tuple = new Tuple<>();
        tuple.key = String.format("%s_stats:%s:%s:%s%s",
                counter.getType(),
                DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(counter.getInstant(), ZoneId.systemDefault())),
                counter.getIpAddress(),
                counter.getName(),
                counter.getReason() != null ? ":" + counter.getReason() : "");
        tuple.value = counter.getValue();
        return tuple;
    }

    private class Tuple<K, V> {
        K key;
        V value;

        Tuple() {
        }
    }

    private class StatisticsCounterSpliterator implements Spliterator<StatisticsCounter>, Closeable {
        private static final int NUM = 256;

        private final Jedis jedis;
        private final Set<String> keys;

        private final List<String> fetchedKeys = new ArrayList<>(NUM);
        private List<String> fetchedValues = Collections.emptyList();
        private int index;

        StatisticsCounterSpliterator(JedisPool jedisPool, Set<String> keys) {
            this.jedis = jedisPool.getResource();
            this.keys = keys;
        }

        @Override
        public boolean tryAdvance(Consumer<? super StatisticsCounter> action) {
            if (index == fetchedValues.size() && !fillCache()) {
                return false;
            }

            action.accept(mapKeyValueToCounter(fetchedKeys.get(index), fetchedValues.get(index)));
            ++index;
            return true;
        }

        @Override
        public Spliterator<StatisticsCounter> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return DISTINCT | NONNULL;
        }

        @Override
        public void close() {
            this.jedis.close();
        }

        private boolean fillCache() {
            if (keys.isEmpty()) {
                return false;
            }

            index = 0;
            fetchedKeys.clear();
            int n = 0;
            for (Iterator<String> i = keys.iterator(); i.hasNext() && n < NUM; ++n) {
                fetchedKeys.add(i.next());
                i.remove();
            }

            fetchedValues = jedis.mget(fetchedKeys.toArray(new String[0]));
            return true;
        }
    }

    private class KeyDateTimePredicate implements Predicate<String> {
        private final String begin;
        private final String end;

        KeyDateTimePredicate(Instant begin, Instant end) {
            this.begin = DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(begin, ZoneId.systemDefault()));
            this.end = DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(end, ZoneId.systemDefault()));
        }

        @Override
        public boolean test(String s) {
            String t = s.split(":")[1];
            return t.compareTo(begin) >= 0 && t.compareTo(end) < 0;
        }
    }
}
