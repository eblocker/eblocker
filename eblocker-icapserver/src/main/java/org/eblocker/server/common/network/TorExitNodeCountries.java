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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.ExitNodeCountry;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Provides a mapping from country names to country codes.
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class TorExitNodeCountries {
    private Set<ExitNodeCountry> exitNodeCountries;
    private Map<String, String> countryCodes;
    private DataSource dataSource;

    @Inject
    public TorExitNodeCountries(DataSource dataSource) {
        this.dataSource = dataSource;

    }

    @SubSystemInit
    public void init() {
        createListOfTorCountryCodes();
    }

    public Set<String> getCountryCodes(Set<String> names) {
        return names.stream()
                .map(countryCodes::get)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    /**
     * Create list of country codes and names according to language used in the frontend
     */
    public void createListOfTorCountryCodes() {
        Locale lang = Locale.forLanguageTag(dataSource.getCurrentLanguage().getId());
        // Set of all countries with their respective code and the name according to language used in the fronted, e.g. (de, Germany)
        exitNodeCountries = Arrays.asList(Locale.getISOCountries()).stream()
                .map(code -> new Locale("", code))
                .map(locale -> new ExitNodeCountry(locale.getDisplayCountry(lang), locale.getCountry().toLowerCase()))
                .collect(Collectors.toSet());

        countryCodes = exitNodeCountries.stream()
                .collect(toMap(c -> c.getName(), c -> c.getCode().toLowerCase()));
    }

    public Set<ExitNodeCountry> getExitNodeCountries() {
        return exitNodeCountries;
    }
}
