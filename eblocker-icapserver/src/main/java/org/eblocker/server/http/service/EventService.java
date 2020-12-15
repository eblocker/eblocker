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

import com.google.inject.Inject;
import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventDataSource;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provides access to events
 */
public class EventService {

    private EventDataSource dataSource;

    @Inject
    public EventService(EventDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Event> getEvents() {
        return dataSource.getEvents();
    }

    public void setLastEventSeen(Event event) {
        dataSource.setLastEventSeen(event);
    }

    public Event getLastEventSeen() {
        return dataSource.getLastEventSeen();
    }

    public List<Event> getEventsSince(Event lastSeen) {
        Instant last = lastSeen.getTimestamp();
        List<Event> events = dataSource.getEvents();
        return events.stream()
                .filter(e -> e.getTimestamp().compareTo(last) > 0)
                .collect(Collectors.toList());
    }

    public void deleteAllEvents() {
        dataSource.deleteAllEvents();
    }

    /*
     * Delete those events that match the predicate. Notice the predicate is
     * negated and used to count how many events are to be preserved. Further it
     * is assumed the elements to remain are one continuous block at the start
     * of the list.
     */
    public void delete(Predicate<Event> predicate) {
        long numberDesiredElements = dataSource.getEvents().stream().filter(predicate.negate())
                .count();
        dataSource.trimEventsAfter(numberDesiredElements);
    }
}
