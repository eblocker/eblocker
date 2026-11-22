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
package org.eblocker.server.icap.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemShutdown;
import org.eblocker.server.common.system.FatalErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Singleton
@SubSystemService(SubSystem.ICAP_SERVER)
public class EblockerIcapServer {
    private static final Logger log = LoggerFactory.getLogger(EblockerIcapServer.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private final int serverPort;
    private final ChannelInitializer pipelineFactory;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final FatalErrorHandler fatalErrorHandler;

    private ChannelFuture channelFuture;
    private volatile boolean stopping = false;

    public EblockerIcapServer(
            EblockerIcapServerChannelPipelineFactory pipelineFactory,
            int icapPort,
            NioEventLoopGroup bossGroup,
            NioEventLoopGroup workerGroup
    ) {
        this(pipelineFactory, icapPort, bossGroup, workerGroup, FatalErrorHandler.noOp());
    }

    @Inject
    public EblockerIcapServer(
            EblockerIcapServerChannelPipelineFactory pipelineFactory,
            @Named("icapPort") int icapPort,
            @Named("nettyBossEventGroupLoop") NioEventLoopGroup bossGroup,
            @Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup,
            FatalErrorHandler fatalErrorHandler
    ) {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
        this.serverPort = icapPort;
        this.pipelineFactory = pipelineFactory;
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
        this.fatalErrorHandler = fatalErrorHandler != null ? fatalErrorHandler : FatalErrorHandler.noOp();
    }

    @SubSystemInit
    public void run() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .localAddress(serverPort)
                .childHandler(pipelineFactory);

        SocketAddress address = new InetSocketAddress("0.0.0.0", serverPort);
        log.info("Binding ICAP server to address {}", address);
        channelFuture = bootstrap.bind(address).syncUninterruptibly();
        channelFuture.channel().closeFuture().addListener(future -> {
            if (!stopping) {
                fatalErrorHandler.onFatalError("ICAP server channel closed unexpectedly", future.cause());
            }
        });
        attachGroupWatchdog("ICAP Netty boss", bossGroup);
        attachGroupWatchdog("ICAP Netty worker", workerGroup);
    }

    @SubSystemShutdown
    public void shutdown() {
        stopping = true;
        if (channelFuture != null) {
            channelFuture.channel().close().awaitUninterruptibly();
        }
        STATUS.info("ICAP server stopped");
    }

    private void attachGroupWatchdog(String name, EventLoopGroup group) {
        if (group == null) {
            return;
        }
        group.terminationFuture().addListener(future -> {
            if (!stopping) {
                fatalErrorHandler.onFatalError(name + " event loop group terminated unexpectedly", future.cause());
            }
        });
        for (EventExecutor executor : group) {
            executor.terminationFuture().addListener(future -> {
                if (!stopping) {
                    fatalErrorHandler.onFatalError(name + " executor terminated unexpectedly", future.cause());
                }
            });
        }
    }
}
