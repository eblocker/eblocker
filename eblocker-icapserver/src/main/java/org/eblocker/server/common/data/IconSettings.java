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
import org.eblocker.server.common.data.Device.DisplayIconPosition;

public class IconSettings {


    DisplayIconMode iconMode;

    private final DisplayIconPosition iconPosition;

    @JsonCreator
    public IconSettings(@JsonProperty("enabled") boolean enabled,
                        @JsonProperty("fiveSeconds") boolean fiveSeconds,
                        @JsonProperty("browserOnly") boolean browserOnly,
                        @JsonProperty("iconPosition") DisplayIconPosition iconPosition) {
        this.iconMode = DisplayIconMode.generateFrom(enabled, fiveSeconds, browserOnly);
        this.iconPosition = iconPosition;
    }

    public IconSettings(Device device) {
        this.iconMode = device.getIconMode();
        this.iconPosition = device.getIconPosition();
    }

    @JsonProperty
    public boolean isEnabled() {
        return iconMode.isEnabled();
    }

    @JsonProperty
    public boolean isFiveSeconds() {
        return iconMode.isFiveSeconds();
    }

    @JsonProperty
    public boolean isBrowserOnly() {
        return iconMode.isBrowserOnly();
    }

    @JsonProperty
    public DisplayIconPosition getIconPosition() {
        return iconPosition;
    }

    public DisplayIconMode getDisplayIconMode() {
        return iconMode;
    }
}
