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
package org.eblocker.server.common.network.unix;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.dns.DnsDataSource;
import org.eblocker.server.common.data.dns.DnsDataSourceDnsResponse;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class DnsResolver {

    private final DnsDataSource dnsDataSource;

    private final AtomicInteger nextId = new AtomicInteger();

    @Inject
    public DnsResolver(DnsDataSource dnsDataSource) {
        this.dnsDataSource = dnsDataSource;
    }

    public List<DnsResponse> resolve(String nameServer, List<DnsQuery> queries) {
        String id = String.valueOf(nextId.getAndIncrement());

        dnsDataSource.addDnsQueryQueue(id, nameServer, queries);

        DnsDataSourceDnsResponse result = dnsDataSource.popDnsResolutionQueue(id, 60);
        return result != null ? result.getResponses() : null;
    }

}
