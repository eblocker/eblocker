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
package org.eblocker.server.common.service;

import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestPubSubService implements PubSubService {
    private static final Logger LOG = LoggerFactory.getLogger(TestPubSubService.class);

    private final Map<String, List<Subscriber>> channels = new HashMap<>();

    @Override
    public synchronized void publish(String channel, String message) {
        LOG.info("Publishing {} to channel {}", message, channel);
        if (channels.containsKey(channel)) {
            for (Subscriber subscriber : channels.get(channel)) {
                LOG.info("Subscriber processes {} in channel {}", message, channel);
                subscriber.process(message);
            }
        }
    }

    @Override
    public synchronized void subscribeAndLoop(String channel, Subscriber subscriber) {
        LOG.info("Subscribing to channel {}", channel);
        if (!channels.containsKey(channel)) {
            channels.put(channel, new ArrayList<>());
        }
        channels.get(channel).add(subscriber);
        subscriber.onSubscribe();

        // block until unsubscribed
        while (channels.get(channel).contains(subscriber)) {
            try {
                wait();
            } catch (InterruptedException e) {
                LOG.warn("interrupted, should not happen.", e);
            }
        }
    }

    @Override
    public synchronized void unsubscribe(Subscriber subscriber) {
        channels.entrySet().stream().filter(e -> e.getValue().contains(subscriber)).forEach(e -> {
            LOG.info("Unsubscribing from channel {}", e.getKey());
            e.getValue().remove(subscriber);
        });
        subscriber.onUnsubscribe();
        notifyAll();
    }

}
