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
import java.util.List;
import java.util.stream.Stream;

public class DomainFilterOr<T> implements DomainFilter<T> {

    @Nonnull
    private final List<DomainFilter<T>> filters;

    DomainFilterOr(@Nonnull List<DomainFilter<T>> filters) {
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
        StringBuilder sb = new StringBuilder("(or");
        for (DomainFilter<?> filter : filters) {
            sb.append(" ");
            sb.append(filter.getName());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int getSize() {
        return filters.stream().mapToInt(DomainFilter::getSize).sum();
    }

    @Nonnull
    @Override
    public Stream<T> getDomains() {
        return filters.stream().flatMap(DomainFilter::getDomains);
    }

    @Nonnull
    @Override
    public FilterDecision<T> isBlocked(T domain) {
        return filters.stream()
                .map(filter -> filter.isBlocked(domain))
                .filter(FilterDecision::isBlocked)
                .findFirst()
                .orElse(new FilterDecision<>(domain, false, this));
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return new ArrayList<>(filters);
    }
}
