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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An event is something that occurred and that might be of interest to
 * the user
 */
public class Event {
    private EventType type;
    private Instant timestamp;
    private Map<String, String> eventDetails = null;

    public Event() {
        // Needed for convertng from json to an object
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant now) {
        this.timestamp = now;
    }

    public Map<String, String> getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(Map<String, String> details) {
        this.eventDetails = details;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Event) {
            Event other = (Event) obj;
            return Objects.equals(timestamp, other.timestamp) &&
                Objects.equals(type, other.type) &&
                Objects.equals(eventDetails, other.eventDetails);
        } else {
            return false;
        }
    }
}
