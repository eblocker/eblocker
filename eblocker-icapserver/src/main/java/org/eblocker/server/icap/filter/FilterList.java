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

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FilterList extends AbstractFilter implements FilterContainer {
	private static final String FILTER_LIST_DEF = "<<FILTER-LIST>>";

	@JsonProperty("list")
	private final List<Filter> filters;

	public FilterList() {
		super(FilterPriority.HIGHEST, FILTER_LIST_DEF);
        filters = new ArrayList<>();
	}

	@JsonCreator
	@SuppressWarnings("unused")
	public FilterList(@JsonProperty("list") Collection<Filter> filters) {
        super(FilterPriority.HIGHEST, FILTER_LIST_DEF);
        this.filters = new ArrayList<>(filters);
        Collections.sort(this.filters);
    }

	@Override
	public synchronized FilterResult doFilter(TransactionContext context) {
        for (Filter filter : filters) {
            FilterResult result = filter.filter(context);
            if (result.getDecision() != Decision.NO_DECISION) {
                return result;
            }
        }
		return FilterResult.NO_DECISION;
	}

	@Override
	public synchronized void add(Filter filter) {
        for(int i = 0; i < filters.size(); ++i) {
            int c = filter.compareTo(filters.get(i));
            if (c < 0) {
                filters.add(i, filter);
                return;
            } else if (c == 0 && filter.equals(filters.get(i))) {
                filters.set(i, filter);
                return;
            }
        }
	    filters.add(filter);
	}

	public synchronized int size() {
		return filters.size();
	}

	@Override
	public synchronized  int getMatches() {
		int matches = 0;
        for (Filter filter : filters) {
            matches += filter.getMatches();
        }
		return matches;
	}

	@Override
	public synchronized  String toString() {
		StringBuilder s = new StringBuilder();
		s.append(super.toString()).append(" [\n");
        for (Filter filter : filters) {
            s.append(filter).append("\n");
        }
		s.append("]");
		return s.toString();
	}

	@Override
	public synchronized int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((filters == null) ? 0 : filters.hashCode());
		return result;
	}

	@Override
	public synchronized boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof FilterList))
			return false;
		FilterList other = (FilterList) obj;
		if (filters == null) {
			if (other.filters != null)
				return false;
		} else if (!filters.equals(other.filters)) {
            return false;
        }
		return true;
	}

	@Override
	public synchronized boolean resolveReferences(FilterStore store) {
        filters.removeIf(filter -> filter instanceof FilterWrapper && !((FilterWrapper) filter).resolveReferences(store));
        return !filters.isEmpty();
	}

	@Override
	public synchronized void remove(Filter filter) {
	    filters.remove(filter);
	}

}
