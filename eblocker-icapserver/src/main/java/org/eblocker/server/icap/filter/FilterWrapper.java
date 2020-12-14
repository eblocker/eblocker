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
package org.eblocker.server.icap.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.transaction.TransactionContext;

public class FilterWrapper extends AbstractFilter implements FilterContainer {

    public static Filter wrap(Filter filter) {
        return new FilterWrapper(filter);
    }

    private Filter wrapped;

    @JsonCreator
    private FilterWrapper(@JsonProperty("priority") FilterPriority priority, @JsonProperty("definition") String definition) {
        super(priority, definition);
        wrapped = null;
    }

    private FilterWrapper(Filter filter) {
        super(filter.getPriority(), filter.getDefinition());
        this.wrapped = filter;
    }

    @Override
    protected FilterResult doFilter(TransactionContext context) {
        return wrapped.filter(context);
    }

    @Override
    public String toString() {
        return getMatches() + "/" + (wrapped == null ? "<null>" : wrapped.toString());
    }

    @Override
    public boolean resolveReferences(FilterStore store) {
        wrapped = store.get(getDefinition());
        return wrapped != null;
    }

    @Override
    public void remove(Filter filter) {
        throw new EblockerException("Cannot remove reference from wrapping filter");
    }

    @Override
    public void add(Filter filter) {
        throw new EblockerException("Cannot add new reference to wrapping filter");
    }

    @JsonIgnore
    @Override
    public String getDomain() {
        return wrapped.getDomain();
    }
}
