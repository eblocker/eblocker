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
package org.eblocker.server.common.blacklist;

import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Singleton
@SubSystemService(SubSystem.SERVICES)
public class DomainBlockingNetworkService {
    private static final AttributeKey<InetSocketAddress> UDP_SENDER = AttributeKey.newInstance("udpSender");

    private final ServerBootstrap tcpBootstrap;
    private final Bootstrap udpBootstrap;
    private ChannelFuture tcpChannelFuture;
    private ChannelFuture udpChannelFuture;

    @Inject
    public DomainBlockingNetworkService(@Named("domainblacklist.networkService.host") String host,
                                        @Named("domainblacklist.networkService.port") Integer port,
                                        @Named("nettyBossEventGroupLoop") NioEventLoopGroup bossGroup,
                                        @Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup,
                                        @Named("DomainBlockingRequestHandler") ChannelHandler requestHandler) {
        ChannelInitializer channelInitializer = new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline()
                    .addLast("frameDecoder", new LineBasedFrameDecoder(512))
                    .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
                    .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
                    .addLast("handler", requestHandler);
            }
        };

        tcpBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .localAddress(host, port)
            .childHandler(channelInitializer);


        udpBootstrap = new Bootstrap()
            .group(workerGroup)
            .channel(NioDatagramChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .localAddress(host, port)
            .handler(new ChannelInitializer() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline()
                        .addLast("udp-decode", new UdpMessageDecoder())
                        .addLast("udp-encode", new UdpMessageEncoder())
                        .addLast(channelInitializer);
                }
            });
    }

    @SubSystemInit
    public void init() {
        tcpChannelFuture = tcpBootstrap.bind().awaitUninterruptibly();
        udpChannelFuture = udpBootstrap.bind().awaitUninterruptibly();
    }

    void stop() {
        ChannelFuture tcpCloseFuture = tcpChannelFuture.channel().close();
        ChannelFuture udpCloseFuture = udpChannelFuture.channel().close();
        tcpCloseFuture.awaitUninterruptibly();
        udpCloseFuture.awaitUninterruptibly();
        tcpChannelFuture = null;
        udpChannelFuture = null;
    }

    Integer getTcpPort() {
        return getPort(tcpChannelFuture);
    }

    Integer getUdpPort() {
        return getPort(udpChannelFuture);
    }

    private Integer getPort(ChannelFuture channelFuture) {
        SocketAddress localAddress = channelFuture.channel().localAddress();
        return localAddress instanceof InetSocketAddress ? ((InetSocketAddress) localAddress).getPort() : null;
    }

    private class UdpMessageDecoder extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            ctx.channel().attr(UDP_SENDER).set(msg.sender());
            ctx.fireChannelRead(msg.content().retainedDuplicate());
        }
    }

    private class UdpMessageEncoder extends MessageToMessageEncoder<ByteBuf> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            InetSocketAddress sender = ctx.channel().attr(UDP_SENDER).get();
            out.add(new DatagramPacket(msg.retainedDuplicate(), sender));
        }
    }
}
