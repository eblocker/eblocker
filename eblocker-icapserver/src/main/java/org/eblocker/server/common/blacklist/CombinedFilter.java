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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CombinedFilter<T> implements DomainFilter<T> {

    private final List<DomainFilter<T>> filters;

    public CombinedFilter(List<DomainFilter<T>> filters) {
        this.filters = filters;
    }

    @Override
    public FilterDecision<T> isBlocked(T domain) {
        return filters.parallelStream()
                .map(f -> f.isBlocked(domain))
                .filter(FilterDecision::isBlocked)
                .findAny()
                .orElse(new FilterDecision<>(domain, false, this));
    }

    @Override
    public Integer getListId() {
        return null;
    }

    @Override
    public String getName() {
        String filterNames = filters.stream().map(DomainFilter::getName).collect(Collectors.joining(" "));
        return "(combined " + filterNames + ")";
    }

    @Override
    public int getSize() {
        return filters.stream().mapToInt(DomainFilter::getSize).sum();
    }

    @Override
    public Stream<T> getDomains() {
        return filters.stream().flatMap(DomainFilter::getDomains);
    }

    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.unmodifiableList(filters);
    }
}
