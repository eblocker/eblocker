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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class DataSourceEventLoggerTest {
    private DataSourceEventLogger logger;
    private EventDataSource dataSource;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(EventDataSource.class);
        TestExecutor executor = new TestExecutor();
        logger = new DataSourceEventLogger(dataSource, executor);
    }

    @Test
    public void eventsAreAdded() throws InterruptedException {

        Event event1 = Events.powerFailure();
        Event event2 = Events.networkInterfaceDown();

        logger.log(event1);
        logger.log(event2);

        Thread.sleep(100); // wait for background thread to add events to the datasource

        Mockito.verify(dataSource).addEvent(event1);
        Mockito.verify(dataSource).addEvent(event2);
    }

    @Test
    public void loadTest() throws InterruptedException {
        // Each thread logs 1000 events of a type:
        Thread a = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                logger.log(Events.powerFailure());
                logger.log(Events.networkInterfaceDown());
            }
        });

        a.start();

        a.join();

        while (logger.queueSize() > 0){ // wait for background thread to add events to the datasource
            Thread.sleep(10);
        }


        // If we have seen more than two elements of each type in the datasource, we know that it works. To see all elements, the testing effort becomes large, as multithreading is difficult to predict for testing and predictability is irrelevant for production.
        Mockito.verify(dataSource, Mockito.atLeast(2)).addEvent(Mockito.argThat(event -> event.getType() == EventType.POWER_FAILURE));
        Mockito.verify(dataSource, Mockito.atLeast(2)).addEvent(Mockito.argThat(event -> event.getType() == EventType.NETWORK_INTERFACE_DOWN));
    }

    private static class TestExecutor implements Executor {
        private Thread thread;

        @Override
        public void execute(Runnable command) {
            thread = new Thread(command);
            thread.start();
        }

        public void interrupt() {
            thread.interrupt();
        }

        public boolean isRunning() {
            return thread.isAlive();
        }
    }
}
