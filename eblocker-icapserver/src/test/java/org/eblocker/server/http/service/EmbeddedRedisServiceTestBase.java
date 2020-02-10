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
package org.eblocker.server.http.service;

import org.eblocker.server.common.TestRedisServer;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.JedisDataSource;
import org.eblocker.server.common.data.messagecenter.provider.AppModuleRemovalMessageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

public class EmbeddedRedisServiceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedRedisServiceTestBase.class);

    private JedisPool jedisPool;
    protected ObjectMapper objectMapper;
    protected DataSource dataSource;
    protected TestRedisServer redisServer;
    protected AppModuleRemovalMessageProvider appModuleRemovalMessageProvider;

    @Before
    public void setup() {
        doSetup();
    }

    @After
    public void shutdown() {
        doShutdown();
    }

    protected void doSetup() {
        redisServer = new TestRedisServer();
        redisServer.start();

        jedisPool = redisServer.getPool();
        objectMapper = new ObjectMapper();
        dataSource = new JedisDataSource(jedisPool, objectMapper);
        appModuleRemovalMessageProvider = Mockito.mock(AppModuleRemovalMessageProvider.class);
    }

    protected void doShutdown() {
        redisServer.stop();
    }

    protected JedisPool getJedisPool() {
        return jedisPool;
    }
}
