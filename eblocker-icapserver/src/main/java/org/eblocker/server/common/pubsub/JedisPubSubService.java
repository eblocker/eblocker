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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JedisPubSubService implements PubSubService {
    private final Logger logger = LoggerFactory.getLogger(JedisPubSubService.class);

    private final JedisPool pool;
    private final int retryDelay;

    private ConcurrentMap<Subscriber, String> channelBySubscriber = new ConcurrentHashMap<>(32, .75f, 2);

    @Inject
    public JedisPubSubService(JedisPool pool, @Named("jedis.pubsub.retry.delay") int retryDelay) {
        this.pool = pool;
        this.retryDelay = retryDelay;
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
                jedis.subscribe(new JedisSubscriberWrapper(subscriber), channel);
            } catch (JedisConnectionException e) {
                logger.warn("connection failed, attempting to reconnect in {}ms", retryDelay, e);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    logger.warn("interrupted while sleeping", ie);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void unsubscribe(Subscriber subscriber) {
        // unsubscribe must happen from the same thread as the subscribe so there's no other way than using
        // a marker message for shutdown
        publish(channelBySubscriber.remove(subscriber), JedisSubscriberWrapper.UNSUBSCRIBE_REQUEST);
    }
}
