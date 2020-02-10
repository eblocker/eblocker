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
package org.eblocker.server.common.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class provides consistency checks for data stored in redis and tries to repair them.
 *
 * Checks are run before migrations so it must depend solely on jedis to avoid conflicts due to entity changes between versions
 */
public class JedisConsistencyCheck {

    private static final Logger log = LoggerFactory.getLogger(JedisConsistencyCheck.class);

    private final JedisPool jedisPool;

    @Inject
    public JedisConsistencyCheck(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public boolean run() {
        return checkDuplicateIps();
    }

    private boolean checkDuplicateIps() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, List<String>> devicesByIpAddresses = getDevicesByIpAddresses(jedis);
            return !fixDuplicateIps(jedis, devicesByIpAddresses);
        }
    }

    private Map<String, List<String>> getDevicesByIpAddresses(Jedis jedis) {
        Map<String, List<String>> devicesByIpAddresses = new HashMap<>();
        Set<String> keys = jedis.keys("device:*");
        for (String key : keys) {
            String ipAddress = jedis.hget(key, "ipAddress");
            if (ipAddress != null) {
                List<String> devices = devicesByIpAddresses.get(ipAddress);
                if (devices == null) {
                    devices = new ArrayList<>();
                    devicesByIpAddresses.put(ipAddress, devices);
                }
                devices.add(key);
            }
        }
        return devicesByIpAddresses;
    }

    private boolean fixDuplicateIps(Jedis jedis, Map<String, List<String>> devicesByIpAddresses) {
        boolean duplicates = false;
        for (Map.Entry<String, List<String>> e : devicesByIpAddresses.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicates = true;
                log.error("ip address {} is assigned to multiple devices:", e.getKey());
                for (String key : e.getValue()) {
                    log.error("    {}", key);
                    jedis.hdel(key, "ipAddress");
                }
            }
        }
        return duplicates;
    }
}
