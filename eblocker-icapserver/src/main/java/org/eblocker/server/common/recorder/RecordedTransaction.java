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
package org.eblocker.server.common.recorder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.icap.transaction.Transaction;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordedTransaction {

    private final int id;

    private final Date timestamp;

    private final String sessionId;

    private final String method;

    private final String url;

    private final String domain;

    private final String referrer;

    private final String decision;

    private final String decider;

    private final Map<String, List<String>> headers;

    public RecordedTransaction(int id, Transaction transaction) {
        this.id = id;
        this.timestamp = new Date();
        this.sessionId = transaction.getSessionId();
        this.method = transaction.getRequest().getMethod().toString();
        this.url = transaction.getUrl();
        this.domain = transaction.getDomain();
        this.referrer = transaction.getReferrer();
        this.decision = transaction.getFilterResult() == null ? "default" : transaction.getFilterResult().getDecision().name();
        this.decider = transaction.getFilterResult() == null || transaction.getFilterResult().getDecider() == null ? "default" : transaction.getFilterResult().getDecider().getDefinition();
        this.headers = new HashMap<>();
        if (transaction.getRequest() != null && transaction.getRequest().headers() != null)
            for (String name : transaction.getRequest().headers().names()) {
                headers.put(name, transaction.getRequest().headers().getAll(name));
            }
    }

    @JsonCreator
    public RecordedTransaction(
        @JsonProperty("id") int id,
        @JsonProperty("timestamp") Date timestamp,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("method") String method,
        @JsonProperty("url") String url,
        @JsonProperty("domain") String domain,
        @JsonProperty("referrer") String referrer,
        @JsonProperty("decision") String decision,
        @JsonProperty("decider") String decider,
        @JsonProperty("headers") Map<String, List<String>> headers) {
        this.id = id;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
        this.method = method;
        this.url = url;
        this.domain = domain;
        this.referrer = referrer;
        this.decision = decision;
        this.decider = decider;
        this.headers = headers;
    }

    public int getId() {
        return id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getMethod() {
        return method;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUrl() {
        return url;
    }

    public String getDomain() {
        return domain;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getDecision() {
        return decision;
    }

    public String getDecider() {
        return decider;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}
