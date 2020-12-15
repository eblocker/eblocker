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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class NetworkInterfaceWatchdog implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(NetworkInterfaceWatchdog.class);

    private NetworkInterfaceWrapper networkInterface;

    enum NetworkState {
        INITIAL, DOWN, UP
    }

    private NetworkState networkState;
    private DataSource dataSource;
    private EventLogger eventLogger;

    @Inject
    public NetworkInterfaceWatchdog(NetworkInterfaceWrapper networkInterface, DataSource dataSource, EventLogger eventLogger) {
        this.networkInterface = networkInterface;
        this.networkState = NetworkState.UP;
        this.dataSource = dataSource;
        this.eventLogger = eventLogger;
    }

    @SubSystemInit
    public void init() {
        checkCleanShutdown();
    }

    private void checkCleanShutdown() {
        if (dataSource.getCleanShutdownFlag() == false) {
            log.warn("The last shutdown was not clean!");
            eventLogger.log(Events.powerFailure());
        }

        dataSource.setCleanShutdownFlag(false);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                dataSource.setCleanShutdownFlag(true);
            }
        });
    }

    @Override
    public void run() {
        NetworkState newState = networkInterface.isUp() ? NetworkState.UP : NetworkState.DOWN;

        if (newState != networkState) {
            if (networkState != NetworkState.INITIAL) {
                log.warn("Network state changed: {} -> {}", networkState, newState);

                if (newState == NetworkState.DOWN) {
                    eventLogger.log(Events.networkInterfaceDown());
                } else {
                    eventLogger.log(Events.networkInterfaceUp());
                }
            }
        }
        networkState = newState;
    }

}
