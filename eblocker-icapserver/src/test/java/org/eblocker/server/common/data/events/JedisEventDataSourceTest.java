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

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eblocker.server.common.EmbeddedRedisTestBase;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JedisEventDataSourceTest extends EmbeddedRedisTestBase {
    public static final int MAXIMUM_NUMBER_OF_EVENTS = 100;

    private EventDataSource dataSource;

    @Override
    public void doSetup() {
        super.doSetup();
        objectMapper.registerModule(new JavaTimeModule());
        dataSource = new JedisEventDataSource(jedisPool, objectMapper, MAXIMUM_NUMBER_OF_EVENTS);
    }

    @Test
    public void orderOfEvents() {
        dataSource.addEvent(Events.networkInterfaceDown());
        dataSource.addEvent(Events.powerFailure());

        List<Event> events = dataSource.getEvents();
        assertEquals(2, events.size());
        assertEquals(EventType.POWER_FAILURE, events.get(0).getType());
        assertEquals(EventType.NETWORK_INTERFACE_DOWN, events.get(1).getType());
    }

    @Test
    public void maximumNumberOfEvents() {
        int numberOfEvents = MAXIMUM_NUMBER_OF_EVENTS + 10;
        for (int i = 0; i < numberOfEvents; i++) {
            dataSource.addEvent(Events.networkInterfaceDown());
        }

        List<Event> events = dataSource.getEvents();
        assertEquals(MAXIMUM_NUMBER_OF_EVENTS, events.size());
    }

    @Test
    public void lastEventSeen() {
        assertNull(dataSource.getLastEventSeen());
        Event event = Events.powerFailure();
        dataSource.setLastEventSeen(event);
        assertEquals(event, dataSource.getLastEventSeen());
    }
}
