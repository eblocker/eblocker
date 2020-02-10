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
package org.eblocker.server.common.pubsub;

import org.eblocker.server.http.service.EmbeddedRedisServiceTestBase;
import org.junit.Assert;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class JedisPubSubServiceTest extends EmbeddedRedisServiceTestBase {

    private static final String CHANNEL = "unit-test-channel";
    private static final int RETRY_DELAY = 100;

    private volatile boolean runPublisher;

    @Test(timeout = 5000)
    public void testSubscriberException() throws InterruptedException {
        JedisPubSubService service = new JedisPubSubService(getJedisPool(), RETRY_DELAY);

        // run publisher thread to publish messages for subscriber
        runPublisher = true;
        new Thread(() -> {
            try {
                while (runPublisher) {
                    service.publish(CHANNEL, "exception");
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Assert.fail("should not be interrupted!");
            }
        }).start();

        Thread subscriberThread = new Thread(()->service.subscribeAndLoop(CHANNEL, new Subscriber() {
            boolean exceptionThrown = false;
                    @Override
                    public void process(String message) {
                        if (!exceptionThrown) {
                            // this is the first received message, just throw an exception
                            exceptionThrown = true;
                            throw new IllegalStateException();
                        }
                        // second exception, we can quit now: unsubscribe and stop message publisher
                        service.unsubscribe(this);
                        runPublisher = false;
                    }
                }));
        subscriberThread.start();
        subscriberThread.join();

        // check all connections are still usable
        while (getJedisPool().getNumIdle() != 0) {
            Jedis jedis = getJedisPool().getResource();
            jedis.set("test" + jedis, "test");
        }
    }

    @Test(timeout = 5000)
    public void testJedisPubSubService() throws InterruptedException {

        JedisPubSubService service = new JedisPubSubService(getJedisPool(), RETRY_DELAY);
        service.publish(CHANNEL, "pre subscription - must not be received");

        // Subscribing to a channel blocks the starting thread, so we need to use a second
        // thread to actually publish a message and call unsubscribe. This thread may not
        // start before the subscription has been successful. To ensure this a semaphore is
        // used which is acquired right now, released on successful subscription and finally
        // acquired by the publisher thread.
        Semaphore subscriptionSemaphore = new Semaphore(1);
        subscriptionSemaphore.acquire();

        List<String> events = new ArrayList<>();
        Subscriber subscriber = new Subscriber() {
            @Override
            public void process(String message) {
                events.add(message);
            }

            @Override
            public void onSubscribe() {
                // signal the publisher thread the subscription is successful
                subscriptionSemaphore.release();
                events.add("onSubscribe");
            }

            @Override
            public void onUnsubscribe() {
                events.add("onUnsubscribe");
            }
        };

        // publisher thread
        new Thread(() -> {
            try {
                // wait until subscriber is actually subscribed
                subscriptionSemaphore.acquire();
                service.publish(CHANNEL, "message");
                service.unsubscribe(subscriber);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).start();

        service.subscribeAndLoop(CHANNEL, subscriber);
        service.publish(CHANNEL, "post subscription - must not be received");

        Assert.assertEquals(3, events.size());
        Assert.assertEquals("onSubscribe", events.get(0));
        Assert.assertEquals("message", events.get(1));
        Assert.assertEquals("onUnsubscribe", events.get(2));
    }

    @Test//(timeout = 5000)
    public void testReconnectAfterLostConnection() throws InterruptedException {
        JedisPool jedisPool = getJedisPool();
        JedisPubSubService service = new JedisPubSubService(jedisPool, RETRY_DELAY);

        // Sempahore with 4 permits for two subscribe and two message events
        Semaphore subscriptionSemaphore = new Semaphore(4);
        subscriptionSemaphore.acquire(4);

        List<String> messages = new ArrayList<>();
        Subscriber subscriber = new Subscriber() {
            @Override
            public void process(String message) {
                messages.add(message);
                subscriptionSemaphore.release();
            }

            @Override
            public void onSubscribe() {
                subscriptionSemaphore.release();
            }

            @Override
            public void onUnsubscribe() {
            }
        };

        Thread subscriberThread = new Thread(() -> service.subscribeAndLoop(CHANNEL, subscriber));
        subscriberThread.start();

        subscriptionSemaphore.acquire(); // release in onSubscribe
        service.publish(CHANNEL, "beforeConnectionLost");
        subscriptionSemaphore.acquire(); // release in message received
        redisServer.stop();

        redisServer.start();
        subscriptionSemaphore.acquire(); // release in onSubscribe
        service.publish(CHANNEL, "afterConnectionLost");
        subscriptionSemaphore.acquire(); // release in message received

        service.unsubscribe(subscriber);
        subscriberThread.join();

        Assert.assertEquals(2, messages.size());
        Assert.assertEquals("beforeConnectionLost", messages.get(0));
        Assert.assertEquals("afterConnectionLost", messages.get(1));
    }
}
