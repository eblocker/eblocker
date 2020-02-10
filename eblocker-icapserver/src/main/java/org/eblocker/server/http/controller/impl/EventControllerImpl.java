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
package org.eblocker.server.http.controller.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import org.eblocker.server.common.data.events.Event;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

import org.eblocker.server.http.controller.EventController;
import org.eblocker.server.http.service.EventService;
import com.google.inject.Inject;

/**
 * Allows loading of events for display
 */
public class EventControllerImpl implements EventController {
    
    private static final long DAYS_IN_WEEK = 7;
    private final EventService service;

    public enum EventDeletionMode {
        ALL, WEEK, UNKNOWN;
        public static EventDeletionMode getMode(String modeString) {
            for (EventDeletionMode mode : EventDeletionMode.values()) {
                if (mode.toString().equals(modeString)) {
                    return mode;
                }
            }
            return UNKNOWN;
        }
    }
    
    @Inject
    public EventControllerImpl(EventService service) {
        this.service = service;
    }

    /**
     * Returns a list of all events
     */
    @Override
    public List<Event> getEvents(Request request, Response response) {
        return service.getEvents();
    }
    
    /**
     * Deleted events according to a condition
     */
    @Override
    public void deleteSeveralEvents(Request request, Response response) {
        Predicate<Event> deletionPredicate;
        EventDeletionMode mode = EventDeletionMode.getMode(request.getHeader("mode"));
        switch (mode) {
        case ALL:
            service.deleteAllEvents();
            return;
        case WEEK:
            Instant now = new Date().toInstant();
            deletionPredicate = event -> Duration.between(event.getTimestamp(), now).toDays() > DAYS_IN_WEEK;
            break;
        default:
            throw new BadRequestException("unsupported mode " + request.getHeader("mode"));
        }
        service.delete(deletionPredicate);
    }
}
