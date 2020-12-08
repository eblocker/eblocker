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

import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.TosContainer;
import org.eblocker.registration.UpgradeInfo;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

    private final DeviceRegistrationClient client;

    private final DeviceRegistrationProperties properties;

    private final EventLogger eventLogger;

    private final ReminderService reminderService;

    @Inject
    public RegistrationService(
        DeviceRegistrationClient client,
        DeviceRegistrationProperties properties,
        EventLogger eventLogger,
        ReminderService reminderService
    ) {
        this.client = client;
        this.properties = properties;
        this.eventLogger = eventLogger;
        this.reminderService = reminderService;
    }

    public TosContainer getTosContainer() {
        return client.getTosContainer();
    }

    public void checkAndExecuteUpgrade() {
        UpgradeInfo upgradeInfo = client.isUpgradeAvailable();
        if (upgradeInfo.isUpgradeAvailable()) {
            DeviceRegistrationRequest deviceRegistrationRequest = properties.generateRequest(
                null,
                null,
                null,
                null,
                null,
                null
            );
            try {
                DeviceRegistrationResponse response = client.upgrade(deviceRegistrationRequest);
                properties.processResponse(response);
                // Call ReminderService to calculate time for next reminder
                reminderService.setReminder();
                eventLogger.log(Events.licenseUpgraded());

            } catch (Exception e) {
                LOG.error("Cannot upgrades license", e);
                eventLogger.log(Events.licenseUpgradFailed());
            }
        }
    }

}
