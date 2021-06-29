package org.eblocker.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import redis.clients.jedis.JedisPool;

public class EmbeddedRedisTestBase {
    protected ObjectMapper objectMapper;
    protected TestRedisServer redisServer;
    protected JedisPool jedisPool;

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
    }

    protected void doShutdown() {
        redisServer.stop();
    }
}
