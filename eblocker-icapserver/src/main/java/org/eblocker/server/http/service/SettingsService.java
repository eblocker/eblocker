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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.LocaleSettings;

import java.time.ZoneId;
import java.util.Locale;

@Singleton
public class SettingsService {

    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of(LocaleSettings.DEFAULT_TIMEZONE);
    private static final Locale DEFAULT_LOCALE = new Locale(LocaleSettings.DEFAULT_COUNTRY, LocaleSettings.DEFAULT_LANGUAGE);
    private final DataSource dataSource;

    @Inject
    public SettingsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ZoneId getTimeZone() {
        String timezone = dataSource.getTimezone();
        if (timezone != null) {
            return ZoneId.of(timezone);
        }
        dataSource.setTimezone(DEFAULT_TIMEZONE.getId());
        return DEFAULT_TIMEZONE;
    }

    public void setTimeZone(ZoneId timeZone) {
        dataSource.setTimezone(timeZone.getId());
    }

    //TODO: This is very preliminary and must be improved - but sufficient for now.
    public Locale getLocale() {
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

    //TODO: This is very preliminary and must be improved - but sufficient for now.
    public boolean getClock24() {
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

}
