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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.ExpirationDate;
import org.eblocker.server.common.data.NextReminderSelection;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;

@Singleton
public class ReminderService {

    private final DataSource dataSource;
    private static final Random rand = new Random();

    private Instant nextReminder = null;
    private boolean doNotShowAgain = false;
    private final SettingsService settingsService;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final int timeThresholdFirstReminder;
    private final int nextOffsetMin;
    private final int nextOffsetMax;
    private final int warningPeriodDays;
    private static final String NEXT_REMINDER_IN_DAY = "day";
    private static final String NEXT_REMINDER_IN_WEEK = "week";
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    @Inject
    public ReminderService(
        SettingsService settingsService,
        DeviceRegistrationProperties deviceRegistrationProperties,
        DataSource dataSource,
        @Named("license.expiration.warning.threshold.first.reminder") int timeThresholdFirstReminder,
        @Named("license.expiration.warning.nextOffset.min") int nextOffsetMin,
        @Named("license.expiration.warning.nextOffset.max") int nextOffsetMax,
        @Named("registration.warning.period") int warningPeriodDays
    ) {
        this.settingsService = settingsService;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.timeThresholdFirstReminder = timeThresholdFirstReminder;
        this.nextOffsetMin = nextOffsetMin;
        this.nextOffsetMax = nextOffsetMax;
        this.warningPeriodDays = warningPeriodDays;
        setReminder();
        this.dataSource = dataSource;
        this.doNotShowAgain = dataSource.isDoNotShowReminder();
    }

    public void setReminder() {
        // First reminder to be shown on first day of expiration, after 18:00 PM
        if (deviceRegistrationProperties.getRegistrationState() != RegistrationState.NEW
            && deviceRegistrationProperties.getRegistrationState() != RegistrationState.OK_UNREGISTERED) {
            nextReminder = deviceRegistrationProperties.getLicenseNotValidAfter().toInstant()
                .minus(warningPeriodDays, ChronoUnit.DAYS).plus(timeThresholdFirstReminder, ChronoUnit.HOURS);
        }
    }

    public void setNextReminder(NextReminderSelection nextReminderSelection) {
        String when = nextReminderSelection.getNextReminderSelection();
        dataSource.setDoNotShowReminder(nextReminderSelection.isDoNotShowAgain());

        if (when.equals(NEXT_REMINDER_IN_DAY)) {
            long offset = (long) nextOffsetMin + (long) rand.nextInt(nextOffsetMax - nextOffsetMin);
            nextReminder = new Date().toInstant().plus(offset, ChronoUnit.SECONDS);
        } else if (when.equals(NEXT_REMINDER_IN_WEEK)) {
            nextReminder = new Date().toInstant().plus(Period.ofWeeks(1));
        } else {
            log.error("Unknown value {}", when);
            throw new EblockerException("Unknown value " + when);
        }
    }

    public ExpirationDate getExpirationDate() {
        Long daysTillExpiration = deviceRegistrationProperties.getLicenseRemainingDays();
        if (daysTillExpiration == null) {
            throw new EblockerException("Could not get remaining days");
        }
        return new ExpirationDate(deviceRegistrationProperties.getLicenseNotValidAfter(), daysTillExpiration);
    }

    public Boolean isReminderNeeded() {
        if (doNotShowAgain) {
            // User does not wish to be shown a reminder
            return false;
        }
        if (nextReminder == null) {
            // No reminder was shown yet and the user could not make a decision
            // yet
            // Show first message - but only after 6 PM local time
            LocalDateTime now = LocalDateTime.now(settingsService.getTimeZone());
            return (now.getHour() >= timeThresholdFirstReminder);
        }
        return new Date().after(Date.from(nextReminder));
    }

}
