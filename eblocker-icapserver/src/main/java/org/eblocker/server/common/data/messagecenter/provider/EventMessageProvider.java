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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.http.service.EventService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class EventMessageProvider extends AbstractMessageProvider {

    public static final String MESSAGE_ALERT_EVENT_TITLE = "MESSAGE_ALERT_EVENT_TITLE";
    public static final String MESSAGE_ALERT_EVENT_CONTENT = "MESSAGE_ALERT_EVENT_CONTENT";
    public static final String MESSAGE_ALERT_EVENT_LABEL = "MESSAGE_ALERT_EVENT_LABEL";
    public static final String MESSAGE_ALERT_EVENT_URL = "MESSAGE_ALERT_EVENT_URL";

    private final EventService eventService;

    @Inject
    public EventMessageProvider(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        List<Event> events;
        Event lastSeen = eventService.getLastEventSeen();
        if (lastSeen == null) { // get all events
            events = eventService.getEvents();
        } else {
            events = eventService.getEventsSince(lastSeen);
        }
        if (events.isEmpty()) {
            messageContainers.remove(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId());
        } else {
            MessageContainer message = createMessage(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId(),
                MESSAGE_ALERT_EVENT_TITLE,
                MESSAGE_ALERT_EVENT_CONTENT,
                MESSAGE_ALERT_EVENT_LABEL,
                MESSAGE_ALERT_EVENT_URL,
                Collections.emptyMap(),
                false,
                MessageSeverity.INFO
            );
            messageContainers.put(MessageProviderMessageId.MESSAGE_ALERT_EVENT_ID.getId(), message);
        }
    }

    @Override
    public boolean executeAction(int messageId) {
        List<Event> events = eventService.getEvents();
        if (!events.isEmpty()) {
            eventService.setLastEventSeen(events.get(0));
        }
        return true;
    }
}
