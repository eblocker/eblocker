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
package org.eblocker.server.icap.filter.csv;

import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterLineParser;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.url.StringMatchType;
import org.eblocker.server.icap.filter.url.UrlFilterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class CSVLineParser implements FilterLineParser {
    private static final Logger log = LoggerFactory.getLogger(CSVLineParser.class);

    private static final String EMPTY = "-";

    @Override
    public Filter parseLine(String line) {
        if (line == null || line.isEmpty() || line.startsWith("#")) {
            return null;
        }

        // 0    1        2      3               4                 5                       6                       7
        // TYPE PRIORITY DOMAIN REGEX/MATCHTYPE PATTERN/SUBSTRING REFERRERDOMAINWHITELIST REFERRERDOMAINBLACKLIST REDIRECTPARAM
        String[] fields = line.split("\\t");
        if (fields.length < 8) {
            log.warn("Invalid CSV filter definition: not enough fields [{}]", line);
            return null;
        }

        FilterType type = FilterType.fromName(fields[0]);
        if (type == null) {
            log.warn("Invalid CSV filter definition: no or invalid type {} [{}]", fields[0], line);
            return null;
        }

        FilterPriority priority = FilterPriority.fromName(fields[1]);
        if (priority == null) {
            log.warn("Invalid CSV filter definition: no or invalid priority {} [{}]", fields[1], line);
            return null;
        }

        String domain = (isEmpty(fields[2]) ? null : fields[2]);

        UrlFilterFactory filterFactory = UrlFilterFactory.getInstance()
            .setDefinition(line)
            .setType(type)
            .setPriority(priority)
            .setDomain(domain);

        StringMatchType matchType = StringMatchType.fromName(fields[3]);
        if (matchType == null) {
            log.warn("Invalid CSV filter definition: no or invalid substring matchtype {} [{}]", fields[3], line);
            return null;
        }
        filterFactory.setStringMatchType(matchType)
            .setMatchString(fields[4]);

        if (!isEmpty(fields[5])) {
            filterFactory.setReferrerDomainWhiteList(Arrays.asList(fields[5].split("\\|")));
        }

        if (!isEmpty(fields[6])) {
            filterFactory.setReferrerDomainBlackList(Arrays.asList(fields[6].split("\\|")));
        }

        if (!isEmpty(fields[7])) {
            filterFactory.setRedirectParam(fields[7]);
        }

        return filterFactory.build();

    }

    private boolean isEmpty(String field) {
        return (field == null || field.isEmpty() || EMPTY.equals(field));
    }

}
