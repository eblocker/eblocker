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
    private TestExecutor executor;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(EventDataSource.class);
        executor = new TestExecutor();
        logger = new DataSourceEventLogger(dataSource, executor);
    }

    @After
    public void tearDown() {
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
            for (int i = 0; i < 1000; i++) {
                logger.log(Events.powerFailure());
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                logger.log(Events.networkInterfaceDown());
            }
        });

        a.start();
        b.start();

        a.join();
        b.join();

        Thread.sleep(100); // wait for background thread to add events to the datasource

        // Verify that the datasource received 1000 events of each type:
        Stream
                .of(EventType.POWER_FAILURE,
                        EventType.NETWORK_INTERFACE_DOWN)
                .forEach((type) -> {
                    Mockito.verify(dataSource, Mockito.times(1000)).addEvent(Mockito.argThat(event -> event.getType() == type));
                });
    }

    @Test
    public void testInterruption() throws InterruptedException {
        executor.interrupt();
        Thread.sleep(100);
        Assert.assertFalse(executor.isRunning());
    }

    private class TestExecutor implements Executor {
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
