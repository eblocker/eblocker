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

import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.dns.DnsServerConfig;
import org.eblocker.server.common.data.dns.EblockerDnsServerState;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates EblockerDnsServerState based on current config
 */
public class SchemaMigrationVersion15 implements SchemaMigration {

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    @Inject
    public SchemaMigrationVersion15(
        DataSource dataSource,
        JedisPool jedisPool) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
    }

    @Override
    public String getSourceVersion() {
        return "14";
    }

    @Override
    public String getTargetVersion() {
        return "15";
    }

    @Override
    public void migrate() {
        DnsServerConfig config = dataSource.get(DnsServerConfig.class);
        if (config != null) {
            createEblockerServerState(config);
        }

        dataSource.setVersion("15");
    }

    private void createEblockerServerState(DnsServerConfig config) {
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(isEblockerDnsEnabled());
        state.setResolverByDeviceId(mapToResolverById(config.getResolverConfigNameByIp()));
        dataSource.save(state);
    }

    private boolean isEblockerDnsEnabled() {
        try (Jedis jedis = jedisPool.getResource()) {
            return Boolean.valueOf(jedis.get(DefaultEntities.DNS_ENABLED_KEY));
        }
    }

    private Map<String, String> mapToResolverById(Map<String, String> resolverByIp) {
        Map<String, String> deviceIdByIp = dataSource.getDevices().stream()
            .filter(d -> !d.getIpAddresses().isEmpty())
            .collect(Collectors.toMap(d -> d.getIpAddresses().get(0).toString(), Device::getId));

        return resolverByIp.entrySet().stream()
            .map(e -> new AbstractMap.SimpleEntry<>(deviceIdByIp.get(e.getKey()), e.getValue()))
            .filter(e -> e.getKey() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
