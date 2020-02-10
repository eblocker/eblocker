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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenVpnConfiguration {

    private String sourceConfig;
    private List<Option> userOptions = new ArrayList<>();
    private Map<String, String> inlinedContentByName = new HashMap<>();

    public String getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(String sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public List<Option> getUserOptions() {
        return userOptions;
    }

    public void setUserOptions(List<Option> userOptions) {
        this.userOptions = userOptions;
    }

    /**
     * Retrieves a map which keys are option which are stored on disk. Values for these keys will always be null for
     * profiles version 2. In version 1 this holds the actual content to be inlined.
     * @return
     */
    public Map<String, String> getInlinedContentByName() {
        return inlinedContentByName;
    }

    public void setInlinedContentByName(Map<String, String> inlinedContentByName) {
        this.inlinedContentByName = inlinedContentByName;
    }
}
