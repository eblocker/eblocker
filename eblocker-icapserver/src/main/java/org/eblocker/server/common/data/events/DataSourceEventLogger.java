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
package org.eblocker.server.common.data.events;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Logs events. This class should be threadsafe.
 * <p>
 * Events are appended to a queue and saved to Redis in a
 * background thread. This de-couples adding events from
 * I/O operations.
 */
public class DataSourceEventLogger implements EventLogger {
    private static final Logger log = LoggerFactory.getLogger(DataSourceEventLogger.class);

    private BlockingQueue<Event> queue;

    @Inject
    public DataSourceEventLogger(EventDataSource dataSource, @Named("unlimitedCachePoolExecutor") Executor executor) {
        queue = new LinkedBlockingQueue<>();
        executor.execute(() -> {
            for (; ; ) {
                try {
                    Event event = queue.take(); // blocks until the next event becomes available
                    dataSource.addEvent(event);
                } catch (InterruptedException e) {
                    log.info("Interrupted while waiting for next event", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    @Override
    public void log(Event event) {
        boolean appended = queue.offer(event);
        if (!appended) {
            log.error("Could not append event to event log");
        }
    }

}
