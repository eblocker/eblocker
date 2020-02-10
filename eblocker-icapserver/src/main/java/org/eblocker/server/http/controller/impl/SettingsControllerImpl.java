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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.http.controller.SettingsController;
import org.eblocker.server.http.service.SettingsService;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;

public class SettingsControllerImpl implements SettingsController {
    private static final Logger log = LoggerFactory.getLogger(SettingsControllerImpl.class);

    private final SettingsService settingsService;
    private final DataSource dataSource;
    private final TorExitNodeCountries torExitNodeCountries;
    private final ScriptRunner scriptRunner;
    private final String setTimezoneCommand;

    @Inject
    public SettingsControllerImpl(SettingsService settingsService,
                                  DataSource dataSource,
                                  TorExitNodeCountries torExitNodeCountries,
                                  ScriptRunner scriptRunner,
                                  @Named("set.timezone.command") String setTimezoneCommand) throws IOException {
        this.dataSource = dataSource;
        this.torExitNodeCountries = torExitNodeCountries;
        this.settingsService = settingsService;
        this.scriptRunner = scriptRunner;
        this.setTimezoneCommand = setTimezoneCommand;

        setTimeZone(dataSource.getTimezone());
    }

    @Override
    public LocaleSettings getLocaleSettings(Request request, Response response) {
        return settingsService.getLocaleSettings();
    }

    @Override
    public LocaleSettings setTimeZone(Request request, Response response) throws IOException {
        LocaleSettings localeSettings = request.getBodyAs(LocaleSettings.class);
        return setTimeZone(localeSettings.getTimezone());
    }

    private LocaleSettings setTimeZone(String posixTimezone) throws IOException {
        ZoneId timezone = ZoneId.of(posixTimezone);
        settingsService.setTimeZone(timezone);
        scriptRunner.startScript(setTimezoneCommand, posixTimezone);
        return settingsService.getLocaleSettings();
    }

    public LocaleSettings setLocale(Request request, Response response) throws IOException {
        LocaleSettings localeSettings = request.getBodyAs(LocaleSettings.class);
        ZoneId timezone = ZoneId.of(localeSettings.getTimezone());

        String langID = localeSettings.getLanguage();
        String langName = localeSettings.getName();

        if(langID != null && !langID.equals("") && langName != null && !langName.equals("")) {
            Language lang = new Language(langID, langName);
            log.info("Setting language id: {} name: {}", lang.getId(), lang.getName());
            dataSource.setCurrentLanguage(lang);
            // Language has changed, tell TorExitNodeCountries to update its list
            torExitNodeCountries.createListOfTorCountryCodes();
        }
        return setTimeZone(localeSettings.getTimezone());
    }

}
