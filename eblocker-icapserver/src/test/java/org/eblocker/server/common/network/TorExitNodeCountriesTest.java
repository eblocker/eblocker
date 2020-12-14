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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.ExitNodeCountry;
import org.eblocker.server.common.data.Language;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TorExitNodeCountriesTest {
    private TorExitNodeCountries countries;
    private DataSource dataSource;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getCurrentLanguage()).thenReturn(new Language("en", "english"));
        countries = new TorExitNodeCountries(dataSource);
        countries.init();
    }

    @Test
    public void getCountryCodes() {
        Set<String> names = new HashSet<>(asList("France", "Italy", "Legoland", "Germany"));
        Set<String> codes = new HashSet<>(asList("fr", "it", "de"));
        assertEquals(codes, countries.getCountryCodes(names));
    }

    @Test
    public void getExitNodeCountries() {
        Set<ExitNodeCountry> exitNodeCountries = countries.getExitNodeCountries();
        assertTrue(exitNodeCountries.contains(new ExitNodeCountry("Germany", "de")));
        assertTrue(exitNodeCountries.contains(new ExitNodeCountry("Cayman Islands", "ky")));
        assertFalse(exitNodeCountries.contains(new ExitNodeCountry("Fantasy Land", "xy")));
    }
}
