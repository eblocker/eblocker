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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class DomainBlockingNetworkServiceTest {

    private final String HOST = "127.0.0.1";

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private DomainBlockingNetworkService networkService;

    @Before
    public void setUp() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        networkService = new DomainBlockingNetworkService(HOST, 0, bossGroup, workerGroup, new EchoHandler());
    }

    @After
    public void tearDown() {
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
    }

    @Test(timeout = 10000)
    public void testRun() throws IOException {
        networkService.init();

        // TCP
        Integer tcpPort = networkService.getTcpPort();
        Assert.assertNotNull(tcpPort);
        byte[] message = "UNIT-TEST\n".getBytes(StandardCharsets.ISO_8859_1);
        try (Socket socket = new Socket(HOST, tcpPort)) {
            socket.getOutputStream().write(message);
            byte[] response = new byte[message.length];
            ByteStreams.readFully(socket.getInputStream(), response);
            Assert.assertArrayEquals(message, response);
        }

        // UDP
        Integer udpPort = networkService.getUdpPort();
        Assert.assertNotNull(udpPort);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(message, message.length, new InetSocketAddress(HOST, udpPort)));
            DatagramPacket responsePacket = new DatagramPacket(new byte[65536], 65536);
            socket.receive(responsePacket);
            Assert.assertArrayEquals(message, Arrays.copyOf(responsePacket.getData(), responsePacket.getLength()));
        }
    }

    @Test(timeout = 10000)
    public void testStop() throws IOException {
        networkService.init();
        Integer tcpPort = networkService.getTcpPort();
        Assert.assertNotNull(tcpPort);
        Integer udpPort = networkService.getUdpPort();
        Assert.assertNotNull(udpPort);

        networkService.stop();

        // TCP
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, tcpPort));
            Assert.fail("connection must fail");
        } catch (ConnectException e) {
            // expected
        }

        // UDP
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress(HOST, udpPort));
            socket.send(new DatagramPacket(new byte[256], 256));
            socket.receive(new DatagramPacket(new byte[65536], 65536));
            Assert.fail("\"connection\" or receiving a packet must fail");
        } catch (PortUnreachableException | SocketTimeoutException e) {
            // expected
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

