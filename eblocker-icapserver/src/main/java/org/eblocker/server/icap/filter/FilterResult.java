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

public class FilterResult {

    public static final FilterResult NO_DECISION = new FilterResult(null, Decision.NO_DECISION);

    private final Decision decision;

    private final Filter decider;

    private final String value;

    private FilterResult(Filter decider, Decision decision) {
        this.decider = decider;
        this.decision = decision;
        this.value = null;
    }

    private FilterResult(Filter decider, Decision decision, String value) {
        this.decider = decider;
        this.decision = decision;
        this.value = value;
    }

    public static FilterResult pass(Filter filter) {
        return new FilterResult(filter, Decision.PASS);
    }

    public static FilterResult block(Filter filter) {
        return new FilterResult(filter, Decision.BLOCK);
    }

    public static FilterResult noContent(Filter filter) {
        return new FilterResult(filter, Decision.NO_CONTENT);
    }

    public static FilterResult redirect(Filter filter, String redirectTarget) {
        return new FilterResult(filter, Decision.REDIRECT, redirectTarget);
    }

    public static FilterResult ask(Filter filter, String redirectTarget) {
        return new FilterResult(filter, Decision.ASK, redirectTarget);
    }

    public static FilterResult noDecision(Filter filter) {
        return new FilterResult(filter, Decision.NO_DECISION);
    }

    public static FilterResult setCspHeader(Filter filter, String value) {
        return new FilterResult(filter, Decision.SET_CSP_HEADER, value);
    }

    public Decision getDecision() {
        return decision;
    }

    public Filter getDecider() {
        return decider;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "FilterResult[" + decision + " by " + decider + "]";
    }
}
