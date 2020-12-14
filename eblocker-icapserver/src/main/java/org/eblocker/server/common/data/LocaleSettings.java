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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LocaleSettings {

    public static final String DEFAULT_NAME = "Deutsch";
    public static final String DEFAULT_COUNTRY = "DE";
    public static final String DEFAULT_LANGUAGE = "de";
    public static final String DEFAULT_TIMEZONE = "Europe/Berlin";
    public static final boolean DEFAULT_CLOCK = true;

    private final String name;

    private final String country;

    private final String language;

    private final String timezone;

    private final boolean clock24;

    @JsonCreator
    public LocaleSettings(
        @JsonProperty("name") String name,
        @JsonProperty("country") String country,
        @JsonProperty("language") String language,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("clock24") Boolean clock24) {
        this.name = name == null ? DEFAULT_NAME : name;
        this.country = country == null ? DEFAULT_COUNTRY : country;
        this.language = language == null ? DEFAULT_LANGUAGE : language;
        this.timezone = timezone == null ? DEFAULT_TIMEZONE : timezone;
        this.clock24 = clock24 == null ? true : clock24;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getLanguage() {
        return language;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isClock24() {
        return clock24;
    }
}
