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
package org.eblocker.server.common.data.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Deprecated
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardCard {

    private final int id;
    private final String requiredFeature;
    private final String translateSuffix;
    private final String html;
    private final boolean visible;
    private final boolean alwaysVisible;
    private final DashboardCardPosition[] defaultPos;
    private final DashboardCardPosition[] customPos;

    public DashboardCard(@JsonProperty("id") int id,
                         @JsonProperty("requiredFeature") String requiredFeature,
                         @JsonProperty("translateSuffix") String translateSuffix,
                         @JsonProperty("html") String html,
                         @JsonProperty("visible") boolean visible,
                         @JsonProperty("alwaysVisible") boolean alwaysVisible,
                         @JsonProperty("defaultPos") DashboardCardPosition[] defaultPos,
                         @JsonProperty("customPos") DashboardCardPosition[] customPos) {
        this.id = id;
        this.requiredFeature = requiredFeature;
        this.translateSuffix = translateSuffix;
        this.html = html;
        this.visible = visible;
        this.alwaysVisible = alwaysVisible;
        this.defaultPos = defaultPos;
        this.customPos = customPos;
    }

    public int getId() {
        return id;
    }

    public String getRequiredFeature() {
        return requiredFeature;
    }

    public String getTranslateSuffix() {
        return translateSuffix;
    }

    public String getHtml() {
        return html;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isAlwaysVisible() {
        return alwaysVisible;
    }

    public DashboardCardPosition[] getDefaultPos() {
        return defaultPos;
    }

    public DashboardCardPosition[] getCustomPos() {
        return customPos;
    }
}
