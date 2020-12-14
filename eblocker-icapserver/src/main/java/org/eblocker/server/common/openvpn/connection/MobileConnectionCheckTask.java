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
package org.eblocker.server.common.openvpn.connection;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import org.eblocker.registration.MobileConnectionCheck;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MobileConnectionCheckTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MobileConnectionCheckService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private final int timeout;
    private final int numberOfMessagesProtocol;
    private final int pollInterval;
    private final int pollTries;
    private final DeviceRegistrationClient deviceRegistrationClient;
    private final OpenVpnServerService openVpnServerService;

    private final int port;
    private int mappedPort;
    private final boolean udpEnabled;
    private final boolean tcpEnabled;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private List<ChannelFuture> channelFutures;

    private final byte[] secret = new byte[128];
    private final AtomicInteger udpMessagesRequested = new AtomicInteger();
    private final AtomicInteger udpMessagesReceived = new AtomicInteger();
    private final AtomicInteger tcpMessagesRequested = new AtomicInteger();
    private final AtomicInteger tcpMessagesReceived = new AtomicInteger();
    private final int numberOfMessages;
    private final Semaphore semaphore;

    private Map<String, MobileConnectionCheck> tests = new HashMap<>();

    private MobileConnectionCheckStatus.State state = MobileConnectionCheckStatus.State.NOT_STARTED;

    @Inject
    public MobileConnectionCheckTask(@Named("mobile.connection.check.timeout") int timeout,
                                     @Named("mobile.connection.check.messages") int numberOfMessagesProtocol,
                                     @Named("mobile.connection.check.result.poll.interval") int pollInterval,
                                     @Named("mobile.connection.check.result.poll.tries") int pollTries,
                                     @Named("mobile.connection.check.protocols") String protocols,
                                     @Named("openvpn.server.port") int port,
                                     OpenVpnServerService openVpnServerService,
                                     DeviceRegistrationClient deviceRegistrationClient,
                                     @Named("nettyBossEventGroupLoop") NioEventLoopGroup bossGroup,
                                     @Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup) {
        this.timeout = timeout;
        this.numberOfMessagesProtocol = numberOfMessagesProtocol;
        this.pollInterval = pollInterval * 1000;
        this.pollTries = pollTries;
        this.openVpnServerService = openVpnServerService;
        this.port = port;

        this.udpEnabled = protocols.toLowerCase().contains("udp");
        this.tcpEnabled = protocols.toLowerCase().contains("tcp");

        this.deviceRegistrationClient = deviceRegistrationClient;

        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;

        new Random().nextBytes(secret);

        int numProtocols = (udpEnabled ? 1 : 0) + (tcpEnabled ? 1 : 0);
        numberOfMessages = numberOfMessagesProtocol * numProtocols;
        semaphore = new Semaphore(numberOfMessages);
    }

    public MobileConnectionCheckStatus getStatus() {
        return new MobileConnectionCheckStatus(state, tcpMessagesRequested.get(), tcpMessagesReceived.get(), udpMessagesRequested.get(), udpMessagesReceived
            .get());
    }

    @Override
    public void run() {
        try {
            if (this.openVpnServerService.getOpenVpnPortForwardingMode() == PortForwardingMode.AUTO) {
                this.mappedPort = this.openVpnServerService.getOpenVpnTempMappedPort();
            } else {
                this.mappedPort = this.openVpnServerService.getOpenVpnMappedPort();
            }
            start();
            issueRequests();
            awaitRequests();
            stop();
            awaitResults();
        } catch (TimeoutException e) {
            state = e.getState();
            STATUS.info("Connection test timeout"); //NOSONAR
        } catch (InterruptedException e) {
            state = MobileConnectionCheckStatus.State.CANCELED;
            STATUS.info("test canceled"); //NOSONAR
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            state = MobileConnectionCheckStatus.State.ERROR;
            log.error("running test failed", e);
        } finally {
            stop();
        }
    }

    private void start() {
        log.info("starting listeners ...");
        state = MobileConnectionCheckStatus.State.PENDING_REQUESTS;

        channelFutures = new ArrayList<>(2);
        semaphore.drainPermits();
        if (udpEnabled) {
            channelFutures.add(setupUdpChannel());
        }
        if (tcpEnabled) {
            channelFutures.add(setupTcpChannel());
        }
    }

    private ChannelFuture setupUdpChannel() {
        return new Bootstrap()
            .channel(NioDatagramChannel.class)
            .group(workerGroup)
            .option(ChannelOption.SO_REUSEADDR, true)
            .handler(new UdpEchoHandler())
            .bind(port).awaitUninterruptibly();
    }

    private ChannelFuture setupTcpChannel() {
        return new ServerBootstrap()
            .channel(NioServerSocketChannel.class)
            .group(bossGroup, workerGroup)
            .childOption(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline()
                        .addLast("frame-decoder", new FixedLengthFrameDecoder(secret.length))
                        .addLast("echo", new TcpEchoHandler());
                }
            })
            .bind(port).awaitUninterruptibly();
    }

    private void issueRequests() {
        log.info("requesting {} connections", numberOfMessages);
        for (int i = 0; i < numberOfMessagesProtocol; ++i) {
            if (udpEnabled) {
                MobileConnectionCheck udpTest = deviceRegistrationClient
                    .requestMobileConnectionCheck(MobileConnectionCheck.Protocol.UDP, mappedPort, secret);
                udpMessagesRequested.incrementAndGet();
                tests.put(udpTest.getId(), udpTest);
            }
            if (tcpEnabled) {
                MobileConnectionCheck tcpTest = deviceRegistrationClient
                    .requestMobileConnectionCheck(MobileConnectionCheck.Protocol.TCP, mappedPort, secret);
                tcpMessagesRequested.incrementAndGet();
                tests.put(tcpTest.getId(), tcpTest);
            }
        }
    }

    private void awaitRequests() throws InterruptedException, TimeoutException {
        if (semaphore.tryAcquire(numberOfMessages, timeout, TimeUnit.SECONDS)) {
            log.info("received all messages ({})", numberOfMessages);
        } else {
            throw new TimeoutException("timeout waiting for messages, received " + semaphore.availablePermits() + " of " + numberOfMessages,
                MobileConnectionCheckStatus.State.TIMEOUT_REQUESTS);
        }
    }

    private void stop() {
        channelFutures.forEach(future -> future.channel().close().awaitUninterruptibly());
    }

    private void awaitResults() throws InterruptedException, TimeoutException {
        log.info("waiting for results ...");

        state = MobileConnectionCheckStatus.State.PENDING_RESULTS;

        List<MobileConnectionCheck> pendingTests = tests.values().stream()
            .filter(test -> test.getState() == MobileConnectionCheck.State.PENDING)
            .collect(Collectors.toList());

        for (int i = 0; i < pollTries; ++i) {
            log.info("fetching results for {} tests", pendingTests.size());
            List<MobileConnectionCheck> resolvedTests = pendingTests.parallelStream()
                .map(test -> deviceRegistrationClient.getMobileConnectionCheck(test.getId()))
                .filter(test -> test.getState() != MobileConnectionCheck.State.PENDING)
                .collect(Collectors.toList());
            resolvedTests.forEach(test -> tests.put(test.getId(), test));
            pendingTests.removeAll(resolvedTests);

            if (pendingTests.isEmpty()) {
                log.info("got all results");
                long successful = tests.values().stream().filter(test -> MobileConnectionCheck.State.SUCCESS == test.getState()).count();
                log.info("{} of {} tests have been successful.", successful, tests.size());
                if (tests.size() != successful) {
                    state = MobileConnectionCheckStatus.State.FAILURE;
                } else {
                    state = MobileConnectionCheckStatus.State.SUCCESS;
                }
                return;
            } else {
                log.info("still missing {} results", pendingTests.size());
                Thread.sleep(pollInterval);
            }
        }

        throw new TimeoutException("failed to retrieve all pending results", MobileConnectionCheckStatus.State.TIMEOUT_RESULTS);
    }

    private class TimeoutException extends Exception {
        private final MobileConnectionCheckStatus.State state;

        public TimeoutException(String message, MobileConnectionCheckStatus.State state) {
            super(message);
            this.state = state;
        }

        public MobileConnectionCheckStatus.State getState() {
            return state;
        }
    }

    private class TcpEchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            String remoteAddress = ((InetSocketAddress) ctx.channel().remoteAddress()).getHostString();
            int remotePort = ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();
            if (msg.equals(Unpooled.wrappedBuffer(secret))) {
                semaphore.release();
                int count = tcpMessagesReceived.incrementAndGet();
                log.info("echoed {} bytes to {}:{} ({} / {})", msg.readableBytes(),
                    remoteAddress,
                    remotePort,
                    count,
                    numberOfMessagesProtocol);
                ctx.writeAndFlush(msg.retainedDuplicate()).addListener(ChannelFutureListener.CLOSE);
            } else {
                log.warn("unexpected message from {}:{}", remoteAddress, remotePort);
                ctx.close();
            }
        }
    }

    private class UdpEchoHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            if (packet.content().equals(Unpooled.wrappedBuffer(secret))) {
                semaphore.release();
                int count = udpMessagesReceived.incrementAndGet();
                log.info("echoed {} bytes to {}:{} ({} / {})", packet.content().readableBytes(),
                    packet.sender().getHostString(),
                    packet.sender().getPort(),
                    count,
                    numberOfMessagesProtocol);
                ctx.writeAndFlush(new DatagramPacket(packet.content().retainedDuplicate(), packet.sender()));
            } else {
                log.warn("unexpected {} bytes from {}:{}", packet.content().readableBytes(), packet.sender().getHostString(), packet.sender().getPort());
            }
        }
    }
}
