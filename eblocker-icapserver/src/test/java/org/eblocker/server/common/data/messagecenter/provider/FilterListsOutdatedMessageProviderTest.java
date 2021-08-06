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