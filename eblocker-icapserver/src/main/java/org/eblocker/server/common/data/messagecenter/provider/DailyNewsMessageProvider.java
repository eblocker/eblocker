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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class DailyNewsMessageProvider extends AbstractMessageProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DailyNewsMessageProvider.class);

    private final String dailyNewsFilePath;

    private final ObjectMapper objectMapper;

    private FileTime latestNewsNotes;

    @Inject
    public DailyNewsMessageProvider(@Named("message.dailyNews.file") String dailyNewsFilePath, ObjectMapper objectMapper) {
        this.dailyNewsFilePath = dailyNewsFilePath;
        this.objectMapper = objectMapper;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_DAILY_NEWS_ID.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {

        MessageContainer current = loadMessage();
        if (current == null) {
            // No new message, nothing to do.
            // Leave current release message in the map!
            return;
        }

        boolean newRelease = true;
        MessageContainer previous = messageContainers.get(MessageProviderMessageId.MESSAGE_DAILY_NEWS_ID.getId());
        if (previous != null) {
            newRelease = previous.getMessage().getDate() != current.getMessage().getDate();
        }
        if (newRelease) {
            messageContainers.put(MessageProviderMessageId.MESSAGE_DAILY_NEWS_ID.getId(), current);
        }

    }

    private MessageContainer loadMessage() {
        Path releaseNotesPath = Paths.get(dailyNewsFilePath);
        if (Files.exists(releaseNotesPath) && Files.isReadable(releaseNotesPath)) {

            try {
                FileTime currentReleaseNotes = Files.getLastModifiedTime(releaseNotesPath);
                if (currentReleaseNotes.equals(latestNewsNotes)) {
                    // Already seen
                    return null;
                }
                latestNewsNotes = currentReleaseNotes;
                MessageCenterMessage message = objectMapper.readValue(Files.newBufferedReader(releaseNotesPath), MessageCenterMessage.class);
                if (message != null) {
                    return new MessageContainer(message);
                }

            } catch (IOException e) {
                LOG.info("Cannot read release notes from file {}.", dailyNewsFilePath, e);
            }

        }
        return null;
    }

}
