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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import org.eblocker.server.common.util.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class FilterStore {
    private final static Logger log = LoggerFactory.getLogger(FilterStore.class);

    @JsonProperty("filter")
    private final FilterDomainContainer container;

    @JsonProperty("store")
    private ConcurrentMap<String, Filter> store = new ConcurrentHashMap<>(16384, 0.75f, 1);

    @JsonProperty("lastUpdate")
    private Date lastUpdate;

    public FilterStore(FilterDomainContainer container) {
        this.container = container;
    }

    @JsonCreator
    public FilterStore(@JsonProperty("filter") FilterDomainContainer container, @JsonProperty("store") Map<String, Filter> store) {
        this.container = container;
        this.store.putAll(store);
        container.resolveReferences(this);
    }

    public synchronized void update(List<Filter> filters) {
        // add definitions
        for (Filter filter : filters) {
            store.put(filter.getDefinition(), filter);
            String domain = filter.getDomain() != null ? UrlUtils.getDomain(filter.getDomain()) : null;
            container.add(domain, filter);
        }

        // remove obsolete definitions
        Set<String> currentDefinitions = filters.stream()
                .map(Filter::getDefinition)
                .collect(Collectors.toSet());
        Set<String> deletedDefinitions = Sets.difference(store.keySet(), currentDefinitions);
        deletedDefinitions.forEach(store::remove);

        // update filters referencing old versions
        container.resolveReferences(this);

        lastUpdate = new Date();
    }

    public Filter get(String definition) {
        return store.get(definition);
    }

    public FilterDomainContainer getFilter() {
        return container;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("========================================================================\n");
        s.append("FilterStore - List of all core filters\n");
        s.append("------------------------------------------------------------------------\n");
        for (Filter filter : store.values()) {
            s.append(filter).append("\n");
        }
        s.append("------------------------------------------------------------------------\n");
        s.append("FilterStore - Effective filter\n");
        s.append("------------------------------------------------------------------------\n");
        s.append(container).append("\n");

        return s.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((container == null) ? 0 : container.hashCode());
        result = prime * result + ((store == null) ? 0 : store.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof FilterStore))
            return false;
        FilterStore other = (FilterStore) obj;
        if (container == null) {
            if (other.container != null)
                return false;
        } else if (!container.equals(other.container))
            return false;
        if (store == null) {
            if (other.store != null)
                return false;
        } else if (!store.equals(other.store))
            return false;
        return true;
    }

    public void resetLastUpdate() {
        lastUpdate = null;
    }

}
