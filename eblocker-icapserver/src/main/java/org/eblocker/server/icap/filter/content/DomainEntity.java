/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.filter.content;

import com.google.common.net.InternetDomainName;

/**
 * A domain ending in a wildcard (*). The wildcard matches all registry suffixes.
 */
public class DomainEntity extends Domain {
    public DomainEntity(String domain) {
        super(getDomainPattern(domain));
    }

    private static String getDomainPattern(String domain) {
        if (!domain.endsWith(".*")) {
            throw new IllegalArgumentException("Expected a domain entity to end with '.*'");
        }
        String pattern = domain.substring(0, domain.length() - 1); // remove asterisk
        if (pattern.length() == 1) {
            throw new IllegalArgumentException("Empty domain entity: " + domain);
        }
        return pattern;
    }

    @Override
    public boolean matches(String hostname) {
        if (hostname.startsWith(domain) || hostname.contains("." + domain)) {
            // is the rest a public suffix?
            int loc = hostname.lastIndexOf("." + domain);
            if (loc >= 0) {
                loc += domain.length() + 1;
            } else {
                loc = domain.length(); // domain must have started with pattern
            }
            String suffix = hostname.substring(loc);
            return InternetDomainName.from(suffix).isRegistrySuffix();
        }
        return false;
    }

    @Override
    public String toString() {
        return domain + "*";
    }
}
