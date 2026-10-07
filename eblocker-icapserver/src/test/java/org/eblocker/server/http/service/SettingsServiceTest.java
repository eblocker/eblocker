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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsServiceTest {
    private static final String TIMEZONE_ID = "America/New_York";

    private SettingsService settingsService;
    private DataSource dataSource;
    private ScriptRunner scriptRunner;
    private String setTimezoneCommand;
    private TorExitNodeCountries torExitNodeCountries;

    @BeforeEach
    public void setUp() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        torExitNodeCountries = Mockito.mock(TorExitNodeCountries.class);
        setTimezoneCommand = "set_timezone";
        settingsService = new SettingsService(dataSource, scriptRunner, setTimezoneCommand, torExitNodeCountries);
    }

    @Test
    public void testTimeZone_getUninitialized() {
        Mockito.when(dataSource.getTimezone()).thenReturn(null);

        ZoneId timezone = settingsService.getTimeZone();

        assertNotNull(timezone);
        assertEquals(ZoneId.of(LocaleSettings.DEFAULT_TIMEZONE), timezone);
    }

    @Test
    public void testTimeZone_get() {
        Mockito.when(dataSource.getTimezone()).thenReturn(TIMEZONE_ID);

        ZoneId timezone = settingsService.getTimeZone();

        assertNotNull(timezone);
        assertEquals(ZoneId.of(TIMEZONE_ID), timezone);
    }

    @Test
    public void testLocaleSettings_get() {
        Mockito.when(dataSource.getCurrentLanguage()).thenReturn(new Language("en", "English"));
        Mockito.when(dataSource.getTimezone()).thenReturn(TIMEZONE_ID);

        LocaleSettings result = settingsService.getLocaleSettings();
        assertEquals("en", result.getLanguage());
        assertEquals("US", result.getCountry());
        assertFalse(result.isClock24());
        assertEquals("English (United States)", result.getName());
        assertEquals(TIMEZONE_ID, result.getTimezone());
    }

    @Test
    public void testLocaleSettings_set() throws IOException {
        LocaleSettings localeSettings = new LocaleSettings("English (United States)", "US", "en", TIMEZONE_ID, true);
        settingsService.setLocaleSettings(localeSettings);

        Mockito.verify(dataSource).setCurrentLanguage(new Language("en", "English (United States)"));
        Mockito.verify(dataSource).setTimezone(TIMEZONE_ID);
    }
}
