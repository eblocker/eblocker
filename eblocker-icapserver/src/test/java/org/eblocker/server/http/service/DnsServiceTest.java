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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DnsServiceTest {

    private NetworkServices networkServices;
    private NetworkStateMachine networkStateMachine;
    private EblockerDnsServer dnsServer;
    private DnsStatisticsService dnsStatisticsService;

    @Before
    public void setup() {
        networkServices = Mockito.mock(NetworkServices.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        dnsStatisticsService = Mockito.mock(DnsStatisticsService.class);
    }

    @Test
    public void testSetStatusActivate() {
        NetworkConfiguration networkConfig = Mockito.mock(NetworkConfiguration.class);

        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfig);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);

        boolean result = service.setStatus(true);
        Assert.assertTrue(result);
        Mockito.verify(networkServices).getCurrentNetworkConfiguration();
        Mockito.verify(networkConfig).setDnsServer(true);
        Mockito.verify(networkStateMachine).updateConfiguration(Mockito.eq(networkConfig));
        Mockito.verify(dnsServer).isEnabled();
    }

    @Test
    public void testSetStatusDeactivate() {
        NetworkConfiguration networkConfig = Mockito.mock(NetworkConfiguration.class);

        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfig);
        Mockito.when(dnsServer.isEnabled()).thenReturn(false);

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);

        boolean result = service.setStatus(false);
        Assert.assertFalse(result);
        Mockito.verify(networkServices).getCurrentNetworkConfiguration();
        Mockito.verify(networkConfig).setDnsServer(false);
        Mockito.verify(networkStateMachine).updateConfiguration(Mockito.eq(networkConfig));
        Mockito.verify(dnsServer).isEnabled();
    }

    @Test
    public void testGetDnsResolvers() {
        DnsResolvers expected = new DnsResolvers();
        Mockito.when(dnsServer.getDnsResolvers()).thenReturn(expected);

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        DnsResolvers result = service.getDnsResolvers();

        Assert.assertEquals(expected, result);
    }

    @Test
    public void testSetDnsResolvers() {
        DnsResolvers param = new DnsResolvers();

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        service.setDnsResolvers(param);

        Mockito.verify(dnsServer).setDnsResolvers(Mockito.eq(param));
    }

    @Test
    public void testGetLocalDnsRecords() {
        List<LocalDnsRecord> recList = new ArrayList<>();
        LocalDnsRecord lRecA = new LocalDnsRecord("a", true, true, Ip4Address.parse("1.2.3.4"), null, null, null);
        LocalDnsRecord lRecB = new LocalDnsRecord("b", true, false, Ip4Address.parse("5.6.7.8"), null, null, null);
        recList.add(lRecA);
        recList.add(lRecB);

        Mockito.when(dnsServer.getLocalDnsRecords()).thenReturn(recList);

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        List<LocalDnsRecord> result = service.getLocalDnsRecords();

        Assert.assertEquals(1, result.size());
    }

    @Test
    public void testSetLocalDnsRecords() {
        List<LocalDnsRecord> recList = new ArrayList<>();

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        service.setLocalDnsRecords(recList);

        Mockito.verify(dnsServer).setLocalDnsRecords(Mockito.eq(recList));
    }

    @Test
    public void testIsEnabled() {
        Mockito.when(dnsServer.isEnabled()).thenReturn(false, true);
        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);

        Assert.assertEquals(false, service.isEnabled());
        Assert.assertEquals(true, service.isEnabled());

    }

    @Test
    public void testFlushCache() {
        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        service.flushCache();

        Mockito.verify(dnsServer).flushCache();
    }

    @Test
    public void testTestNameServers() {
        String firstServer = "first.example";
        String secondServer = "second.example";
        List<String> listNames = Arrays.asList("eblocker.org", "eblocker.org", "eblocker.org", "eblocker.org",
            "eblocker.org");
        NameServerStats firstResponse = new NameServerStats("a", 1, 2, 3, 4, 5L, 6L, 7L, 8L, null, null, null);
        NameServerStats secondResponse = new NameServerStats("aa", 11, 12, 13, 14, 15L, 16L, 17L, 18L, null, null,
            null);
        Mockito.when(dnsStatisticsService.testNameServer(firstServer, listNames)).thenReturn(firstResponse);
        Mockito.when(dnsStatisticsService.testNameServer(secondServer, listNames)).thenReturn(secondResponse);

        List<String> nameServers = Arrays.asList(firstServer, secondServer);

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        List<NameServerStats> result = service.testNameServers(nameServers);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals(Arrays.asList(firstResponse, secondResponse), result);
    }

    @Test
    public void testGetResolverStats() {
        String resolver = "resolver";
        int hours = 10;
        String lengthValue = "11";

        DnsService service = new DnsService(networkServices, networkStateMachine, dnsServer, dnsStatisticsService);
        service.getResolverStats(resolver, hours, lengthValue);

        Mockito.verify(dnsStatisticsService).getResolverStatistics(Mockito.eq(resolver), Mockito.any(Instant.class),
            Mockito.eq(Long.parseLong(lengthValue)), Mockito.eq(ChronoUnit.MINUTES));
    }

}
