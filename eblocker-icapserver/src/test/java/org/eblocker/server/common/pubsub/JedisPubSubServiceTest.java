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

import org.eblocker.server.common.system.FatalErrorHandler;
import org.eblocker.server.http.service.EmbeddedRedisServiceTestBase;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class JedisPubSubServiceTest extends EmbeddedRedisServiceTestBase {

    private static final String CHANNEL = "unit-test-channel";
    private static final int RETRY_DELAY = 100;

    private volatile boolean runPublisher;

    @Test(timeout = 5000)
    public void testSubscriberException() throws InterruptedException {
        JedisPubSubService service = new JedisPubSubService(jedisPool, RETRY_DELAY);

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

        Thread subscriberThread = new Thread(() -> service.subscribeAndLoop(CHANNEL, new Subscriber() {
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
        while (jedisPool.getNumIdle() != 0) {
            Jedis jedis = jedisPool.getResource();
            jedis.set("test" + jedis, "test");
        }
    }

    @Test(timeout = 5000)
    public void testJedisPubSubService() throws InterruptedException {

        JedisPubSubService service = new JedisPubSubService(jedisPool, RETRY_DELAY);
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

    @Test(timeout = 5000)
    public void testDisconnectsSubscribedJedisOnRuntimeExceptionBeforePoolReturn() {
        JedisPool mockPool = Mockito.mock(JedisPool.class);
        Jedis firstJedis = Mockito.mock(Jedis.class);
        Jedis secondJedis = Mockito.mock(Jedis.class);
        FatalErrorHandler fatalHandler = Mockito.mock(FatalErrorHandler.class);

        JedisPubSubService service = new JedisPubSubService(mockPool, 10, fatalHandler);
        Subscriber subscriber = Mockito.mock(Subscriber.class);

        Mockito.when(mockPool.getResource()).thenReturn(firstJedis, secondJedis);
        Mockito.doThrow(new IllegalStateException("unexpected runtime failure"))
                .when(firstJedis).subscribe(Mockito.any(JedisPubSub.class), Mockito.eq(CHANNEL));
        Mockito.doAnswer(invocation -> {
            service.unsubscribe(subscriber);
            return null;
        }).when(secondJedis).subscribe(Mockito.any(JedisPubSub.class), Mockito.eq(CHANNEL));

        service.subscribeAndLoop(CHANNEL, subscriber);

        InOrder firstOrder = Mockito.inOrder(firstJedis);
        firstOrder.verify(firstJedis).disconnect();
        firstOrder.verify(firstJedis).close();

        Mockito.verify(secondJedis, Mockito.never()).disconnect();
        Mockito.verify(secondJedis).close();
        Mockito.verifyNoInteractions(fatalHandler);
    }

    @Test(timeout = 5000)
    public void testDisconnectsSubscribedJedisAndEscalatesOnFatalError() {
        JedisPool mockPool = Mockito.mock(JedisPool.class);
        Jedis mockJedis = Mockito.mock(Jedis.class);
        AtomicReference<Throwable> recordedError = new AtomicReference<>();
        FatalErrorHandler fatalHandler = (source, error) -> recordedError.set(error);

        JedisPubSubService service = new JedisPubSubService(mockPool, 10, fatalHandler);
        Subscriber subscriber = Mockito.mock(Subscriber.class);

        Mockito.when(mockPool.getResource()).thenReturn(mockJedis);
        OutOfMemoryError oom = new OutOfMemoryError("simulated OOM");
        Mockito.doThrow(oom).when(mockJedis).subscribe(Mockito.any(JedisPubSub.class), Mockito.eq(CHANNEL));

        try {
            service.subscribeAndLoop(CHANNEL, subscriber);
            Assert.fail("Expected OutOfMemoryError to be rethrown");
        } catch (OutOfMemoryError expected) {
            Assert.assertSame(oom, expected);
        }

        Assert.assertSame(oom, recordedError.get());
        InOrder inOrder = Mockito.inOrder(mockJedis);
        inOrder.verify(mockJedis).disconnect();
        inOrder.verify(mockJedis).close();
    }
}
