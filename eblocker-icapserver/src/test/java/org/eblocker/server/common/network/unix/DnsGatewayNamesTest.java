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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.dns.DnsRecordType;
import org.eblocker.server.common.data.dns.DnsResponse;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DnsGatewayNamesTest {

    private final String gatewayNames = "magnificent.box, bread.box, nodata.box";

    private DataSource dataSource;
    private DnsResolver dnsResolver;
    private EblockerDnsServer dnsServer;
    private DnsGatewayNames dnsGatewayNames;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getGateway()).thenReturn("192.168.1.1");

        dnsResolver = Mockito.mock(DnsResolver.class);
        Mockito.when(dnsResolver.resolve(Mockito.anyString(), Mockito.anyList())).thenReturn(null);

        dnsServer = Mockito.mock(EblockerDnsServer.class);

        dnsGatewayNames = new DnsGatewayNames(gatewayNames, dataSource, dnsResolver, dnsServer);
    }

    @Test
    public void testNoResults() {
        dnsGatewayNames.check();

        Mockito.verify(dnsResolver).resolve(Mockito.anyString(), Mockito.anyList());
        Mockito.verifyZeroInteractions(dnsServer);
        Mockito.verify(dataSource, Mockito.never()).setResolvedDnsGateway(Mockito.anyString());
    }

    @Test
    public void testGatewayNamesNew() {
        Mockito.when(dnsServer.getLocalDnsRecords()).thenReturn(Arrays.asList(
                new LocalDnsRecord("test.com", false, false, Ip4Address.parse("1.2.3.4"), null, null, null)));
        Mockito.when(dnsResolver.resolve(Mockito.eq("192.168.1.1"), Mockito.anyList()))
                .thenReturn(Arrays.asList(
                        new DnsResponse(0, DnsRecordType.A, IpAddress.parse("192.168.1.1"), "magnificent.box"),
                        new DnsResponse(0, DnsRecordType.AAAA, IpAddress.parse("fe80::192:168:1:1"), "magnificent.box"),
                        new DnsResponse(3),
                        new DnsResponse(3),
                        new DnsResponse(3),
                        new DnsResponse(3)));

        dnsGatewayNames.check();

        Mockito.verify(dataSource).setResolvedDnsGateway("192.168.1.1");

        ArgumentCaptor<List<LocalDnsRecord>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(dnsServer).setLocalDnsRecords(captor.capture());

        Map<String, LocalDnsRecord> recordsByName = captor.getValue().stream().collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        Assert.assertEquals(2, recordsByName.size());
        Assert.assertNotNull(recordsByName.get("magnificent.box"));
        Assert.assertEquals(Ip4Address.parse("192.168.1.1"), recordsByName.get("magnificent.box").getIpAddress());
        Assert.assertEquals(Ip6Address.parse("fe80::192:168:1:1"), recordsByName.get("magnificent.box").getIp6Address());
        Assert.assertNotNull(recordsByName.get("test.com"));
        Assert.assertEquals(Ip4Address.parse("1.2.3.4"), recordsByName.get("test.com").getIpAddress());
    }

    @Test
    public void testGatewayNamesUpdated() {
        Mockito.when(dnsServer.getLocalDnsRecords()).thenReturn(Arrays.asList(
                new LocalDnsRecord("test.com", false, false, Ip4Address.parse("1.2.3.4"), null, null, null),
                new LocalDnsRecord("magnificent.box", false, false, Ip4Address.parse("192.168.2.1"), null, null, null)));
        Mockito.when(dnsResolver.resolve(Mockito.eq("192.168.1.1"), Mockito.anyList()))
                .thenReturn(Arrays.asList(
                        new DnsResponse(0, DnsRecordType.A, IpAddress.parse("192.168.1.1"), "magnificent.box"),
                        new DnsResponse(0, DnsRecordType.AAAA, IpAddress.parse("fe80::192:168:1:1"), "magnificent.box"),
                        new DnsResponse(3),
                        new DnsResponse(3),
                        new DnsResponse(3),
                        new DnsResponse(3)));

        dnsGatewayNames.check();

        Mockito.verify(dataSource).setResolvedDnsGateway("192.168.1.1");

        ArgumentCaptor<List<LocalDnsRecord>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(dnsServer).setLocalDnsRecords(captor.capture());

        Map<String, LocalDnsRecord> recordsByName = captor.getValue().stream().collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        Assert.assertEquals(2, recordsByName.size());
        Assert.assertNotNull(recordsByName.get("magnificent.box"));
        Assert.assertEquals(Ip4Address.parse("192.168.1.1"), recordsByName.get("magnificent.box").getIpAddress());
        Assert.assertEquals(Ip6Address.parse("fe80::192:168:1:1"), recordsByName.get("magnificent.box").getIp6Address());
        Assert.assertNotNull(recordsByName.get("test.com"));
        Assert.assertEquals(Ip4Address.parse("1.2.3.4"), recordsByName.get("test.com").getIpAddress());
    }

    @Test
    public void testGatewayNamesRemoved() {
        Mockito.when(dnsServer.getLocalDnsRecords()).thenReturn(Arrays.asList(
                new LocalDnsRecord("test.com", false, false, Ip4Address.parse("1.2.3.4"), null, null, null),
                new LocalDnsRecord("magnificent.box", false, false, Ip4Address.parse("192.168.2.1"), null, null, null),
                new LocalDnsRecord("bread.box", false, false, Ip4Address.parse("2.3.4.5"), null, null, null)));
        Mockito.when(dnsResolver.resolve(Mockito.eq("192.168.1.1"), Mockito.anyList()))
                .thenReturn(Arrays.asList(
                        new DnsResponse(0, null, null, null),
                        new DnsResponse(0, null, null, null),
                        new DnsResponse(3),
                        new DnsResponse(3),
                        new DnsResponse(0, null, null, null),
                        new DnsResponse(0, null, null, null)));

        dnsGatewayNames.check();

        Mockito.verify(dataSource).setResolvedDnsGateway("192.168.1.1");

        ArgumentCaptor<List<LocalDnsRecord>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(dnsServer).setLocalDnsRecords(captor.capture());

        Map<String, LocalDnsRecord> recordsByName = captor.getValue().stream().collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        Assert.assertEquals(1, recordsByName.size());
        Assert.assertNotNull(recordsByName.get("test.com"));
        Assert.assertEquals(Ip4Address.parse("1.2.3.4"), recordsByName.get("test.com").getIpAddress());
    }

    @Test
    public void testTooFewResults() {
        Mockito.when(dnsResolver.resolve(Mockito.eq("192.168.1.1"), Mockito.anyList()))
                .thenReturn(Arrays.asList(new DnsResponse(3)));

        dnsGatewayNames.check();

        Mockito.verify(dnsResolver).resolve(Mockito.anyString(), Mockito.anyList());
        Mockito.verifyZeroInteractions(dnsServer);
        Mockito.verify(dataSource, Mockito.never()).setResolvedDnsGateway(Mockito.anyString());
    }

    @Test
    public void testNoResponse() {
        Mockito.when(dnsResolver.resolve(Mockito.eq("192.168.1.1"), Mockito.anyList()))
                .thenReturn(Arrays.asList(new DnsResponse(), new DnsResponse()));

        dnsGatewayNames.check();

        Mockito.verify(dnsResolver).resolve(Mockito.anyString(), Mockito.anyList());
        Mockito.verifyZeroInteractions(dnsServer);
        Mockito.verify(dataSource, Mockito.never()).setResolvedDnsGateway(Mockito.anyString());
    }

    @Test
    public void testGatewayUnchanged() {
        Mockito.when(dataSource.getResolvedDnsGateway()).thenReturn("192.168.1.1");

        dnsGatewayNames.check();

        Mockito.verifyZeroInteractions(dnsServer);
        Mockito.verify(dataSource, Mockito.never()).setResolvedDnsGateway(Mockito.anyString());
    }
}
