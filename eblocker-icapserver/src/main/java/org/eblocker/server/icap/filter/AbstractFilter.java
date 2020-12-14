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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
@JsonSubTypes({
	@Type(value = RegexUrlFilter.class, name = "regex"),
	@Type(value = SubstringUrlFilter.class, name = "substring"),
	@Type(value = FilterList.class, name = "list"),
	@Type(value = FilterWrapper.class, name = "wrapper"),
	@Type(value = AsynchronousLearningFilter.class, name = "asyncLearner"),
	@Type(value = SynchronousLearningFilter.class, name = "syncLearner"),
})
*/
public abstract class AbstractFilter implements Filter {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AbstractFilter.class);

    @JsonProperty("priority")
    private final FilterPriority priority;

    @JsonProperty("definition")
    private final String definition;

    @JsonProperty("matches")
    private int matches = 0;

    public AbstractFilter(FilterPriority priority, String definition) {
        this.priority = priority;
        this.definition = definition;
    }

    @Override final public FilterResult filter(TransactionContext context) {
        FilterResult result = doFilter(context);
        if (result.getDecision() != Decision.NO_DECISION) {
            matches++;
        }
        return result;
    }

    abstract protected FilterResult doFilter(TransactionContext context);

    @Override
    public FilterPriority getPriority() {
        return priority;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @JsonIgnore
    @Override
    public String getDomain() {
        return null;
    }

    @Override
    public int getMatches() {
        return matches;
    }

    @Override
    public int compareTo(Filter other) {
        int c = this.priority.compareTo(other.getPriority());
        if (c == 0) {
            c = other.getDefinition().compareTo(this.definition);
        }
        return c;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + definition.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Filter))
            return false;
        Filter other = (Filter) obj;
        if (!definition.equals(other.getDefinition()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(matches).append("\t").append(priority).append("\t").append(definition);
        return s.toString();
    }
}
