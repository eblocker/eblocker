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
package org.eblocker.server.common.ssl;

import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.server.SSLContextHandler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.security.cert.X509Certificate;

@Singleton
@SubSystemService(SubSystem.HTTPS_SERVER)
public class SslTestListeners {
    private static final Logger log = LoggerFactory.getLogger(SslTestListeners.class);

    private final int currentPort;
    private final int renewalPort;
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private final SSLContextHandler sslContextHandler;
    private final SslTestRequestHandlerFactory handlerFactory;
    private final SslService sslService;

    private ChannelFuture currentChannelFuture;
    private ChannelFuture renewalChannelFuture;

    @Inject
    public SslTestListeners(@Named("ssl.test.ca.current.endpoint.port") int currentPort,
                            @Named("ssl.test.ca.renewal.endpoint.port") int renewalPort,
                            @Named("nettyBossEventGroupLoop") NioEventLoopGroup bossGroup,
                            @Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup,
                            SSLContextHandler sslContextHandler,
                            SslService sslService,
                            SslTestRequestHandlerFactory handlerFactory) {
        this.currentPort = currentPort;
        this.renewalPort = renewalPort;
        this.sslContextHandler = sslContextHandler;
        this.sslService = sslService;
        this.handlerFactory = handlerFactory;
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;

        sslContextHandler.addContextChangeListener(new SSLContextHandler.SslContextChangeListener() {
            @Override
            public void onEnable() {
                stopListeners();
                setupListeners();
            }

            @Override
            public void onDisable() {
                stopListeners();
            }
        });
    }

    private void setupListeners() {
        if (sslService.isCaAvailable()) {
            log.debug("current ca available, adding listener on port {}", currentPort);
            currentChannelFuture = configureStart(currentPort, sslService.getCa().getCertificate(), sslContextHandler.getSSLContext());
        }

        if (sslService.isRenewalCaAvailable()) {
            log.debug("renewal ca available, adding listener on port {}", renewalPort);
            renewalChannelFuture = configureStart(renewalPort, sslService.getRenewalCa().getCertificate(), sslContextHandler.getRenewalSSLContext());
        }
    }

    private ChannelFuture configureStart(int port, X509Certificate certificate, SSLContext sslContext) {
        ChannelHandler handler = handlerFactory.create(certificate.getSerialNumber());
        SslContext nettySslContext = new JdkSslContext(sslContext, false, ClientAuth.NONE);
        ChannelFuture channelFuture = new ServerBootstrap()
            .channel(NioServerSocketChannel.class)
            .localAddress(port)
            .group(bossGroup, workerGroup)
            .childHandler(new ChannelInitializer() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline()
                        .addLast("ssl", nettySslContext.newHandler(ch.alloc()))
                        .addLast("http-codec", new HttpServerCodec())
                        .addLast("ssl-test", handler);
                }
            })
            .bind().awaitUninterruptibly();
        log.debug("configured and started listener for certificate with {}", certificate.getSerialNumber());
        return channelFuture;
    }

    private void stopListeners() {
        log.debug("stopping listener(s)");
        if (currentChannelFuture != null) {
            currentChannelFuture.channel().close().awaitUninterruptibly();
        }
        if (renewalChannelFuture != null) {
            renewalChannelFuture.channel().close().awaitUninterruptibly();
        }
    }
}
