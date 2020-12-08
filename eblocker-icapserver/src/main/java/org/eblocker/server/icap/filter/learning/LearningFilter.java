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
package org.eblocker.server.icap.filter.learning;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.AbstractFilter;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterDomainContainer;
import org.eblocker.server.icap.filter.FilterList;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.FilterStore;
import org.eblocker.server.icap.filter.FilterWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class LearningFilter extends AbstractFilter implements FilterDomainContainer {
    private static final Logger log = LoggerFactory.getLogger(LearningFilter.class);

    private static final FilterPriority TRUSTED_PRIO = FilterPriority.HIGH;
    private static final int TRUSTED_COUNT = 100;
    private static final int TRUSTED_COUNT_FOR_DEFAULT = 1000;
    private static final String UNDEFINED_DOMAIN = "*.*";

    @JsonProperty("map")
    private final ConcurrentMap<String, FilterList> map = new ConcurrentHashMap<>(16384, 0.75f, 1);

    private final boolean learnForAllDomains;

    public LearningFilter(String definition, boolean learnForAllDomains) {
        super(FilterPriority.HIGHEST, definition);
        this.learnForAllDomains = learnForAllDomains;
    }

    public LearningFilter(String definition, boolean learnForAllDomains, Map<String, FilterList> map) {
        super(FilterPriority.HIGHEST, definition);
        this.learnForAllDomains = learnForAllDomains;
        this.map.putAll(map);
    }

    @Override
    public FilterResult doFilter(TransactionContext context) {
        String domain = context.getDomain();
        Filter filter = get(domain, learnForAllDomains);
        if (filter == null) {
            return FilterResult.NO_DECISION;
        }
        FilterResult result = filter.filter(context);
        if (!isTrustworthy(result)) {
            result = doLearn(result, context);
        }
        return result;
    }

    public FilterResult learn(FilterResult domainResult, TransactionContext context) {
        log.debug("Learning about filter for {} --- referrer: {}", context.getUrl(), context.getReferrer());
        FilterResult result = getUnassigned().filter(context);
        if (result.getDecision() != Decision.NO_DECISION) {
            add(context.getDomain(), result.getDecider());
            log.debug("Found matching filter in queue {}", result.getDecider());
        }

        if (domainResult != null && domainResult.getDecision() != Decision.NO_DECISION && (result.getDecision() == Decision.NO_DECISION || domainResult.getDecider().getPriority().isHigher(result.getDecider().getPriority()))) {
            log.debug("Previously found rule has higher priority {}", result.getDecider());
            return domainResult;
        }

        return result;
    }

    abstract protected FilterResult doLearn(FilterResult result, TransactionContext context);

    protected boolean isTrustworthy(FilterResult result) {
        Filter filter = result.getDecider();
        if (filter == null) {
            return false;
        }
        if (filter.getPriority().isHigherOrEqual(TRUSTED_PRIO)) {
            // No need to cross check a high priority result
            return true;
        }
        if (filter.getPriority().isHigher(FilterPriority.DEFAULT)) {
            if (filter.getMatches() >= TRUSTED_COUNT) {
                // No need to cross check a frequent result
                return true;
            }
        }
        if (filter.getMatches() >= TRUSTED_COUNT_FOR_DEFAULT) {
            // No need to cross check a frequent result
            return true;
        }
        return false;
    }

    protected FilterList get(String domain) {
        return get(domain, true);
    }

    protected FilterList get(String domain, boolean createDomainEntry) {
        synchronized (map) {
            if (domain == null) {
                domain = UNDEFINED_DOMAIN;
            }
            if (!map.containsKey(domain)) {
                if (createDomainEntry) {
                    map.put(domain, new FilterList());
                } else {
                    return null;
                }
            }
        }
        return map.get(domain);
    }

    @Override
    public void add(String domain, Filter filter) {
        get(domain).add(FilterWrapper.wrap(filter));
    }

    @Override
    public FilterList remove(String domain) {
        return map.remove(domain);
    }

    /**
     * Return list of filters that are not assigned to a specific domain.
     *
     * @return
     */
    private FilterList getUnassigned() {
        return get(UNDEFINED_DOMAIN);
    }

    @Override
    public boolean resolveReferences(FilterStore store) {
        for (Map.Entry<String, FilterList> e : map.entrySet()) {
            if (!e.getValue().resolveReferences(store)) {
                map.remove(e.getKey());
            }
        }
        return true;
    }

    @Override
    public void remove(Filter filter) {
        for (FilterList filterList : map.values()) {
            filterList.remove(filter);
        }
    }

    @Override
    public void add(Filter filter) {
        add(null, filter);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(super.toString()).append(" {");
        for (Entry<String, FilterList> entry : map.entrySet()) {
            s.append("\n").append(entry.getKey()).append("  =>  ").append(entry.getValue());
        }
        s.append("\n}");
        return s.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + map.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof LearningFilter))
            return false;
        LearningFilter other = (LearningFilter) obj;
        return map.equals(other.map);
    }
}
