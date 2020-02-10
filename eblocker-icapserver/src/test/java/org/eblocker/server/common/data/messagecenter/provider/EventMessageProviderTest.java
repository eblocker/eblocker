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
package org.eblocker.server.common.data.messagecenter.provider;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventDataSource;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.http.service.EventService;

public class EventMessageProviderTest {
    @Mock
    private EventDataSource dataSource;
    private Map<Integer, MessageContainer> containers;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        containers = new HashMap<Integer, MessageContainer>();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testLifecycle() throws InterruptedException {
        EventMessageProvider provider = new EventMessageProvider(new EventService(dataSource));

        // no message is created when no events occurred:
        Mockito.when(dataSource.getEvents()).thenReturn(Collections.emptyList());
        provider.update(containers);
        assertEquals(0, containers.size());
        
        // now add some events (a few milliseconds apart):
        Event event1 = Events.networkInterfaceDown();    Thread.sleep(3);
        Event event2 = Events.networkInterfaceUp();      Thread.sleep(3);
        List<Event> events = Arrays.asList(event2, event1);
        Mockito.when(dataSource.getEvents()).thenReturn(events);
        provider.doUpdate(containers);
        assertEquals(1, containers.size());
        MessageCenterMessage message = containers.get(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId()).getMessage();
        assertFalse(message.isShowDoNotShowAgain());
        
        // the user clicks the message's action button
        boolean hide = provider.executeAction(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId());
        assertTrue(hide);
        Mockito.verify(dataSource).setLastEventSeen(event2);
        Mockito.when(dataSource.getLastEventSeen()).thenReturn(event2);

        // now the message disappears:
        provider.update(containers);
        assertEquals(0, containers.size());
        
        // another event comes in:
        Event event3 = Events.powerFailure();
        events = Arrays.asList(event3, event2, event1);
        Mockito.when(dataSource.getEvents()).thenReturn(events);

        // the message reappears
        provider.doUpdate(containers);
        assertEquals(1, containers.size());
    }
}
