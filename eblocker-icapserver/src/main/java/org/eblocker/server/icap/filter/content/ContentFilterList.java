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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A list of content filters that supports adding (##) and removing (#@#) filters.
 */
public class ContentFilterList {
    private List<ContentFilter> filters;

    public ContentFilterList(List<ContentFilter> filters) {
        this.filters = filters;
    }

    public List<ContentFilter> getMatchingFilters(String hostname) {
        List<ContentFilter> matching = filters.stream()
                .filter(f -> f.getAction() == ContentAction.ADD)
                .filter(f -> f.matches(hostname))
                .collect(Collectors.toList());

        // Removals:
        Set<String> expressionsToRemove = filters.stream()
                .filter(f -> f.getAction() == ContentAction.REMOVE)
                .filter(f -> f.matches(hostname))
                .map(f -> f.getExpression())
                .collect(Collectors.toSet());

        if (expressionsToRemove.isEmpty()) {
            return matching;
        } else {
            return matching.stream()
                    .filter(f -> !expressionsToRemove.contains(f.getExpression()))
                    .collect(Collectors.toList());
        }
    }
    public void setFilters(List<ContentFilter> filters) {
        this.filters = filters;
    }

    public int size() {
        return filters.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ContentFilterList that = (ContentFilterList) o;
        return Objects.equals(filters, that.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filters);
    }

    public static ContentFilterList emptyList() {
        return new ContentFilterList(List.of());
    }
}
