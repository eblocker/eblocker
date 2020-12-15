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
package org.eblocker.server.icap.filter.url;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DomainExcluder {

    @JsonProperty("domains")
    private final String[] domains;

    @JsonProperty("whiteList")
    private final boolean whiteList; // true: white list; false: black list

    public DomainExcluder(@JsonProperty("domains") List<String> domains, @JsonProperty("whiteList") boolean whiteList) {
        this.domains = domains.toArray(new String[]{});
        this.whiteList = whiteList;
    }

    public boolean isExcluded(String hostname) {
        if (domains == null || domains.length == 0) {
            // Without any domains for comparison, we cannot exclude anything.
            return false;
        }
        if (hostname == null) {
            // Without a hostname, it's excluded by any whitelist
            // but cannot be excluded by a blacklist
            return whiteList;
        }
        for (String domain : domains) {
            if (hostname.equals(domain) || hostname.endsWith("." + domain))
                return !whiteList;
        }
        return whiteList;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        if (domains == null || domains.length == 0) {
            s.append("-");
        } else {
            s.append(whiteList ? "" : "~").append(domains[0]);
            for (int i = 1; i < domains.length; i++) {
                s.append("|").append(whiteList ? "" : "~").append(domains[i]);
            }
        }
        return s.toString();
    }
}
