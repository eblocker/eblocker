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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses content filters from lists in
 * <a href="https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#static-extended-filtering">uBlock Origin's format</a>.
 *
 * Not supported:
 * <ul>
 *     <li>specific-generic filters (<tt>*##...</tt>)</li>
 *     <li>conditionals</li>
 * </ul>
 */
public class ContentFilterParser {
    private boolean inConditional;
    private final Pattern linePattern = Pattern.compile("(.+?)(#@?#)(.+)");
    private final Pattern scriptletPattern = Pattern.compile("\\+js\\((.*)\\)");

    public List<ContentFilter> parse(Stream<String> lines) {
        inConditional = false;
        return lines
                .map(String::trim)
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ContentFilter parseLine(String string) {
        if (string.equals("!#endif")) {
            inConditional = false;
            return null;
        }
        if (inConditional) { // rules enclosed in conditionals are ignored
            return null;
        }
        if (string.startsWith("!#if ")) {
            inConditional = true;
            return null;
        }
        if (string.startsWith("!")) { // other comments
            return null;
        }
        Matcher m = linePattern.matcher(string);
        if (!m.matches()) {
            return null;
        }
        List<Domain> domains = parseDomains(m.group(1));
        if (domains.size() == 0) {
            return null;
        }
        ContentAction action = m.group(2).equals("##") ? ContentAction.ADD : ContentAction.REMOVE;
        String definition = m.group(3);
        Matcher scriptlet = scriptletPattern.matcher(definition);
        if (scriptlet.matches()) {
            return new ScriptletFilter(domains, action, scriptlet.group(1));
        } else {
            return new ElementHidingFilter(domains, action, definition);
        }
    }

    private List<Domain> parseDomains(String string) {
        return Arrays.stream(string.split(","))
                .filter(d -> !d.equals("*")) // ignore specific-generic filters
                .map(ContentFilterParser::createDomain)
                .collect(Collectors.toList());
    }

    private static Domain createDomain(String string) {
        return string.endsWith("*") ? new DomainEntity(string) : new Domain(string);
    }
}
