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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimezoneControllerImplTest {
    private TimezoneControllerImpl controller;

    @Before
    public void setUp() {
        controller = new TimezoneControllerImpl("classpath:posix_timezone_strings.json");
    }

    @Test
    public void testGetTimezoneCategories() {
        Set<String> categories = controller.getTimezoneCategories(null, null);
        assertTrue(categories.containsAll(Arrays.asList("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Brazil", "Canada", "Chile", "Europe", "Indian", "Mexico", "Pacific")));
    }

    @Test
    public void testGetTimezoneStringForMexico() {
        Map<String, String> params = new HashMap<>();
        params.put("timezoneContinent", "Mexico");
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(Map.class)).thenReturn(params);

        assertTrue(controller.getTimeZoneStringsForCategory(request, null).containsAll(Arrays.asList("BajaNorte", "BajaSur", "General")));
    }

    @Test
    public void testGetTimezoneStringForInvalidString() {
        Map<String, String> params = new HashMap<>();
        params.put("timezoneContinent", "ADskdjAk");
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(Map.class)).thenReturn(params);

        assertEquals(0, controller.getTimeZoneStringsForCategory(request, null).size());
    }

    @Test
    public void testGetTimezoneStringForInvalidArgument() {
        Map<String, String> params = new HashMap<>();
        params.put("asdasd", "ADskdjAk");
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(Map.class)).thenReturn(params);

        assertEquals(0, controller.getTimeZoneStringsForCategory(request, null).size());
    }
}
