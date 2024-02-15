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

import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class FilterListsOutdatedMessageProviderTest {
    private FilterListsOutdatedMessageProvider provider;
    private DataSource dataSource;
    private TestClock clock;
    private final int outdatedDays = 30;
    Map<Integer, MessageContainer> messageContainers;
    private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private final Integer MESSAGE_ID = MessageProviderMessageId.MESSAGE_FILTER_LISTS_OUTDATED_ID.getId();

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        clock = new TestClock(ZonedDateTime.now());
        messageContainers = new HashMap();
        provider = new FilterListsOutdatedMessageProvider(outdatedDays, dataSource, clock);
    }

    @Test
    public void testListsOutdated() throws Exception {
        String listsVersion = "20210806000000";
        String currentDate  = "20210906000000";
        Mockito.when(dataSource.getListsPackageVersion()).thenReturn(listsVersion);
        clock.setInstant(dateFormat.parse(currentDate).toInstant());

        provider.doUpdate(messageContainers);

        Assert.assertEquals(1, messageContainers.size());
        Assert.assertEquals("30", messageContainers.get(MESSAGE_ID).getMessage().getContext().get("outdatedDays"));

        // Simulate list update:
        Mockito.when(dataSource.getListsPackageVersion()).thenReturn("20210905000000");
        provider.doUpdate(messageContainers);

        // Message was removed:
        Assert.assertEquals(0, messageContainers.size());
    }

    @Test
    public void testListsNotOutdated() throws Exception {
        String listsVersion = "20210806000000";
        String outdatedDate = "20210831000000";
        Mockito.when(dataSource.getListsPackageVersion()).thenReturn(listsVersion);
        clock.setInstant(dateFormat.parse(outdatedDate).toInstant());

        provider.doUpdate(messageContainers);

        Assert.assertEquals(0, messageContainers.size());
    }
}