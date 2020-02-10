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
package org.eblocker.server.http.model;

import java.util.Collections;
import java.util.Set;

public class CustomDomainFilterConfig {
    private Set<String> blacklistedDomains;
    private Set<String> whitelistedDomains;

    public CustomDomainFilterConfig() {
        this.blacklistedDomains = Collections.emptySet();
        this.whitelistedDomains = Collections.emptySet();
    }

    public CustomDomainFilterConfig(Set<String> blacklistedDomains, Set<String> whitelistedDomains) {
        this.blacklistedDomains = blacklistedDomains;
        this.whitelistedDomains = whitelistedDomains;
    }

    public Set<String> getBlacklistedDomains() {
        return blacklistedDomains;
    }

    public void setBlacklistedDomains(Set<String> blacklistedDomains) {
        this.blacklistedDomains = blacklistedDomains;
    }

    public Set<String> getWhitelistedDomains() {
        return whitelistedDomains;
    }

    public void setWhitelistedDomains(Set<String> whitelistedDomains) {
        this.whitelistedDomains = whitelistedDomains;
    }
}
