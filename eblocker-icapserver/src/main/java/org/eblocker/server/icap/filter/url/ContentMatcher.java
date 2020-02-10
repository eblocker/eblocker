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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ContentMatcher {

    private final Pattern acceptPattern;
    private final Pattern suffixPattern;

    ContentMatcher(String[] contentTypes,
                   String[] suffixes) {
        acceptPattern = createAcceptPattern(contentTypes);
        suffixPattern = createSuffixPattern(suffixes);
    }

    boolean matches(String acceptHeader, String url) {
        if (acceptHeader != null && acceptPattern != null) {
            if (acceptPattern.matcher(acceptHeader).find()) {
                return true;
            }
        }

        if (url != null && suffixPattern != null) {
            return suffixPattern.matcher(url).matches();
        }

        return false;
    }

    private static Pattern createAcceptPattern(String... accepts) {
        if (accepts.length == 0) {
            return null;
        }

        Pattern subTypeRangePattern = Pattern.compile("(\\w+)/\\*");
        List<String> patterns = new ArrayList<>();
        for (String accept : accepts) {
            Matcher matcher = subTypeRangePattern.matcher(accept);
            if ("*/*".equals(accept)) {
                patterns.add("\\*/\\*");
                patterns.add("\\w+/(\\*|[-\\+.\\w]+)");
            } else if (matcher.matches()) {
                patterns.add(matcher.group(1) + "/(\\*|[-\\+.\\w]+)");
            } else {
                patterns.add(accept);
            }
        }
        return Pattern.compile(patterns.stream().collect(Collectors.joining("|")));
    }

    private static Pattern createSuffixPattern(String... suffixes) {
        if (suffixes.length == 0) {
            return null;
        }

        String joinedSuffixes = Arrays.asList(suffixes).stream().collect(Collectors.joining("|"));
        return Pattern.compile("[^?]*\\.(" + joinedSuffixes + ")(\\?.*)??");
    }
}
