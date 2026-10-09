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
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;

@Singleton
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of(LocaleSettings.DEFAULT_TIMEZONE);
    private static final Locale DEFAULT_LOCALE = new Locale(LocaleSettings.DEFAULT_COUNTRY, LocaleSettings.DEFAULT_LANGUAGE);

    private final DataSource dataSource;
    private final ScriptRunner scriptRunner;
    private final String setTimezoneCommand;
    private final TorExitNodeCountries torExitNodeCountries;

    @Inject
    public SettingsService(DataSource dataSource,
                           ScriptRunner scriptRunner,
                           @Named("set.timezone.command") String setTimezoneCommand,
                           TorExitNodeCountries torExitNodeCountries) throws IOException {

        this.dataSource = dataSource;
        this.scriptRunner = scriptRunner;
        this.setTimezoneCommand = setTimezoneCommand;
        this.torExitNodeCountries = torExitNodeCountries;

        setTimeZone(getTimeZone().getId());
    }

    public ZoneId getTimeZone() {
        String timezone = dataSource.getTimezone();
        if (timezone != null) {
            return ZoneId.of(timezone);
        }
        return DEFAULT_TIMEZONE;
    }

    private LocaleSettings setTimeZone(String posixTimezone) throws IOException {
        ZoneId timezone = ZoneId.of(posixTimezone);
        dataSource.setTimezone(timezone.getId());
        scriptRunner.startScript(setTimezoneCommand, posixTimezone);
        return getLocaleSettings();
    }

    private Locale getLocale() {
        Language language = dataSource.getCurrentLanguage();
        if (language == null) {
            return DEFAULT_LOCALE;
        }
        switch (language.getId()) {

            case "en":
                return Locale.US;

            case "de":
                return Locale.GERMANY;

            default:
                return DEFAULT_LOCALE;
        }
    }

    private boolean getClock24() {
        Language language = dataSource.getCurrentLanguage();
        if (language == null) {
            return LocaleSettings.DEFAULT_CLOCK;
        }
        switch (language.getId()) {
            case "en":
                return false;

            case "de":
                return true;

            default:
                return LocaleSettings.DEFAULT_CLOCK;
        }
    }

    public LocaleSettings getLocaleSettings() {
        Locale locale = getLocale();
        return new LocaleSettings(
                locale.getDisplayName(),
                locale.getCountry(),
                locale.getLanguage(),
                getTimeZone().getId(),
                getClock24()
        );
    }

    public LocaleSettings setLocaleSettings(LocaleSettings localeSettings) throws IOException {
        String langID = localeSettings.getLanguage();
        String langName = localeSettings.getName();

        if (langID != null && !langID.equals("") && langName != null && !langName.equals("")) {
            Language lang = new Language(langID, langName);
            log.info("Setting language id: {} name: {}", lang.getId(), lang.getName());
            dataSource.setCurrentLanguage(lang);
            // Language has changed, tell TorExitNodeCountries to update its list
            torExitNodeCountries.createListOfTorCountryCodes();
        }
        return setTimeZone(localeSettings.getTimezone());
    }
}
