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
package org.eblocker.server.common.data.openvpn;

import java.util.ArrayList;
import java.util.List;

public class OpenVpnConfigurationViewModel {

    private List<ConfigLine> activeOptions = new ArrayList<>();
    private List<ConfigLine> blacklistedOptions = new ArrayList<>();
    private List<ConfigLine> ignoredOptions = new ArrayList<>();
    private List<RequiredFile> requiredFiles;
    private boolean credentialsRequired;
    private List<String> validationErrors;

    public List<ConfigLine> getActiveOptions() {
        return activeOptions;
    }

    public void setActiveOptions(List<ConfigLine> activeOptions) {
        this.activeOptions = activeOptions;
    }

    public List<ConfigLine> getBlacklistedOptions() {
        return blacklistedOptions;
    }

    public void setBlacklistedOptions(List<ConfigLine> blacklistedOptions) {
        this.blacklistedOptions = blacklistedOptions;
    }

    public List<ConfigLine> getIgnoredOptions() {
        return ignoredOptions;
    }

    public void setIgnoredOptions(List<ConfigLine> ignoredOptions) {
        this.ignoredOptions = ignoredOptions;
    }

    public List<RequiredFile> getRequiredFiles() {
        return requiredFiles;
    }

    public void setRequiredFiles(List<RequiredFile> requiredFiles) {
        this.requiredFiles = requiredFiles;
    }

    public boolean isCredentialsRequired() {
        return credentialsRequired;
    }

    public void setCredentialsRequired(boolean credentialsRequired) {
        this.credentialsRequired = credentialsRequired;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public static class ConfigLine {
        public Integer lineNumber;
        public String line;

        public String source;  // user / eblocker
        public Integer overriddenLineNumber;
        public String overriddenLine;
    }

    public static class RequiredFile {
        public String option;
        public String name;
        public boolean uploaded;
    }
}
