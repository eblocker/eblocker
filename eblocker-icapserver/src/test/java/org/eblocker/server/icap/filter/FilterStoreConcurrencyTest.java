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

import org.eblocker.server.icap.filter.json.JSONMarshaller;
import org.eblocker.server.icap.filter.learning.AsynchronousLearningFilter;
import org.eblocker.server.icap.filter.url.StringMatchType;
import org.eblocker.server.icap.filter.url.UrlFilterFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class FilterStoreConcurrencyTest {
    private static final Logger LOG = LoggerFactory.getLogger(FilterStoreConcurrencyTest.class);

    @Test
    public void test() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

        FilterDomainContainer container = new AsynchronousLearningFilter(true);
        FilterStore store = new FilterStore(container);
        store.update(Collections.singletonList(createUrlFilter("a")));

        executor.scheduleWithFixedDelay((Runnable) container, 0L, 100L, TimeUnit.MILLISECONDS);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        final List<Exception> exceptions = new ArrayList<>();

        final int[] i = new int[]{ 0 };

        long t0 = new Date().getTime();

        Thread marshaller = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    //
                }
                for (int i = 0; i < 100; i++) {
                    try {
                        JSONMarshaller.marshall(store, out);
                    } catch (Exception e) {
                        exceptions.add(e);
                        LOG.debug("JSON run " + i);
                        return;
                    }
                }
            }
        });
        marshaller.start();

        Thread filterProcessor = new Thread(new Runnable() {
            @Override
            public void run() {
                do {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException e) {
                        //
                    }
                    store.getFilter().filter(new TestContext("http://a.i-" + (i[0]++) + ".com"));
                } while (true);
            }
        });
        filterProcessor.start();

        try {
            marshaller.join();
        } catch (InterruptedException e) {
            //
        }

        long t1 = new Date().getTime();

        if (!exceptions.isEmpty()) {
            exceptions.get(0).printStackTrace();
        }
        LOG.debug("i=" + i[0] + ", t=" + (t1 - t0));
        assertTrue(exceptions.isEmpty());
    }

    private Filter createUrlFilter(String matchString) {
        return UrlFilterFactory.getInstance()
                .setStringMatchType(StringMatchType.CONTAINS)
                .setMatchString(matchString)
                .setType(FilterType.BLOCK)
                .setDefinition("definition::" + matchString)
                .setDomain("domain.com")
                .setPriority(FilterPriority.HIGH)
                .setRedirectParam("redirectUrl")
                .build();
    }

}
