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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;

public class JedisSubscriberWrapper extends JedisPubSub {
    private final static Logger logger = LoggerFactory.getLogger(JedisSubscriberWrapper.class);
    final static String UNSUBSCRIBE_REQUEST = "UNSUBSCRIBE-" + Math.random();

    private final Subscriber subscriber;
    private final FatalErrorHandler fatalErrorHandler;

    public JedisSubscriberWrapper(Subscriber subscriber) {
        this(subscriber, FatalErrorHandler.noOp());
    }

    public JedisSubscriberWrapper(Subscriber subscriber, FatalErrorHandler fatalErrorHandler) {
        this.subscriber = subscriber;
        this.fatalErrorHandler = fatalErrorHandler != null ? fatalErrorHandler : FatalErrorHandler.noOp();
    }

    @Override
    public void onMessage(String channel, String message) {
        if (message == null) {
            logger.warn("Ignoring null pubsub message on channel {}", channel);
            return;
        }
        if (UNSUBSCRIBE_REQUEST.equals(message)) {
            unsubscribe();
            return;
        }

        try {
            subscriber.process(message);
        } catch (Error e) {
            logger.error("Fatal error in pubsub subscriber on channel {}:", channel, e);
            fatalErrorHandler.onFatalError("Redis pubsub message on channel " + channel, e);
            throw e;
        } catch (Exception e) {
            logger.error("Caught unchecked exception in channel {}:", channel, e);
        }
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        subscriber.onSubscribe();
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        subscriber.onUnsubscribe();
    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
    }

}
