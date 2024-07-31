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
package org.eblocker.server.common.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.eblocker.server.common.data.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/*
    Class for testing DNS resolvers
 */
public class DnsTestService {
    private static final Logger log = LoggerFactory.getLogger(DnsTestService.class);

    private final Hashtable<String, Response> responses;
    private final NioEventLoopGroup workerGroup;

    public DnsTestService(String host, int port) {
        responses = new Hashtable<>();
        Bootstrap bootstrap = new Bootstrap();
        workerGroup = new NioEventLoopGroup();
        bootstrap.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel nioDatagramChannel) throws Exception {
                        nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                        nioDatagramChannel.pipeline().addLast(new DatagramDnsResponseEncoder());
                        nioDatagramChannel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
                                handleQuery(ctx, query);
                            }
                        });
                    }
                })
                .localAddress(host, port);
        log.info("Binding to {}:{}", host, port);
        bootstrap.bind().awaitUninterruptibly();
    }

    private void handleQuery(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        log.debug("Handling query {}", query);
        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        response.retain(); // because SimpleChannelInboundHandler.channelRead() calls release()
        DefaultDnsQuestion dnsQuestion = query.recordAt(DnsSection.QUESTION);
        response.addRecord(DnsSection.QUESTION, dnsQuestion);
        getAnswers(dnsQuestion, response);
        log.debug("Sending response {}", response);
        ctx.writeAndFlush(response);
    }

    public void shutdown() {
        log.info("Shutting down");
        workerGroup.shutdownGracefully().syncUninterruptibly();
    }

    public Response respondTo(String hostname) {
        return respondTo(hostname, "A");
    }

    public Response respondTo(String hostname, String type) {
        DnsQuestion question = new DefaultDnsQuestion(hostname, DnsRecordType.valueOf(type));
        Response rsp = new Response(question);
        responses.put(question.toString(), rsp);
        return rsp;
    }

    private void getAnswers(DnsQuestion question, DnsResponse response) {
        Response rsp = responses.get(question.toString());
        if (rsp != null) {
            if (rsp.isServerFailure()) {
                response.setCode(DnsResponseCode.SERVFAIL);
            } else {
                rsp.addRecords(response);
            }
        } else {
            response.setCode(DnsResponseCode.NXDOMAIN);
        }

    }

    public class Response {
        private DnsQuestion question;
        private List<DnsRecord> records;
        private final int TTL = 3600;
        private boolean serverFailure;

        public Response(DnsQuestion question) {
            this.question = question;
            this.records = new ArrayList<>();
            this.serverFailure = false;
        }

        public Response with(IpAddress address) {
            return with(question.name(), address);
        }

        public Response withCname(String cname) {
            return withCname(question.name(), cname);
        }

        public Response withCname(String hostname, String cname) {
            ByteBuf encodedName = encodeName(cname);
            records.add(new DefaultDnsRawRecord(hostname, DnsRecordType.CNAME, TTL, encodedName));
            return this;
        }

        public Response with(String hostname, IpAddress address) {
            DnsRecordType type = address.isIpv4() ? DnsRecordType.A : DnsRecordType.AAAA;
            ByteBuf encodedAddress = Unpooled.copiedBuffer(address.getAddress());
            records.add(new DefaultDnsRawRecord(hostname, type, TTL, encodedAddress));
            return this;
        }

        public void withServerFailure() {
            if (! records.isEmpty()) {
                throw new IllegalStateException("A server failure response should not contain any records");
            }
            serverFailure = true;
        }

        public boolean isServerFailure() {
            return serverFailure;
        }

        public void addRecords(DnsResponse response) {
            for (DnsRecord record: records) {
                response.addRecord(DnsSection.ANSWER, record);
            }
        }

        private ByteBuf encodeName(String name) {
            ByteBuf buffer = Unpooled.buffer(name.length() + 1);
            for (String label: name.split("\\.")) {
                if (label.length() == 0) {
                    break;
                }
                buffer.writeByte(label.length());
                ByteBufUtil.writeAscii(buffer, label);
            }
            buffer.writeByte(0); // terminate domain
            return buffer;
        }
    }

}
