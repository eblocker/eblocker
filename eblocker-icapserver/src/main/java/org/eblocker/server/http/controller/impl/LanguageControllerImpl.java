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

import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.http.controller.LanguageController;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class handles and controls the language settings of the frontend
 */
public class LanguageControllerImpl implements LanguageController {

    private static final Logger log = LoggerFactory.getLogger(LanguageControllerImpl.class);

    Set<Language> languages = new HashSet<>();
    Language currentLanguage;

    private final DataSource dataSource;
    private final TorExitNodeCountries torExitNodeCountries;

    @Inject
    public LanguageControllerImpl(DataSource dataSource, TorExitNodeCountries torExitNodeCountries) {
        this.dataSource = dataSource;
        this.torExitNodeCountries = torExitNodeCountries;
        loadLanguages();
    }

    private void loadLanguages() {
        Language english = new Language("en", "English");
        languages.add(english);

        currentLanguage = getCurrentLanguage();

        if (currentLanguage == null) {//default language
            currentLanguage = english;
        }

        log.info("Current language of the frontend is : {}", currentLanguage);

        //TODO add more languages here
        languages.add(new Language("de", "Deutsch"));
    }

    private Set<Language> getAllAvailableLanguages() {
        return languages;
    }

    @Override
    public Set<Language> getAllAvailableLanguages(Request request, Response response) {
        //TODO load from redis
        return getAllAvailableLanguages();
    }

    private Language getCurrentLanguage() {
        return dataSource.getCurrentLanguage();
    }

    @Override
    public Language getCurrentLanguage(Request request, Response response) {
        return getCurrentLanguage();
    }

    @Override
    public void setLanguage(Request request, Response response) {
        Map<String, String> map = request.getBodyAs(HashMap.class);
        String langID = map.get("id");

        log.debug("Received languageID: {}", langID);

        if (langID == null || langID.equals("")) {
            log.warn("Not a valid language id: {}", langID);
            return;
        }

        Language lang = findLanguageWithID(langID);
        if (lang == null) {
            log.warn("Not a valid language id: {}", langID);
            return;
        } else {
            log.info("Setting language id: {}, name: {}", lang.getId(), lang.getName());

            dataSource.setCurrentLanguage(lang);
            // Language has changed, tell TorExitNodeCountries to update its list
            torExitNodeCountries.createListOfTorCountryCodes();
        }
    }

    /**
     * Lookup the language with a given ID
     *
     * @param langID
     * @return
     */
    private Language findLanguageWithID(String langID) {
        for (Language lang : languages) {
            if (lang.getId().equals(langID)) {
                return lang;
            }
        }
        return null;
    }
}
