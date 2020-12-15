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
package org.eblocker.server.common.data.migrations;

import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.ExitNodeCountry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

/**
 * Creates EblockerDnsServerState based on current config
 */
public class SchemaMigrationVersion17 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion17(
            DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "16";
    }

    @Override
    public String getTargetVersion() {
        return "17";
    }

    @Override
    public void migrate() {
        Set<String> oldCountries = dataSource.getCurrentTorExitNodes();
        if (oldCountries == null) {
            oldCountries = Collections.emptySet();
        }
        Set<String> newCountries = new HashSet<>();

        Locale lang = Locale.forLanguageTag("en");
        Map<String, String> nameToCode = Arrays.asList(Locale.getISOCountries()).stream()
                .map(code -> new Locale("", code))
                .map(locale -> new ExitNodeCountry(locale.getDisplayCountry(lang), locale.getCountry().toLowerCase()))
                .collect(toMap(c -> c.getName(), c -> c.getCode().toLowerCase()));

        // Try to convert from country names to country codes
        if (oldCountries != null) {
            for (String oldCountry : oldCountries) {
                // Is it the name of a country?
                if (nameToCode.containsKey(oldCountry)) {
                    newCountries.add(nameToCode.get(oldCountry));
                } else if (nameToCode.values().contains(oldCountry)) {
                    // Is it already the code of a country?
                    newCountries.add(oldCountry);
                }
            }
        }

        dataSource.saveCurrentTorExitNodes(newCountries);
        dataSource.setVersion("17");
    }

}
