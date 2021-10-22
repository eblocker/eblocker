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
 * Injects a scriptlet with parameters into an HTML page
 */
public class ScriptletFilter extends ContentFilter {
    private final String scriplet;
    public ScriptletFilter(List<Domain> domains, ContentAction action, String scriptlet) {
        super(domains, action);
        this.scriplet = scriptlet;
    }

    public String getScriptlet() {
        return scriplet;
    }

    @Override
    String getExpression() {
        return "+js(" + scriplet + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ScriptletFilter that = (ScriptletFilter) o;
        return Objects.equals(scriplet, that.scriplet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scriplet);
    }
}
