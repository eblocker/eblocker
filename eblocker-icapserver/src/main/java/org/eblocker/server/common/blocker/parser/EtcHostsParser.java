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
package org.eblocker.server.common.blocker.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class EtcHostsParser implements DomainParser {

    private static final Pattern PATTERN = Pattern.compile("^\\d+.\\d+.\\d+.\\d+\\s+(.*)$");

    @Override
    public Stream<String> parse(Stream<String> lines) {
          return lines
              .map(line -> line.replaceAll("#.*", ""))
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .map(PATTERN::matcher)
              .filter(Matcher::matches)
              .map(m -> m.group(1))
              .filter(domain -> !"localhost".equalsIgnoreCase(domain));
    }

}
