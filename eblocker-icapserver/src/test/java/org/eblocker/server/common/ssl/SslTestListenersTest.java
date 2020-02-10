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

import org.eblocker.server.http.server.SSLContextHandler;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SslTestListenersTest {

    private static final int CURRENT_PORT = 2048;
    private static final int RENEWAL_PORT = 2049;

    private EblockerCa currentCa;
    private EblockerCa renewalCa;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private SSLContextHandler sslContextHandler;
    private SSLContextHandler.SslContextChangeListener contextListener;
    private SslService sslService;
    private SslTestRequestHandlerFactory handlerFactory;
    private SslTestListeners listeners;

    @Before
    public void setup() throws Exception {
        handlerFactory = Mockito.mock(SslTestRequestHandlerFactory.class);
        Mockito.when(handlerFactory.create(Mockito.any(BigInteger.class))).thenReturn(new HttpOkHandler());

        currentCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        renewalCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.ALTERNATIVE_CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        sslContextHandler = Mockito.mock(SSLContextHandler.class);
        Mockito.doAnswer(im -> contextListener = im.getArgument(0)).when(sslContextHandler).addContextChangeListener(Mockito.any(SSLContextHandler.SslContextChangeListener.class));
        Mockito.when(sslContextHandler.getSSLContext()).thenReturn(createSslContext(currentCa));
        Mockito.when(sslContextHandler.getRenewalSSLContext()).thenReturn(createSslContext(renewalCa));

        sslService = Mockito.mock(SslService.class);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.getCa()).thenReturn(currentCa);
        Mockito.when(sslService.isRenewalCaAvailable()).thenReturn(true);
        Mockito.when(sslService.getRenewalCa()).thenReturn(renewalCa);

        listeners = new SslTestListeners(CURRENT_PORT, RENEWAL_PORT, bossGroup, workerGroup, sslContextHandler, sslService, handlerFactory);
    }

    @After
    public void tearDown() {
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
    }

    @Test
    public void test() throws Exception {
        // check constructor registers callback and no listeners have been started
        Assert.assertNotNull(contextListener);
        Mockito.verifyZeroInteractions(handlerFactory);

        // sslcontext init callback
        contextListener.onEnable();

        checkSslTestListener(currentCa, CURRENT_PORT);
        checkSslTestListener(renewalCa, RENEWAL_PORT);

        // sslcontext disable callback
        contextListener.onDisable();

        assertListenerStopped(CURRENT_PORT);
        assertListenerStopped(RENEWAL_PORT);
    }

    private void checkSslTestListener(EblockerCa ca, int port) throws Exception {
        try (Socket socket = createSocket(ca, port)) {
            doRequest(socket);
        }
    }

    private Socket createSocket(EblockerCa ca, int port) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
        trustManagerFactory.init(PKI.generateTrustStore(new X509Certificate[]{ ca.getCertificate() }));
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("127.0.0.1", port);
        socket.startHandshake();
        return socket;
    }

    private void doRequest(Socket socket) throws IOException {
        PrintWriter writer = new PrintWriter(socket.getOutputStream());
        writer.print("GET /device:001122334455 HTTP/1.1\r\n");
        writer.print("Host: controlbar.eblocker.com\r\n");
        writer.print("\r\n");
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Assert.assertEquals("HTTP/1.1 200 OK", reader.readLine());
    }

    private void assertListenerStopped(int port) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            Assert.fail("listener not stopped " + port);
        } catch (ConnectException e) {
            //expected
        }
    }


    private SSLContext createSslContext(EblockerCa ca) throws Exception {
        CertificateAndKey cak = ca.generateServerCertificate("127.0.0.1", Date.from(ZonedDateTime.now().plusDays(1).toInstant()), Collections.emptyList());

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("test", cak.getKey(), "password".toCharArray(), new X509Certificate[] { cak.getCertificate() });

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "password".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    @ChannelHandler.Sharable
    private class HttpOkHandler extends SimpleChannelInboundHandler<HttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
            ctx.writeAndFlush(new DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.OK)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
