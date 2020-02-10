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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CollectionFilter<T> implements DomainFilter<T> {

    private final Integer listId;
    private final Collection<T> collection;

    public CollectionFilter(Integer listId, Collection<T> collection) {
        this.listId = listId;
        this.collection = collection;
    }

    public Integer getListId() {
        return listId;
    }

    @Override
    public String getName() {
        return "collection-filter";
    }

    @Override
    public int getSize() {
        return collection.size();
    }

    @Override
    public Stream<T> getDomains() {
        return collection.stream();
    }

    @Override
    public FilterDecision<T> isBlocked(T domain) {
        return new FilterDecision<>(domain, collection.contains(domain), this);
    }

    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.emptyList();
    }
}
