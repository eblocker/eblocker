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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.transaction.ImmutableTransactionContext;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.FilterList;
import org.eblocker.server.icap.filter.FilterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AsynchronousLearningFilter extends LearningFilter implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AsynchronousLearningFilter.class);

    private static final String ASYNC_LEARNING_FILTER_DEF = "<<ASYNC-LEARNING-FILTER>>";

    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();

    public AsynchronousLearningFilter(@JsonProperty("learnForAllDomains") Boolean learnForAllDomains) {
        super(ASYNC_LEARNING_FILTER_DEF, learnForAllDomains == null ? true : learnForAllDomains);
    }

    @JsonCreator
    @SuppressWarnings("unused")
    public AsynchronousLearningFilter(@JsonProperty("learnForAllDomains") Boolean learnForAllDomains, @JsonProperty("map") Map<String, FilterList> map) {
        super(ASYNC_LEARNING_FILTER_DEF, learnForAllDomains == null ? true : learnForAllDomains, map);
    }

    protected FilterResult doLearn(FilterResult result, TransactionContext context) {
        if (!queue.offer(new Entry(result, new ImmutableTransactionContext(context)))) {
            log.warn("Cannot add current transaction to learning queue of filter - continuing anyway. [{}]", context);
        }
        return result;
    }

    @Override
    public void run() {
        try {
            Entry entry;
            while ((entry = queue.poll()) != null) {
                learn(entry.result, entry.context);
            }
        } catch (Exception e) {
            log.error("aborting learning due to exception", e);
        }
    }

    private class Entry {
        FilterResult result;
        TransactionContext context;

        Entry(FilterResult result, TransactionContext context) {
            this.result = result;
            this.context = context;
        }
    }
}
