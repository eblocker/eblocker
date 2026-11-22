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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.FatalErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JedisPubSubService implements PubSubService {
    private final Logger logger = LoggerFactory.getLogger(JedisPubSubService.class);

    private final JedisPool pool;
    private final int retryDelay;
    private final FatalErrorHandler fatalErrorHandler;

    private ConcurrentMap<Subscriber, String> channelBySubscriber = new ConcurrentHashMap<>(32, .75f, 2);

    public JedisPubSubService(JedisPool pool, int retryDelay) {
        this(pool, retryDelay, FatalErrorHandler.noOp());
    }

    @Inject
    public JedisPubSubService(JedisPool pool,
                              @Named("jedis.pubsub.retry.delay") int retryDelay,
                              FatalErrorHandler fatalErrorHandler) {
        this.pool = pool;
        this.retryDelay = retryDelay;
        this.fatalErrorHandler = fatalErrorHandler != null ? fatalErrorHandler : FatalErrorHandler.noOp();
    }

    @Override
    public void publish(String channel, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        }
    }

    @Override
    public void subscribeAndLoop(String channel, Subscriber subscriber) {
        channelBySubscriber.put(subscriber, channel);
        while (channelBySubscriber.containsKey(subscriber)) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisSubscriberWrapper(subscriber, fatalErrorHandler), channel);
            } catch (Error e) {
                logger.error("Fatal error in pubsub loop for channel {}", channel, e);
                channelBySubscriber.remove(subscriber);
                fatalErrorHandler.onFatalError("Redis pubsub loop on channel " + channel, e);
                throw e;
            } catch (RuntimeException e) {
                logger.warn("Pubsub loop on channel {} failed, attempting to reconnect in {}ms", channel, retryDelay, e);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    logger.warn("interrupted while sleeping", ie);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void unsubscribe(Subscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        // unsubscribe must happen from the same thread as the subscribe so there's no other way than using
        // a marker message for shutdown
        String channel = channelBySubscriber.remove(subscriber);
        if (channel != null) {
            publish(channel, JedisSubscriberWrapper.UNSUBSCRIBE_REQUEST);
        }
    }
}
