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
