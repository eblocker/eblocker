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
package org.eblocker.server.common.blacklist;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DomainReplaceFilter implements DomainFilter<String> {

    private final DomainFilter<String> filter;
    private final Pattern pattern;
    private final String replacement;

    public DomainReplaceFilter(DomainFilter<String> filter, String regex, String replacement) {
        this.filter = filter;
        this.pattern = Pattern.compile(regex);
        this.replacement = replacement;
    }

    @Override
    public Integer getListId() {
        return filter.getListId();
    }

    @Override
    public String getName() {
        return "(replace " + filter.getName() + ")";
    }

    @Override
    public int getSize() {
        return filter.getSize();
    }

    @Override
    public Stream<String> getDomains() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterDecision<String> isBlocked(String domain) {
        String transformedDomain = pattern.matcher(domain).replaceAll(replacement);
        FilterDecision<String> decision = filter.isBlocked(transformedDomain);
        return new FilterDecision<>(domain, decision.isBlocked(), decision.getFilter());
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.singletonList(filter);
    }
}
