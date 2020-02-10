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
package org.eblocker.server.common.openvpn.configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Version 0 of OpenVpnConfiguration, just used for upgrades
 */
public class OpenVpnConfigurationVersion0 {
    private List<Option> effectiveOptions = new ArrayList<>();
    private List<Option> ignoredOptions = new ArrayList<>();
    private List<Option> blacklistedOptions = new ArrayList<>();

    public OpenVpnConfigurationVersion0() {
    }

    public List<Option> getEffectiveOptions() {
        return effectiveOptions;
    }

    public void setEffectiveOptions(List<Option> effectiveOptions) {
        this.effectiveOptions = effectiveOptions;
    }

    public List<Option> getIgnoredOptions() {
        return ignoredOptions;
    }

    public void setIgnoredOptions(List<Option> ignoredOptions) {
        this.ignoredOptions = ignoredOptions;
    }

    public List<Option> getBlacklistedOptions() {
        return blacklistedOptions;
    }

    public void setBlacklistedOptions(List<Option> blacklistedOptions) {
        this.blacklistedOptions = blacklistedOptions;
    }
}
