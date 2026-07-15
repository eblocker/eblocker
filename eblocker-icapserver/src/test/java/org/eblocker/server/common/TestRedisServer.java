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
package org.eblocker.server.common;

import com.github.fppt.jedismock.RedisServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides a local Redis server listening on a random port
 * between 16000 and 18000 for unit tests.
 *
 * The port is logged.
 *
 * Debugging: To look into Redis, set a breakpoint that suspends only the thread that hit the breakpoint
 * (not all threads).
 */
public class TestRedisServer {
    private static final Logger LOG = LoggerFactory.getLogger(TestRedisServer.class);
    private int port;
    private RedisServer server;

    public TestRedisServer() {
        port = ThreadLocalRandom.current().nextInt(16000, 18000);
        try {
            server = RedisServer.newRedisServer(port);
        } catch (Exception e) {
            throw new RuntimeException("Could not start embedded Redis server on port " + port, e);
        }
    }

    public JedisPool getPool() {
        return new JedisPool("localhost", port);
    }

    public void start() {
        try {
            LOG.info("Starting test Redis server on port {}", port);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            LOG.info("Stopping test Redis server on port {}", port);
            server.stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
