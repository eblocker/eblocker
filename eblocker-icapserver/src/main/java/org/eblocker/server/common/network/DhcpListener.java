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
package org.eblocker.server.common.network;

import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;

public class DhcpListener {
    private static final Logger log = LoggerFactory.getLogger(DhcpListener.class);

    private final ConcurrentMap<String, Long> arpProbeCache;
    private final PubSubService pubSubService;

    @Inject
    public DhcpListener(@Named("arpProbeCache") ConcurrentMap<String, Long> arpProbeCache, PubSubService pubSubService) {
        this.arpProbeCache = arpProbeCache;
        this.pubSubService = pubSubService;
    }

    public void run() {
        pubSubService.subscribeAndLoop(Channels.DHCP_IN, message -> {
            String[] tokens = message.split("/");
            if (tokens.length != 2 || !"1".equals(tokens[0])) {
                log.warn("unexpected message: {}", message);
                return;
            }

            String hardwareAddress = tokens[1];
            String deviceId = "device:" + hardwareAddress;
            arpProbeCache.put(deviceId, System.currentTimeMillis());
            log.debug("detected dhcp discover / request for {}", deviceId);
        });
    }

}
