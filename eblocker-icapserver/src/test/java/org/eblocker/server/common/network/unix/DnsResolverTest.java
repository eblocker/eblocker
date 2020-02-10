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

import org.eblocker.server.common.data.dns.DnsDataSource;
import org.eblocker.server.common.data.dns.DnsDataSourceDnsResponse;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsRecordType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class DnsResolverTest {

    private DnsDataSource dnsDataSource;
    private DnsResolver resolver;

    @Before
    public void setUp() {
        dnsDataSource = Mockito.mock(DnsDataSource.class);
        this.resolver = new DnsResolver(dnsDataSource);
    }

    @Test
    public void testResolve() {
        DnsDataSourceDnsResponse response = new DnsDataSourceDnsResponse();
        response.setResponses(new ArrayList<>());
        Mockito.when(dnsDataSource.popDnsResolutionQueue(Mockito.anyString(), Mockito.anyInt())).thenReturn(response);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        List<DnsQuery> queries = Collections.singletonList(new DnsQuery(DnsRecordType.A, "bread.box"));
        for(int i = 0; i < 100; ++i) {
            resolver.resolve("192.168.1.1", queries);
        }

        Mockito.verify(dnsDataSource, Mockito.times(100)).addDnsQueryQueue(captor.capture(), Mockito.eq("192.168.1.1"), Mockito.eq(queries));

        List<String> ids = captor.getAllValues();
        Assert.assertEquals(100, new HashSet<>(ids).size());
    }
}
