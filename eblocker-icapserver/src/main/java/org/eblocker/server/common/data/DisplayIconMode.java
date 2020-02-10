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

public enum DisplayIconMode{
    ON(true,false,true),// This means "on, but only for standard-browsers"
    ON_ALL_DEVICES(true,false,false),
    OFF(false,false,false),
    FIVE_SECONDS(true,true,false),
    FIVE_SECONDS_BROWSER_ONLY(true,true,true);

    boolean enabled;
    boolean fiveSeconds;
    boolean browserOnly;

    DisplayIconMode(boolean enabled, boolean fiveSeconds, boolean browserOnly) {
        this.enabled = enabled;
        this.fiveSeconds = fiveSeconds;
        this.browserOnly = browserOnly;
    }
    public static DisplayIconMode generateFrom(boolean enabled, boolean fiveSeconds, boolean browserOnly) {
        if (!enabled) {
            return OFF;
        }
        for (DisplayIconMode mode : DisplayIconMode.values()) {
            if (mode.isFiveSeconds() == fiveSeconds && mode.isBrowserOnly() == browserOnly) {
                return mode;
            }
        }
        return getDefault();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFiveSeconds() {
        return fiveSeconds;
    }

    public boolean isBrowserOnly() {
        return browserOnly;
    }

    public static DisplayIconMode getDefault() {
        return FIVE_SECONDS_BROWSER_ONLY;
    }
}
