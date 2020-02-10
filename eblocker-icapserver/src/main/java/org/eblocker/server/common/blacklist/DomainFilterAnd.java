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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainFilterAnd<T> implements DomainFilter<T> {

    private final DomainFilter<T>[] filters;

    @SafeVarargs
    public DomainFilterAnd(DomainFilter<T>... filters) {
        this.filters = filters;
    }

    @Override
    public Integer getListId() {
        return null;
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("(and");
        for(DomainFilter filter : filters) {
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

    @Override
    public Stream<T> getDomains() {
        if (filters.length == 0) {
            return Stream.empty();
        }

        Set<T> domains = new HashSet<>(filters[0].getSize());
        filters[0].getDomains().forEach(domains::add);
        for (int i = 1; i < filters.length; ++i) {
            Set<T> filterDomains = filters[i].getDomains().collect(Collectors.toSet());
            domains.retainAll(filterDomains);
        }

        return domains.stream();
    }

    @Override
    public FilterDecision<T> isBlocked(T domain) {
        FilterDecision<T> decision = new FilterDecision<>(domain, false, this);
        for(DomainFilter<T> filter : filters) {
            decision = filter.isBlocked(domain);
            if (!decision.isBlocked()) {
                break;
            }
        }
        return decision;
    }

    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Stream.of(filters).collect(Collectors.toList());
    }
}
