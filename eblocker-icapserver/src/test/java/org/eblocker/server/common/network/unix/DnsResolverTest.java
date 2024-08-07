/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.dns.DnsRecordType;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsResponse;
import org.eblocker.server.common.network.DnsTestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DnsResolverTest {
    public static final String O2_BOX_IP4 = "93.184.215.14";
    public static final String O2_BOX_IP6 = "2606:2800:21f:cb07:6820:80da:af6b:8b2c";
    public static final String FRITZ_BOX_IP4 = "212.42.244.122";

    private DnsResolver resolver;
    private NioEventLoopGroup workerGroup;
    private DnsTestService dnsTestService;
    private String dnsServer = "127.0.0.1";
    private int dnsPort;

    @BeforeEach
    public void setUp() {
        workerGroup = new NioEventLoopGroup(2);
        resolver = new DnsResolver(workerGroup);
        dnsPort = 5000 + new Random().nextInt(1000);
        dnsTestService = new DnsTestService(dnsServer, dnsPort);
        dnsTestService
                .respondTo("o2.box")
                .withCname("example.com")
                .with("example.com", IpAddress.parse(O2_BOX_IP4));
        dnsTestService
                .respondTo("o2.box", "AAAA")
                .withCname("example.com")
                .with("example.com", IpAddress.parse(O2_BOX_IP6));
        dnsTestService.respondTo("fritz.box").with(IpAddress.parse(FRITZ_BOX_IP4));
        dnsTestService.respondTo("empty.response.box");
        dnsTestService.respondTo("server.failure.box").withServerFailure();
    }

    @AfterEach
    public void tearDown() {
        dnsTestService.shutdown();
    }

    @Test
    public void loadTest() {
        for (int i = 0; i < 100; i++) {
            List<DnsQuery> queries = List.of(
                    new DnsQuery(DnsRecordType.A, "o2.box"),
                    new DnsQuery(DnsRecordType.AAAA, "o2.box")
            );
            List<DnsResponse> responses = resolver.resolve(dnsServer, dnsPort, queries);
            assertEquals(2, responses.size());
            assertEquals(IpAddress.parse(O2_BOX_IP4), responses.get(0).getIpAddress());
            assertEquals(IpAddress.parse(O2_BOX_IP6), responses.get(1).getIpAddress());
        }
    }

    @Test
    public void testResolve() {
        List<DnsQuery> queries = List.of(
                new DnsQuery(DnsRecordType.A, "o2.box"),
                new DnsQuery(DnsRecordType.AAAA, "o2.box"),
                new DnsQuery(DnsRecordType.A, "fritz.box"),
                new DnsQuery(DnsRecordType.A, "notfound.box"),
                new DnsQuery(DnsRecordType.A, "server.failure.box"),
                new DnsQuery(DnsRecordType.A, "empty.response.box")
        );
        List<DnsResponse> responses = resolver.resolve(dnsServer, dnsPort, queries);

        assertEquals(queries.size(), responses.size());

        assertEquals(IpAddress.parse(O2_BOX_IP4), responses.get(0).getIpAddress());
        assertEquals(DnsRecordType.A, responses.get(0).getRecordType());

        assertEquals(IpAddress.parse(O2_BOX_IP6), responses.get(1).getIpAddress());
        assertEquals(DnsRecordType.AAAA, responses.get(1).getRecordType());

        assertEquals(IpAddress.parse(FRITZ_BOX_IP4), responses.get(2).getIpAddress());
        assertEquals(DnsRecordType.A, responses.get(2).getRecordType());

        assertEquals(Integer.valueOf(3), responses.get(3).getStatus());
        assertEquals(Integer.valueOf(3), responses.get(4).getStatus());
        assertEquals(Integer.valueOf(3), responses.get(5).getStatus());
    }

    @Test
    public void testTimeout() {
        List<DnsQuery> queries = List.of(
                new DnsQuery(DnsRecordType.A, "o2.box"),
                new DnsQuery(DnsRecordType.AAAA, "o2.box")
        );
        int invalidPort = dnsPort + 1;
        resolver.setTimeout(250);
        List<DnsResponse> responses = resolver.resolve(dnsServer, invalidPort, queries);
        assertEquals(2, responses.size());
        assertEquals(Integer.valueOf(3), responses.get(0).getStatus());
        assertEquals(Integer.valueOf(3), responses.get(1).getStatus());
    }
}
