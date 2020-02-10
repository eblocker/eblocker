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

import java.util.List;

/**
 * Stores events in a stack. New events are pushed onto the stack. The size of the stack
 * is limited to N number of events. Events are returned in reverse order, i.e. latest
 * events are at the top of the list.
 */
public interface EventDataSource {
    /**
     * Adds an event on top of the stack
     * @param event
     */
    public void addEvent(Event event);
    
    /**
     * Returns all events. The events are sorted in chronologically reversed order.
     * @return
     */
    public List<Event> getEvents();

    /**
     * Saves an event that was last seen by the user
     * @param event
     */
    public void setLastEventSeen(Event event);

    /**
     * Retrieves an event that was last seen by the user
     * @return
     */
    public Event getLastEventSeen();
    
    /**
     * Deletes all events
     */
    void deleteAllEvents();

    void trimEventsAfter(long numberDesiredElements);
}
