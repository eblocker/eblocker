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
package org.eblocker.server.http.backup;

import org.eblocker.server.http.ssl.AppWhitelistModule;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class AppModulesBackup {
    private final List<AppWhitelistModule> modules;
    private final Map<Integer, Boolean> enabledStates;

    @JsonCreator
    public AppModulesBackup(
        @JsonProperty("modules") List<AppWhitelistModule> modules,
        @JsonProperty("enabledStates") Map<Integer, Boolean> enabledStates
    ) {
        this.modules = modules;
        this.enabledStates = enabledStates;
    }

    public List<AppWhitelistModule> getModules() {
        return modules;
    }

    public Map<Integer, Boolean> getEnabledStates() {
        return enabledStates;
    }
}
