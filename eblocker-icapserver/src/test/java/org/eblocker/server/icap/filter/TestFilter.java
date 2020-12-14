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

import org.eblocker.server.common.transaction.TransactionContext;

public class TestFilter extends AbstractFilter {

    private final String pattern;

    private boolean passOnMatch;

    public TestFilter(String pattern) {
        super(FilterPriority.HIGHEST, pattern);
        this.pattern = pattern;
        this.passOnMatch = false;
    }

    public TestFilter(String pattern, boolean passOnMatch) {
        super(FilterPriority.HIGHEST, pattern);
        this.pattern = pattern;
        this.passOnMatch = passOnMatch;
    }

    public TestFilter(String pattern, boolean passOnMatch, FilterPriority priority) {
        super(priority, pattern);
        this.pattern = pattern;
        this.passOnMatch = passOnMatch;
    }

    public TestFilter(TestFilter filter) {
        super(filter.getPriority(), filter.pattern);
        this.pattern = filter.pattern;
        this.passOnMatch = filter.passOnMatch;
    }

    @Override
    public FilterResult doFilter(TransactionContext context) {
        String url = context.getUrl();
        if (url.contains(pattern)) {
            if (passOnMatch) {
                return FilterResult.pass(this);
            } else {
                return FilterResult.block(this);
            }
        }
        return FilterResult.noDecision(this);
    }

    @Override
    public String toString() {
        return "TestFilter[" + pattern + "]";
    }
}
