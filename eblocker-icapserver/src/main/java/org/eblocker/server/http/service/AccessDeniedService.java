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
package org.eblocker.server.http.service;

import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.GeneratingKeyManager;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, initPriority = -1)
public class AccessDeniedService {
    private static final Logger log = LoggerFactory.getLogger(AccessDeniedService.class);

    private final String ip;
    private final int port;
    private final int sslPort;
    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final int keyManagerCacheMaxSize;
    private final int keyManagerCacheConcurrencyLevel;
    private final List<String> keyManagerDefaultNames;
    private final ChannelHandler requestHandler;
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private final NetworkInterfaceAliases networkInterfaceAliases;
    private final SslService sslService;

    private KeyPair keyPair;
    private ChannelFuture httpChannelFuture;
    private ChannelFuture httpsChannelFuture;

    @Inject
    public AccessDeniedService(@Named("parentalControl.redirect.ip") String ip,
                               @Named("parentalControl.redirect.http.port") int port,
                               @Named("parentalControl.redirect.https.port") int sslPort,
                               @Named("parentalControl.redirect.keyStore.path") String keyStorePath,
                               @Named("parentalControl.redirect.keyStore.password") String keyStorePassword,
                               @Named("parentalControl.redirect.keyManager.cache.maxSize") int keyManagerCacheMaxSize,
                               @Named("parentalControl.redirect.keyManager.cache.concurrencyLevel") int keyManagerCacheConcurrencyLevel,
                               @Named("parentalControl.redirect.keyManager.default.names") String keyManagerDefaultNames,
                               @Named("nettyBossEventGroupLoop") NioEventLoopGroup bossGroup,
                               @Named("nettyWorkerEventGroupLoop") NioEventLoopGroup workerGroup,
                               NetworkInterfaceAliases networkInterfaceAliases,
                               @Named("AccessDeniedRequestHandler") ChannelHandler requestHandler,
                               SslService sslService) {
        this.ip = ip;
        this.port = port;
        this.sslPort = sslPort;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.keyManagerCacheMaxSize = keyManagerCacheMaxSize;
        this.keyManagerCacheConcurrencyLevel = keyManagerCacheConcurrencyLevel;
        this.keyManagerDefaultNames = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(keyManagerDefaultNames);
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
        this.networkInterfaceAliases = networkInterfaceAliases;
        this.requestHandler = requestHandler;
        this.sslService = sslService;
    }

    @SubSystemInit
    public void init() {
        networkInterfaceAliases.add(ip, "255.255.255.255");

        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
        AddAttributeHandler addHttpSchemeHandler = new AddAttributeHandler(Collections.singletonMap(
            AccessDeniedRequestHandler.SCHEME_KEY, "http"));
        httpChannelFuture = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .localAddress(port)
            .childOption(ChannelOption.SO_REUSEADDR, true)
            .childHandler(new ChannelInitializer() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline()
                        .addLast("http-codec", new HttpServerCodec(8192, 8192, 8192))
                        .addLast("scheme-attribute", addHttpSchemeHandler)
                        .addLast("handler", requestHandler);
                }
            })
            .bind().awaitUninterruptibly();

        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                setupSslListener();
            }

            @Override
            public void onCaChange() {
                setupSslListener();
            }

            @Override
            public void onEnable() {
                setupSslListener();
            }

            @Override
            public void onDisable() {
                closeSslChannel();
            }
        });
    }

    void stop() {
        httpChannelFuture.channel().close().awaitUninterruptibly();
        httpsChannelFuture.channel().close().awaitUninterruptibly();
    }

    private synchronized void closeSslChannel() {
        if (httpsChannelFuture != null) {
            httpsChannelFuture.channel().close().awaitUninterruptibly();
            httpsChannelFuture = null;
        }
    }

    private synchronized void setupSslListener() {
        closeSslChannel();
        if (sslService.isSslEnabled()) {
            try {
                initKeyPair();

                GeneratingKeyManager keyManager = new GeneratingKeyManager(sslService.getCa(), keyPair, keyManagerCacheMaxSize, keyManagerCacheConcurrencyLevel, keyManagerDefaultNames);
                SSLContext sslContext = SSLContext.getInstance("TLS");  //NOSONAR: Lesser security is acceptable here and excluding old clients should be avoided
                sslContext.init(new KeyManager[]{ keyManager }, null, null);

                SslContext nettySslContext = new JdkSslContext(sslContext, false, ClientAuth.NONE);
                AddAttributeHandler addHttpsSchemeHandler = new AddAttributeHandler(Collections.singletonMap(
                    AccessDeniedRequestHandler.SCHEME_KEY, "https"));
                httpsChannelFuture = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(sslPort)
                    .childOption(ChannelOption.SO_REUSEADDR, true)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                .addLast("ssl", nettySslContext.newHandler(ch.alloc()))
                                .addLast("sslExceptionHandler", new SslExceptionHandler())
                                .addLast("http-codec", new HttpServerCodec())
                                .addLast("scheme-attribute", addHttpsSchemeHandler)
                                .addLast("handler", requestHandler);
                        }
                    }).bind().awaitUninterruptibly();

            } catch (NoSuchAlgorithmException | KeyManagementException | IOException | CryptoException e) {
                log.error("failed to setup ssl listener", e);
            }
        }
    }

    private void initKeyPair() throws IOException, CryptoException {
        keyPair = loadKeyPair();
        if (keyPair == null) {
            keyPair = generateKeyPair();
        }
    }

    private KeyPair loadKeyPair() throws IOException, CryptoException {
        if (Files.exists(Paths.get(keyStorePath))) {
            try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                CertificateAndKey cak = PKI.loadKeyStore("parentalControlRedirectService", fis, keyStorePassword);
                if (new Date().before(cak.getCertificate().getNotAfter())) {
                    return new KeyPair(cak.getCertificate().getPublicKey(), cak.getKey());
                }
            }
        }
        return null;
    }

    private KeyPair generateKeyPair() throws CryptoException, IOException {
        // We do not really need a certificate it is just needed it to store the public key of our rsa key pair.
        // But we use it to force key rotation after some time.
        keyPair = PKI.generateRSAKeyPair(2048);
        EblockerCa ca = sslService.getCa();
        CertificateAndKey cak = ca.generateServerCertificate("parentalControlRedirectService", keyPair, ca.getCertificate().getNotAfter(), Arrays.asList("parentalControlRedirectService"));
        try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
            PKI.generateKeyStore(cak, "parentalControlRedirectService", keyStorePassword, fos);
        }
        return keyPair;
    }

    private static class SslExceptionHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof DecoderException && cause.getCause() instanceof SSLException) {
                log.debug("suppressed ssl error", cause);
                ctx.close();
            } else {
                ctx.fireExceptionCaught(cause);
            }
        }
    }

}
