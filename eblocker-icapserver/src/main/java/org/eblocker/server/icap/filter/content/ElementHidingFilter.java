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

/**
 * Hides DOM elements via CSS selector.
 */
public class ElementHidingFilter extends ContentFilter {
    private final String selector;
    public ElementHidingFilter(List<Domain> domains, ContentAction action, String selector) {
        super(domains, action);
        this.selector = selector;
    }

    public String getSelector() {
        return selector;
    }

    @Override
    String getExpression() {
        return selector;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ElementHidingFilter that = (ElementHidingFilter) o;
        return Objects.equals(selector, that.selector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), selector);
    }
}
