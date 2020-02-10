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
package org.eblocker.server.icap.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;

public class FilterStoreConfiguration {

    private final Integer id;
    private final String name;
    private final Category category;
    private final boolean builtin;
    private final long version;
    private final String[] resources;
    private final FilterLearningMode learningMode;
    private final FilterDefinitionFormat format;
    private final boolean learnForAllDomains;
    private final String[] ruleFilters;
    private final boolean enabled;

    @Inject
    public FilterStoreConfiguration(@JsonProperty("id") Integer id,
                                    @JsonProperty("name") String name,
                                    @JsonProperty("category") Category category,
                                    @JsonProperty("builtin") boolean builtin,
                                    @JsonProperty("version") long version,
                                    @JsonProperty("resources") String[] resources,
                                    @JsonProperty("learningMode") FilterLearningMode learningMode,
                                    @JsonProperty("format") FilterDefinitionFormat format,
                                    @JsonProperty("learnForAllDomains") boolean learnForAllDomains,
                                    @JsonProperty("ruleFilters") String[] ruleFilters,
                                    @JsonProperty("enabled") boolean enabled) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.builtin = builtin;
        this.version = version;
        this.resources = resources;
        this.learningMode = learningMode;
        this.format = format;
        this.learnForAllDomains = learnForAllDomains;
        this.ruleFilters = ruleFilters;
        this.enabled = enabled;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public long getVersion() {
        return version;
    }

    public String[] getResources() {
        return resources;
    }

    public FilterLearningMode getLearningMode() {
        return learningMode;
    }

    public FilterDefinitionFormat getFormat() {
        return format;
    }

    public boolean isLearnForAllDomains() {
        return learnForAllDomains;
    }

    public String[] getRuleFilters() {
        return ruleFilters;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
