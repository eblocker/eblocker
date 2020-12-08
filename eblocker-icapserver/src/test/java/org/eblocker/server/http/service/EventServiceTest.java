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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventDataSource;
import org.eblocker.server.common.data.events.Events;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EventServiceTest {
    private EventService service;

    @Mock
    private EventDataSource dataSource;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        service = new EventService(dataSource);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getEventsSince() throws InterruptedException {
        // create some events, a few milliseconds apart:
        Event e1 = Events.networkInterfaceDown();
        Thread.sleep(3);
        Event e2 = Events.networkInterfaceUp();
        Thread.sleep(3);
        Event e3 = Events.powerFailure();
        Thread.sleep(3);
        Event e4 = Events.networkInterfaceDown();

        Mockito.when(dataSource.getEvents()).thenReturn(Arrays.asList(e4, e3, e2, e1));

        List<Event> events = service.getEventsSince(e2);
        assertEquals(2, events.size());
        assertEquals(e4, events.get(0));
        assertEquals(e3, events.get(1));
    }

}
