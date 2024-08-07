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
import com.google.inject.name.Named;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsResponse;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A simple DNS resolver that can send queries to a given host and port.
 * It should only be used for queries of type A and AAAA.
 */
@Singleton
public class DnsResolver {
    private final NioEventLoopGroup workerGroup;
    private int queryTimoutMillis = 5000;

    @Inject
    public DnsResolver(@Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    /**
     * Set timeout in milliseconds
     * @param milliSeconds number of milliseconds to wait for a response
     */
    public void setTimeout(int milliSeconds) {
        queryTimoutMillis = milliSeconds;
    }

    /**
     * Resolve queries with the given name server using the standard port 53
     */
    public List<DnsResponse> resolve(String nameServer, List<DnsQuery> queries) {
        return resolve(nameServer, 53, queries);
    }

    /**
     * Resolve queries with the given name server and port
     * @param nameServer name server
     * @param port port of name server
     * @param queries list of queries. Only A and AAAA types are allowed.
     * @return list of responses. Note: the name in a response might differ from the name in the corresponding query if a CNAME is involved.
     */
    public List<DnsResponse> resolve(String nameServer, int port, List<DnsQuery> queries) {
        DnsNameResolver resolver = new DnsNameResolverBuilder(workerGroup.next())
                .channelType(NioDatagramChannel.class)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(new InetSocketAddress(nameServer, port)))
                .queryTimeoutMillis(queryTimoutMillis)
                .build();

        List<Future<List<DnsRecord>>> results = new ArrayList<>(queries.size());
        for (DnsQuery query: queries) {
            DnsQuestion question = new DefaultDnsQuestion(query.getName(), query.getRecordType());
            results.add(resolver.resolveAll(question));
        }

        List<DnsResponse> responses = new ArrayList<>(queries.size());
        for (Future<List<DnsRecord>> result: results) {
            try {
                List<DnsRecord> records = result.get();
                for (DnsRecord record: records) {
                    if (record.type() == DnsRecordType.A || record.type() == DnsRecordType.AAAA) {
                        if (record instanceof DnsRawRecord) {
                            DnsRawRecord raw = (DnsRawRecord) record;
                            byte[] addr = new byte[raw.content().readableBytes()];
                            raw.content().readBytes(addr);
                            responses.add(new DnsResponse(0, record.type(), IpAddress.of(addr), raw.name()));
                            break;
                        } else {
                            throw new RuntimeException("Expected DnsRecord of type DnsRawRecord but got: " + record.getClass());
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnknownHostException) {
                    responses.add(new DnsResponse(DnsResponseCode.NXDOMAIN.intValue()));
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
        resolver.close();
        if (responses.size() != queries.size()) {
            throw new RuntimeException("Expected " + queries.size() + " DNS responses but got " + responses.size());
        }
        return responses;
    }
}
