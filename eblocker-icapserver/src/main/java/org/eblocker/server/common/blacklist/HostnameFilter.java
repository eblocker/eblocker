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
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

class HostnameFilter implements DomainFilter<String> {

    @Nonnull
    private final DomainFilter<String> filter;

    HostnameFilter(@Nonnull DomainFilter<String> filter) {
        this.filter = filter;
    }

    @Nonnull
    @Override
    public FilterDecision<String> isBlocked(String hostname) {
        String[] labels = hostname.split("\\.");
        if (labels.length < 2) {
            return new FilterDecision<>(hostname, false, this);
        }

        String domain = "." + labels[labels.length - 1];
        for (int i = 2; i <= labels.length; ++i) {
            domain = "." + labels[labels.length - i] + domain;
            FilterDecision<String> decision = filter.isBlocked(domain);
            if (decision.isBlocked()) {
                return decision;
            }
        }

        return filter.isBlocked(hostname);
    }

    @Nullable
    @Override
    public Integer getListId() {
        return filter.getListId();
    }

    @Nonnull
    @Override
    public String getName() {
        return "(hostname " + filter.getName() + ")";
    }

    @Override
    public int getSize() {
        return filter.getSize();
    }

    @Nonnull
    @Override
    public Stream<String> getDomains() {
        return filter.getDomains();
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.singletonList(filter);
    }
}
