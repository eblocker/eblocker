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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.VersionInfo;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.http.service.VersionService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class ReleaseNotesMessageProvider extends AbstractMessageProvider {
    private static final String MESSAGE_RELEASE_NOTES_TITLE = "MESSAGE_RELEASE_NOTES_TITLE";
    private static final String MESSAGE_RELEASE_NOTES_CONTENT = "MESSAGE_RELEASE_NOTES_CONTENT";
    private static final String MESSAGE_RELEASE_NOTES_LABEL = "MESSAGE_RELEASE_NOTES_LABEL";
    private static final String MESSAGE_RELEASE_NOTES_URL = "MESSAGE_RELEASE_NOTES_URL";

    private static final String MESSAGE_PATCH_RELEASE_NOTES_TITLE = "MESSAGE_PATCH_RELEASE_NOTES_TITLE";
    private static final String MESSAGE_PATCH_RELEASE_NOTES_CONTENT = "MESSAGE_PATCH_RELEASE_NOTES_CONTENT";
    private static final String MESSAGE_PATCH_RELEASE_NOTES_LABEL = "MESSAGE_PATCH_RELEASE_NOTES_LABEL";
    private static final String MESSAGE_PATCH_RELEASE_NOTES_URL = "MESSAGE_PATCH_RELEASE_NOTES_URL";

    private final VersionService versionService;
    private EventLogger eventLogger;

    private final String currentVersion;
    private final String currentVersionMajorMinor;

    private static final Set<Integer> MESSAGE_IDS = new HashSet<>();

    static {
        MESSAGE_IDS.add(MessageProviderMessageId.MESSAGE_RELEASE_NOTES_ID.getId());
        MESSAGE_IDS.add(MessageProviderMessageId.MESSAGE_PATCH_RELEASE_NOTES_ID.getId());
    }

    @Inject
    public ReleaseNotesMessageProvider(
        VersionService versionService,
        EventLogger eventLogger,
        @Named("project.version") String currentVersion
    ) {
        this.versionService = versionService;
        this.eventLogger = eventLogger;
        this.currentVersion = currentVersion;
        String currentVersionMajor = "X";
        String currentVersionMinor = "Y";
        if (currentVersion != null) {
            String[] versionSegments = currentVersion.split("\\.");
            currentVersionMajor = versionSegments.length >= 1 ? versionSegments[0] : "X";
            currentVersionMinor = versionSegments.length >= 2 ? versionSegments[1] : "Y";
        }
        currentVersionMajorMinor = currentVersionMajor + "-" + currentVersionMinor;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return MESSAGE_IDS;
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        MessageContainer current = checkAndUpdateVersionInfo();
        if (current == null) {
            // No new message, nothing to do.
            // Leave current release message in the map!
            return;
        }
        // Remove potentially outdated messages
        if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_RELEASE_NOTES_ID.getId())) {
            messageContainers.remove(MessageProviderMessageId.MESSAGE_RELEASE_NOTES_ID.getId());
        }
        if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_PATCH_RELEASE_NOTES_ID.getId())) {
            messageContainers.remove(MessageProviderMessageId.MESSAGE_PATCH_RELEASE_NOTES_ID.getId());
        }
        // Add current message
        messageContainers.put(current.getMessage().getId(), current);
    }

    private MessageContainer checkAndUpdateVersionInfo() {

        // Compare
        VersionInfo versionInfo = versionService.get();
        if (versionInfo != null &&
            versionInfo.getVersionEBlockerOs() != null &&
            versionInfo.getVersionEBlockerOs().equals(currentVersion)) {
            // No update. No need for a release note message
            return null;
        }

        // Distinguish between patches and major/minor updates
        boolean isMajorOrMinorChange = versionInfo == null || isMajorOrMinorChange(versionInfo.getVersionEBlockerOs(), currentVersion);

        // Update version in DB
        versionService.set(new VersionInfo(currentVersion));

        // Log update event
        eventLogger.log(Events.updateEblockerOsInstalled());

        Map<String, String> context = new HashMap<>();
        context.put("version", currentVersion);
        context.put("versionMajorMinor", currentVersionMajorMinor);

        // Create new release note message
        if (isMajorOrMinorChange) {
            return createMessage(MessageProviderMessageId.MESSAGE_RELEASE_NOTES_ID.getId(),
                MESSAGE_RELEASE_NOTES_TITLE,
                MESSAGE_RELEASE_NOTES_CONTENT,
                MESSAGE_RELEASE_NOTES_LABEL,
                MESSAGE_RELEASE_NOTES_URL,
                context,
                true,
                MessageSeverity.INFO
            );
        } else {
            return createMessage(MessageProviderMessageId.MESSAGE_PATCH_RELEASE_NOTES_ID.getId(),
                MESSAGE_PATCH_RELEASE_NOTES_TITLE,
                MESSAGE_PATCH_RELEASE_NOTES_CONTENT,
                MESSAGE_PATCH_RELEASE_NOTES_LABEL,
                MESSAGE_PATCH_RELEASE_NOTES_URL,
                context,
                true,
                MessageSeverity.INFO
            );
        }
    }

    /*
     * Tells if two given version strings of format X.Y.Z indicate a major or minor
     * change, i.e. differ in their respective Xs or Ys. Not following the
     * format of at least X.Y results in a false.
     * @param String oldVersion one version string
     * @param String newVersion another version string
     * @return true if there is a major change with respect to versions
     */
    private boolean isMajorOrMinorChange(String oldVersion, String newVersion) {
        String[] oldVersionSegments = oldVersion.split("\\.");
        String[] newVersionSegments = newVersion.split("\\.");
        return (oldVersionSegments.length >= 2 && newVersionSegments.length >= 2
            && !(oldVersionSegments[0].equals(newVersionSegments[0])
            && oldVersionSegments[1].equals(newVersionSegments[1])));
    }

}
