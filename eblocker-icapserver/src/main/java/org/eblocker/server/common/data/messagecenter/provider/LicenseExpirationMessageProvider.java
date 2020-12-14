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
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class LicenseExpirationMessageProvider extends AbstractMessageProvider {

    public static final String MESSAGE_LICENSE_EXPIRING_TITLE = "MESSAGE_LICENSE_EXPIRING_TITLE";
    public static final String MESSAGE_LICENSE_EXPIRING_MONTH_CONTENT = "MESSAGE_LICENSE_EXPIRING_MONTH_CONTENT";
    public static final String MESSAGE_LICENSE_EXPIRING_WEEK_CONTENT = "MESSAGE_LICENSE_EXPIRING_WEEK_CONTENT";
    public static final String MESSAGE_LICENSE_EXPIRING_DAY_CONTENT = "MESSAGE_LICENSE_EXPIRING_DAY_CONTENT";
    public static final String MESSAGE_LICENSE_EXPIRING_LABEL = "MESSAGE_LICENSE_EXPIRING_LABEL";
    public static final String MESSAGE_LICENSE_EXPIRING_URL = "MESSAGE_LICENSE_EXPIRING_URL";

    public static final String MESSAGE_LICENSE_EXPIRED_TITLE = "MESSAGE_LICENSE_EXPIRED_TITLE";
    public static final String MESSAGE_LICENSE_EXPIRED_CONTENT = "MESSAGE_LICENSE_EXPIRED_CONTENT";
    public static final String MESSAGE_LICENSE_EXPIRED_LABEL = "MESSAGE_LICENSE_EXPIRED_LABEL";
    public static final String MESSAGE_LICENSE_EXPIRED_URL = "MESSAGE_LICENSE_EXPIRED_URL";

    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final EventLogger eventLogger;

    private final int expirationWarningThresholdWeek;
    private final int expirationWarningThresholdDay;

    private static final Set<Integer> MESSAGE_IDS = new HashSet<>();

    static {
        MESSAGE_IDS.add(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());
        MESSAGE_IDS.add(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId());
    }

    @Inject
    public LicenseExpirationMessageProvider(DeviceRegistrationProperties deviceRegistrationProperties,
                                            EventLogger eventLogger,
                                            @Named("license.expiration.warning.threshold.day") int licenseExpirationWarningThresholdDay,
                                            @Named("license.expiration.warning.threshold.week") int licenseExpirationWarningThresholdWeek) {
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.eventLogger = eventLogger;
        this.expirationWarningThresholdWeek = licenseExpirationWarningThresholdWeek;
        this.expirationWarningThresholdDay = licenseExpirationWarningThresholdDay;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return MESSAGE_IDS;
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        if (deviceRegistrationProperties.isLicenseAboutToExpire()) {
            //
            // Make sure, message does exist
            //
            Map<String, String> context = new HashMap<>();
            context.put("licenseNotValidAfter", Long.toString(deviceRegistrationProperties.getLicenseNotValidAfter().getTime()));
            if (deviceRegistrationProperties.getLicenseNotValidAfter().before(new Date())) {
                //
                // Already expired
                //
                if (!messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId())) {
                    messageContainers.put(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId(),
                            createMessage(
                                    MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId(),
                                    MESSAGE_LICENSE_EXPIRED_TITLE,
                                    MESSAGE_LICENSE_EXPIRED_CONTENT,
                                    MESSAGE_LICENSE_EXPIRED_LABEL,
                                    MESSAGE_LICENSE_EXPIRED_URL,
                                    context,
                                    true,
                                    MessageSeverity.ALERT
                            ));
                    // Log event
                    eventLogger.log(Events.licenseExpired());
                }
                if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId())) {
                    messageContainers.remove(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());
                }

            } else {
                //
                // Soon expiring
                //
                // How soon?
                Duration duration = Duration.between(new Date().toInstant(), deviceRegistrationProperties.getLicenseNotValidAfter().toInstant());
                long remainingDays = duration.toDays();
                // Is there a less-urgent message to remove?
                if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId())) {
                    MessageContainer currentMessage = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());
                    if (currentMessage.getMessage().getContentKey().equals(MESSAGE_LICENSE_EXPIRING_MONTH_CONTENT)
                            && remainingDays <= expirationWarningThresholdWeek
                            || currentMessage.getMessage().getContentKey().equals(MESSAGE_LICENSE_EXPIRING_WEEK_CONTENT)
                            && remainingDays <= expirationWarningThresholdDay) {
                        messageContainers.remove(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());
                    }
                }

                if (!messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId())) {
                    messageContainers.put(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(),
                            createMessage(
                                    MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(),
                                    MESSAGE_LICENSE_EXPIRING_TITLE,
                                    (remainingDays <= expirationWarningThresholdDay
                                            ? MESSAGE_LICENSE_EXPIRING_DAY_CONTENT
                                            : (remainingDays <= expirationWarningThresholdWeek
                                            ? MESSAGE_LICENSE_EXPIRING_WEEK_CONTENT
                                            : MESSAGE_LICENSE_EXPIRING_MONTH_CONTENT)),
                                    MESSAGE_LICENSE_EXPIRING_LABEL,
                                    MESSAGE_LICENSE_EXPIRING_URL,
                                    context,
                                    true,
                                    MessageSeverity.ALERT
                            ));
                }
                if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId())) {
                    messageContainers.remove(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId());
                }

            }
        } else {
            //
            // Make sure, messages do not exist
            //
            if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId())) {
                messageContainers.remove(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());
            }
            if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId())) {
                messageContainers.remove(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId());
            }
        }
    }

}
