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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.controller.SettingsController;
import org.eblocker.server.http.service.SettingsService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;

public class SettingsControllerImpl implements SettingsController {
    private final SettingsService settingsService;

    @Inject
    public SettingsControllerImpl(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public LocaleSettings getLocaleSettings(Request request, Response response) {
        return settingsService.getLocaleSettings();
    }

    public LocaleSettings setLocale(Request request, Response response) throws IOException {
        LocaleSettings localeSettings = request.getBodyAs(LocaleSettings.class);

        return settingsService.setLocaleSettings(localeSettings);
    }
}
