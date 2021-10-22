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
import java.util.stream.Collectors;

/**
 * Filters that modify the response.
 */
public abstract class ContentFilter {
    private final List<Domain> domains;
    private final ContentAction action;
    public ContentFilter(List<Domain> domains, ContentAction action) {
        this.domains = domains;
        this.action = action;
    }

    public boolean matches(String hostname) {
        for (Domain domain: domains) {
            if (domain.matches(hostname)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the expression part of the filter definition
     * (the string after ## or #@#)
     * @return
     */
    abstract String getExpression();

    public ContentAction getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ContentFilter that = (ContentFilter) o;
        return Objects.equals(domains, that.domains) &&
                action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(domains, action);
    }

    @Override
    public String toString() {
        return domains.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) +
                (action == ContentAction.ADD ? "##" : "#@#") +
                getExpression();
    }
}
