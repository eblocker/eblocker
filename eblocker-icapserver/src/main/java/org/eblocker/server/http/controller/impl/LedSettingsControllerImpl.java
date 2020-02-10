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

import org.eblocker.server.common.data.LedSettings;
import org.eblocker.server.http.controller.LedSettingsController;
import org.eblocker.server.http.service.StatusLedService;
import com.google.inject.Inject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

public class LedSettingsControllerImpl implements LedSettingsController {
    private final StatusLedService ledService;

    @Inject
    public LedSettingsControllerImpl(StatusLedService ledService) {
        this.ledService = ledService;
    }

    @Override
    public LedSettings getSettings(Request request, Response response) {
        return new LedSettings(ledService.isHardwareAvailable(), ledService.getBrightness());
    }

    @Override
    public void updateSettings(Request request, Response response) {
        LedSettings settings = request.getBodyAs(LedSettings.class);
        if (settings == null) {
            throw new BadRequestException("Expected LedSettings");
        }

        ledService.setBrightness(settings.getBrightness());
    }
}
