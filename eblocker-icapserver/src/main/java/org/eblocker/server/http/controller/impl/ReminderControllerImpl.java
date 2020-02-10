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
package org.eblocker.server.http.controller.impl;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eblocker.server.http.controller.ReminderController;
import org.eblocker.server.common.data.ExpirationDate;
import org.eblocker.server.common.data.NextReminderSelection;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.http.service.ReminderService;

@Singleton
public class ReminderControllerImpl implements ReminderController {
    private final ReminderService reminderService;
    private static final Logger log = LoggerFactory.getLogger(ReminderControllerImpl.class);

    @Inject
    public ReminderControllerImpl(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    @SuppressWarnings("unused")
    public ExpirationDate getExpirationDate(Request request, Response response){
        return reminderService.getExpirationDate();
    }

    @Override
    @SuppressWarnings("unused")
    public void setNextReminder(Request request, Response response) {
        reminderService.setNextReminder(request.getBodyAs(NextReminderSelection.class));
    }
}
