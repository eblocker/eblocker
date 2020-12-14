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
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Set;

public class SchemaMigrationVersion8 implements SchemaMigration {

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    @Inject
    public SchemaMigrationVersion8(DataSource dataSource, JedisPool jedisPool) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
    }

    @Override
    public String getSourceVersion() {
        return "7";
    }

    @Override
    public String getTargetVersion() {
        return "8";
    }

    public void migrate() {
        deleteAllParentalControlFilterSummaries();
        initParentalControlFilterMetaDataSequence();
        dataSource.setVersion("8");
    }

    private void deleteAllParentalControlFilterSummaries() {
        Jedis jedis = jedisPool.getResource();
        Set<String> keys = jedisPool.getResource().keys(DefaultEntities.PARENTAL_CONTROL_FILTER_SUMMARY_KEY);
        keys.forEach(jedis::del);
    }

    private void initParentalControlFilterMetaDataSequence() {
        dataSource.setIdSequence(ParentalControlFilterMetaData.class,
                DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_FILTER_METADATA);
    }
}
