/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class FilterListsOutdatedMessageProvider extends AbstractMessageProvider {
    private final Clock clock;
    private final int outdatedDays;
    private final DataSource dataSource;
    private final DateFormat versionDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private final Integer MESSAGE_ID = MessageProviderMessageId.MESSAGE_FILTER_LISTS_OUTDATED_ID.getId();

    @Inject
    public FilterListsOutdatedMessageProvider(@Named("message.filterlists.outdated.days") int outdatedDays,
                                              DataSource dataSource,
                                              Clock clock) {
        this.outdatedDays = outdatedDays;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Set.of(MESSAGE_ID);
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        String listsVersion = dataSource.getListsPackageVersion();
        if (listsVersion == null) {
            messageContainers.remove(MESSAGE_ID);
            return;
        }
        try {
            Date listsLastUpdated = versionDateFormat.parse(listsVersion);
            Instant listsBecomeOutdated = listsLastUpdated.toInstant().plus(outdatedDays, ChronoUnit.DAYS);
            if (clock.instant().isAfter(listsBecomeOutdated)) {
                if (!messageContainers.containsKey(MESSAGE_ID)) {
                    messageContainers.put(MESSAGE_ID, createMessage());
                }
            } else {
                messageContainers.remove(MESSAGE_ID);
            }
        } catch (ParseException e) {
            messageContainers.remove(MESSAGE_ID);
        }
    }

    private MessageContainer createMessage() {
        return createMessage(MESSAGE_ID,
                "MESSAGE_FILTER_LISTS_OUTDATED_TITLE",
                "MESSAGE_FILTER_LISTS_OUTDATED_CONTENT",
                "MESSAGE_FILTER_LISTS_OUTDATED_LABEL",
                "MESSAGE_FILTER_LISTS_OUTDATED_URL",
                Map.of("outdatedDays", String.valueOf(outdatedDays)),
                false,
                MessageSeverity.ALERT);
    }
}
