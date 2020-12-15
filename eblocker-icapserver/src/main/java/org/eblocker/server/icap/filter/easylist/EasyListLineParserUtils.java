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
package org.eblocker.server.icap.filter.easylist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class EasyListLineParserUtils {
    private static final Pattern DOMAIN_NAME_PATTERN = Pattern.compile("([*\\p{L}\\d._-]*).*");

    private EasyListLineParserUtils() {
    }

    static String findDomain(String definition) {
        Matcher matcher = DOMAIN_NAME_PATTERN.matcher(definition);
        if (matcher.matches()) {
            return normalizeDomain(matcher.group(1));
        }
        return null;
    }

    private static String normalizeDomain(String domain) {
        if (!domain.contains(".")) {
            // ignore incomplete host names with wild card
            if (domain.contains("*")) {
                return null;
            }
            // assume it is a tld
            return "*." + domain;
        }

        // append proper wild card
        if (domain.endsWith(".")) {
            return domain + "*";
        }

        return domain;
    }
}
