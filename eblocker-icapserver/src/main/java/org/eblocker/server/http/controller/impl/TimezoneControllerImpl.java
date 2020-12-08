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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.http.controller.TimezoneController;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is able to set the default timezone
 * <p>
 * Deprecated: There is also a script to set the OS timezone called: set_timezone; it expects the posix timezone string as the first parameter
 */
public class TimezoneControllerImpl implements TimezoneController {
    private static final Logger log = LoggerFactory.getLogger(TimezoneControllerImpl.class);

    private Map<String, List<String>> posixTimezoneStrings = new HashMap<String, List<String>>();

    @Inject
    public TimezoneControllerImpl(@Named("posix.timezone.strings.json.file") String posixStringsFile) {
        loadTimeZoneStringsFromFile(posixStringsFile);
    }

    /**
     * Load (and parse) the JSON file containing the POSIX timezone format strings
     */
    private void loadTimeZoneStringsFromFile(String filePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleResource jsonFile = new SimpleResource(filePath);
        try {
            posixTimezoneStrings = objectMapper.readValue(ResourceHandler.load(jsonFile), Map.class);
        } catch (IOException e) {
            log.error("Error while time zone strings from file.", e);
            ;
        }
    }

    @Override
    public Set<String> getTimezoneCategories(Request request, Response response) {
        if (posixTimezoneStrings != null && posixTimezoneStrings.size() > 0) {
            return posixTimezoneStrings.keySet();
        }
        return Collections.emptySet();
    }

    @Override
    public List<String> getTimeZoneStringsForCategory(Request request, Response response) {
        Map<String, String> map = request.getBodyAs(Map.class);
        if (map != null) {
            String continent = map.get("timezoneContinent");
            if (continent != null) {
                List<String> timezones = posixTimezoneStrings.get(continent);
                if (timezones != null) {
                    return timezones;
                }
            }
        }
        return Collections.emptyList();
    }
}
