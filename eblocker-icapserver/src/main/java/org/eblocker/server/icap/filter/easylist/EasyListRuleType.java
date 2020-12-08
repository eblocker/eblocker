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

import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.url.StringMatchType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum EasyListRuleType {
    COMMENT(null, "^!.*$"),
    TITLE(null, "^\\[.*\\]$"),

    ELEMENT_HIDE(null, "^(.*)#\\??#(.*)$"),
    ELEMENT_NOHIDE(null, "^(.*)#@#(.*)$"),

    EXCEPTION_EXACT(FilterPriority.HIGH, "^@@\\|(.*)\\|$", FilterType.PASS, "^", "$", StringMatchType.EQUALS),
    EXCEPTION_BEGIN(FilterPriority.MEDIUM, "^@@\\|(.*)$", FilterType.PASS, "^", "", StringMatchType.STARTSWITH),
    EXCEPTION_END(FilterPriority.MEDIUM, "^@@(.*)\\|?$", FilterType.PASS, "", "$", StringMatchType.ENDSWITH),
    EXCEPTION_REGEX(FilterPriority.MEDIUM, "^@@/(.*)/$", FilterType.PASS, "", "", StringMatchType.REGEX),
    EXCEPTION(FilterPriority.MEDIUM, "^@@(.*)$", FilterType.PASS, "", "", StringMatchType.CONTAINS),

    BLOCKING_EXACT(FilterPriority.HIGH, "^\\|(.*)\\|$", FilterType.BLOCK, "", "", StringMatchType.EQUALS),
    BLOCKING_BEGIN(FilterPriority.LOW, "^\\|(.*)$", FilterType.BLOCK, "^", "", StringMatchType.STARTSWITH),
    BLOCKING_END(FilterPriority.LOW, "^(.*)\\|$", FilterType.BLOCK, "", "$", StringMatchType.ENDSWITH),
    BLOCKING_REGEX(FilterPriority.LOW, "^/(.*)/$", FilterType.BLOCK, "", "", StringMatchType.REGEX),
    BLOCKING(FilterPriority.LOW, "^(.*)$", FilterType.BLOCK, "", "", StringMatchType.CONTAINS),
    ;

    private final FilterPriority priority;
    private final Pattern pattern;

    private final FilterType type;
    private final String regexPrefix;
    private final String regexPostfix;
    private final StringMatchType substringMatchType;

    private EasyListRuleType(FilterPriority priority, String regex) {
        this.priority = priority;
        this.pattern = Pattern.compile(regex);
        this.type = FilterType.PASS;
        this.regexPrefix = null;
        this.regexPostfix = null;
        this.substringMatchType = null;
    }

    private EasyListRuleType(FilterPriority priority, String regex, FilterType type, String regexPrefix, String regexPostfix, StringMatchType substringMatchType) {
        this.priority = priority;
        this.pattern = Pattern.compile(regex);
        this.type = type;
        this.regexPrefix = regexPrefix;
        this.regexPostfix = regexPostfix;
        this.substringMatchType = substringMatchType;
    }

    public FilterPriority getPriority() {
        return priority;
    }

    public Matcher matcher(String definition) {
        return pattern.matcher(definition);
    }

    public FilterType getType() {
        return type;
    }

    public String getRegexPrefix() {
        return regexPrefix;
    }

    public String getRegexPostfix() {
        return regexPostfix;
    }

    public StringMatchType getSubstringMatchType() {
        return substringMatchType;
    }

    public boolean isExplicitRegularExpression() {
        return this == EXCEPTION_REGEX || this == BLOCKING_REGEX;
    }
}
