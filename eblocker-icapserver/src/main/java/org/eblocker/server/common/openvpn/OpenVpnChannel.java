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
package org.eblocker.server.common.openvpn;

import org.eblocker.server.common.executor.NamedRunnable;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * This class will be listening via PubSub to the Redis channel "vpn_profile_status:$ID:in" and expect messages in the format:
 * [STATUS: up/down/error...]
 */
public class OpenVpnChannel {
    private static final Logger log = LoggerFactory.getLogger(OpenVpnChannel.class);

    private final Executor executor;
    private final PubSubService pubSubService;

    private int id;
    private OpenVpnChannelListener listener;

    private Subscriber subscriber;
    private Semaphore semaphore = new Semaphore(1);

    @Inject
    public OpenVpnChannel(@Named("unlimitedCachePoolExecutor") Executor executor, PubSubService pubSubService, @Assisted int id, @Assisted OpenVpnChannelListener listener) {
        this.executor = executor;
        this.pubSubService = pubSubService;
        this.id = id;
        this.listener = listener;
    }

    public void start() {
        try {
            subscriber = new OpenVpnChannelSubscriber();
            semaphore.acquire();
            executor.execute(new NamedRunnable("openvpn-" + id + "-channel", () -> {
                String channel = String.format(Channels.VPN_PROFILE_STATUS_IN, id);
                log.debug("Subscribing to Redis channel {}", channel);
                pubSubService.subscribeAndLoop(channel, subscriber);
            }));

            // we can only acquire the semaphore again if the onSubscribe-Event has released it before
            semaphore.acquire();
            semaphore.release();
        } catch (InterruptedException e) {
            log.error("unexpectedly interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        try {
            semaphore.acquire();
            pubSubService.unsubscribe(subscriber);
            // we can only acquire the semaphore again if the unSunscribe-Event has released it before
            semaphore.acquire();
            semaphore.release();
        } catch (InterruptedException e) {
            log.error("unexpectedly interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private class OpenVpnChannelSubscriber implements Subscriber {
        @Override
        public void process(String message) {
            log.debug("received message: {}", message);

            if (message == null) {
                log.error("null message?! can't do anything with it");
                return;
            }

            //Message Format: [VPNProfileID] [Status string in {up,down,error}]
            //separated with a whitespace
            String[] parts = message.split(" ");

            String status = parts[0];
            switch (status) {
                case "down":
                    //get the reason why its going down
                    String reason = parts.length == 2 ? parts[1] : "unknown";
                    log.info("VPN client instance with ID: {} just went down because: {}.", id, reason);
                    listener.down(reason);
                    break;
                case "up":
                    String virtualInterfaceName = parts[1];
                    String routeNetGateway = parts[2];
                    String routeVpnGateway = parts[3];
                    String trustedIp = parts[4];
                    List<String> nameServers = parts.length == 6  ? Arrays.asList(parts[5].split(",")) : Collections.emptyList();
                    log.info("VPN client instance with ID: {} using {} just came (back) up.", id, virtualInterfaceName);
                    listener.up(virtualInterfaceName, routeNetGateway, routeVpnGateway, trustedIp, nameServers);
                    break;
                case "error":
                    log.error("VPN client instance with ID: {} just broadcasted an error...NO REACTION IMPLEMENTED!", id);
                    break;
                case "pid":
                    int pid = Integer.parseInt(parts[1]);
                    log.info("VPN client instance with ID: {} reports pid {}", id, pid);
                    listener.reportPid(pid);
                    break;
                case "shutdown":
                    log.info("subscriber shutting down");

                    break;
                default:
                    log.error("unknown status message: {}", status);
                    break;
            }
        }

        @Override
        public synchronized void onSubscribe() {
            // release previously from starting thread acquired semaphore to signal this
            // subscriber is actually ready
            log.debug("onSubscribe for channel {}", id);
            semaphore.release();
        }

        @Override
        public synchronized void onUnsubscribe() {
            // release previously from stopping thread acquired semaphore to signal this
            // subscriber is actually unsubscribed
            log.debug("onUnsubscribe for channel {}", id);
            semaphore.release();
        }
    }
}
