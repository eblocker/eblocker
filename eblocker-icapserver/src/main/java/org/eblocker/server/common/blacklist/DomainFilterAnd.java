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
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainFilterAnd implements DomainFilter<String> {

    @Nonnull
    private final List<DomainFilter<String>> filters;

    DomainFilterAnd(@Nonnull List<DomainFilter<String>> filters) {
        this.filters = new ArrayList<>(filters);
    }

    @Nullable
    @Override
    public Integer getListId() {
        return null;
    }

    @Override
    @Nonnull
    public String getName() {
        StringBuilder sb = new StringBuilder("(and");
        for (DomainFilter<String> filter : filters) {
            sb.append(" ");
            sb.append(filter.getName());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int getSize() {
        return (int) getDomains().count();
    }

    @Nonnull
    @Override
    public Stream<String> getDomains() {
        if (filters.isEmpty()) {
            return Stream.empty();
        }

        Set<String> domains = new HashSet<>(filters.get(0).getSize());
        filters.get(0).getDomains().forEach(domains::add);
        for (int i = 1; i < filters.size(); ++i) {
            Set<String> filterDomains = filters.get(i).getDomains().collect(Collectors.toSet());
            domains.retainAll(filterDomains);
        }

        return domains.stream();
    }

    @Nonnull
    @Override
    public FilterDecision<String> isBlocked(String domain) {
        FilterDecision<String> decision = new FilterDecision<>(domain, false, this);
        for (DomainFilter<String> filter : filters) {
            decision = filter.isBlocked(domain);
            if (!decision.isBlocked()) {
                break;
            }
        }
        return decision;
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return new ArrayList<>(filters);
    }
}
