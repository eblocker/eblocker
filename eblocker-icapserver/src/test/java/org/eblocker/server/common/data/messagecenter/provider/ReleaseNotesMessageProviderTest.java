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

import org.eblocker.server.common.data.VersionInfo;
import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.http.service.VersionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class ReleaseNotesMessageProviderTest {

    private static final String OLD_VERSION = "1.2.3-FINAL";
    private static final String NEW_VERSION_MINOR_CHANGE = "1.2.4-FINAL";
    private static final String NEW_VERSION = "1.3.0-FINAL";
    private static final VersionInfo OLD_VERSION_INFO = new VersionInfo(OLD_VERSION);
    private static final VersionInfo NEW_VERSION_INFO = new VersionInfo(NEW_VERSION);

    private VersionService versionService;
    private EventLogger eventLogger;

    @Before
    public void setup() throws IOException {
        versionService = Mockito.mock(VersionService.class);
        eventLogger = Mockito.mock(EventLogger.class);
        when(versionService.get()).
            thenReturn(OLD_VERSION_INFO). // first call
            thenReturn(NEW_VERSION_INFO); // second call (if necessary)
    }

    @Test
    public void test_noUpdate() throws IOException, InterruptedException {
        ReleaseNotesMessageProvider releaseNotesMessageProvider = new ReleaseNotesMessageProvider(versionService, eventLogger, OLD_VERSION);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // No update available -> no release notes
        //
        releaseNotesMessageProvider.doUpdate(messageContainers);
        assertEquals(0, messageContainers.size());

        //
        // No update of DB version info
        //
        verify(versionService, times(0)).set(any());

        Mockito.verify(eventLogger, Mockito.never()).log(Mockito.any(Event.class));
    }

    @Test
    public void test_mayorUpdate() throws IOException, InterruptedException {
        ReleaseNotesMessageProvider releaseNotesMessageProvider = new ReleaseNotesMessageProvider(versionService, eventLogger, NEW_VERSION);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // Update available -> added release notes
        //
        releaseNotesMessageProvider.doUpdate(messageContainers);
        assertEquals(1, messageContainers.size());
        assertTrue(messageContainers.containsKey(MessageProviderMessageId.MESSAGE_RELEASE_NOTES_ID.getId()));

        //
        // Expecting update of DB version info
        //
        verify(versionService, times(1)).set(any());

        Mockito.verify(eventLogger).log(Mockito.any(Event.class));
    }

    @Test
    public void test_minorUpdate() throws IOException, InterruptedException {
        ReleaseNotesMessageProvider releaseNotesMessageProvider = new ReleaseNotesMessageProvider(versionService,
            eventLogger, NEW_VERSION_MINOR_CHANGE);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // Update available -> added release notes
        //
        releaseNotesMessageProvider.doUpdate(messageContainers);
        assertEquals(1, messageContainers.size());
        assertTrue(messageContainers.containsKey(MessageProviderMessageId.MESSAGE_PATCH_RELEASE_NOTES_ID.getId()));

        //
        // Expecting update of DB version info
        //
        verify(versionService, times(1)).set(any());

        Mockito.verify(eventLogger).log(Mockito.any(Event.class));
    }

    @Test
    public void test_updateAndRestart() throws IOException, InterruptedException {
        ReleaseNotesMessageProvider releaseNotesMessageProvider = new ReleaseNotesMessageProvider(versionService, eventLogger, NEW_VERSION);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // Update available -> added release notes
        //
        releaseNotesMessageProvider.doUpdate(messageContainers);
        assertEquals(1, messageContainers.size());

        //
        // Check again WITHOUT another update (typically by restarting the ICAP server)
        //
        releaseNotesMessageProvider = new ReleaseNotesMessageProvider(versionService, eventLogger, NEW_VERSION);

        //
        // Message still available
        //
        releaseNotesMessageProvider.doUpdate(messageContainers);
        assertEquals(1, messageContainers.size());

        //
        // Expecting only one update of DB version info
        //
        verify(versionService, times(1)).set(any());

        Mockito.verify(eventLogger).log(Mockito.any(Event.class));
    }

}
