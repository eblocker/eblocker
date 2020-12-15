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
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SettingsServiceTest {

    private static final String TIMEZONE_ID = "Europe/Berlin";

    @Test
    public void testTimeZone_getUninitialized() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        when(dataSource.getTimezone()).thenReturn(null);

        SettingsService settingsService = new SettingsService(dataSource);
        ZoneId timezone = settingsService.getTimeZone();

        assertNotNull(timezone);
        assertEquals(ZoneId.of(TIMEZONE_ID), timezone);

        verify(dataSource).getTimezone();
        verify(dataSource).setTimezone(TIMEZONE_ID);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void testTimeZone_get() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        when(dataSource.getTimezone()).thenReturn(TIMEZONE_ID);

        SettingsService settingsService = new SettingsService(dataSource);
        ZoneId timezone = settingsService.getTimeZone();

        assertNotNull(timezone);
        assertEquals(ZoneId.of(TIMEZONE_ID), timezone);

        verify(dataSource).getTimezone();
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void testTimeZone_set() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        SettingsService settingsService = new SettingsService(dataSource);
        settingsService.setTimeZone(ZoneId.of(TIMEZONE_ID));

        verify(dataSource).setTimezone(TIMEZONE_ID);
        verifyNoMoreInteractions(dataSource);
    }

}
