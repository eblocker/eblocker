package org.eblocker.server.common.data;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class JedisUtils {
    public static final int SCAN_BATCH_SIZE = 100;

    public static Set<String> scanKeys(Jedis jedis, String pattern) {
        return scanKeys(jedis, pattern, key -> true);
    }

    public static Set<String> scanKeys(Jedis jedis, String pattern, Predicate<String> keyPredicate) {
        Set<String> keys = new HashSet<>();

        ScanParams params = new ScanParams().count(SCAN_BATCH_SIZE).match(pattern);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            cursor = result.getStringCursor();
            result.getResult().stream().filter(keyPredicate).forEach(keys::add);
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        return keys;
    }
}
