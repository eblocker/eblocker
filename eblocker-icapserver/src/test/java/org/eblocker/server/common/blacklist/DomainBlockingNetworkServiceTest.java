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

import com.google.common.io.ByteStreams;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainBlockingNetworkServiceTest {

    private final String HOST = "127.0.0.1";

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private DomainBlockingNetworkService networkService;

    @BeforeEach
    void setUp() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        networkService = new DomainBlockingNetworkService(HOST, 0, bossGroup, workerGroup, new EchoHandler());
    }

    @AfterEach
    void tearDown() {
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
    }

    @Test
    @Timeout(10)
    void run() throws IOException {
        networkService.init();

        // TCP
        Integer tcpPort = networkService.getTcpPort();
        assertNotNull(tcpPort);
        byte[] message = "UNIT-TEST\n".getBytes(StandardCharsets.ISO_8859_1);
        try (Socket socket = new Socket(HOST, tcpPort)) {
            socket.getOutputStream().write(message);
            byte[] response = new byte[message.length];
            ByteStreams.readFully(socket.getInputStream(), response);
            assertArrayEquals(message, response);
        }

        // UDP
        Integer udpPort = networkService.getUdpPort();
        assertNotNull(udpPort);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(message, message.length, new InetSocketAddress(HOST, udpPort)));
            DatagramPacket responsePacket = new DatagramPacket(new byte[65536], 65536);
            socket.receive(responsePacket);
            assertArrayEquals(message, Arrays.copyOf(responsePacket.getData(), responsePacket.getLength()));
        }
    }

    @ChannelHandler.Sharable
    private static class EchoHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            ctx.writeAndFlush(msg + "\n");
        }
    }
}

