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

import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.SslTestUtils;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import com.google.common.io.ByteStreams;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class AccessDeniedServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(AccessDeniedServiceTest.class);

    private static final EblockerResource KEYSTORE_RESOURCE = new SimpleResource("classpath:test-data/parentalControlRedirectService.jks");
    private static final EblockerResource KEYSTORE_EXPIRED_RESOURCE = new SimpleResource("classpath:test-data/parentalControlRedirectService-expired.jks");
    private static final String IP = "127.0.0.1";
    private static final int PORT = 7778;
    private static final int SSL_PORT = 7779;
    private static final int CACHE_MAX_SIZE = 16;
    private static final int CACHE_CONCURRENCY_LEVEL = 1;
    private static final String FALLBACK_CN = "fallback_cn";

    private Path keyStorePath;
    private NetworkInterfaceAliases networkInterfaceAliases;
    private ChannelHandler handler;
    private EblockerCa eblockerCa;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private SslService sslService;

    private SslService.SslStateListener sslStateListener;

    private AccessDeniedService redirectService;

    @Before
    public void setup() throws Exception {
        keyStorePath = Files.createTempFile(AccessDeniedService.class.getSimpleName(), ".jks");
        Files.delete(keyStorePath);
        networkInterfaceAliases = Mockito.mock(NetworkInterfaceAliases.class);
        handler = new EchoHandler();

        eblockerCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        sslService = Mockito.mock(SslService.class);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        Mockito.doAnswer(im -> sslStateListener = im.getArgument(0)).when(sslService).addListener(Mockito.any(SslService.SslStateListener.class));
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);

        redirectService = new AccessDeniedService(IP, PORT, SSL_PORT, keyStorePath.toString(), SslTestUtils.UNIT_TEST_CA_PASSWORD, CACHE_MAX_SIZE, CACHE_CONCURRENCY_LEVEL, FALLBACK_CN, bossGroup, workerGroup, networkInterfaceAliases, handler, sslService);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(keyStorePath);
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).awaitUninterruptibly();
    }

    @Test
    public void testSetup() {
        // init service
        redirectService.init();

        // check alias has been created
        Mockito.verify(networkInterfaceAliases).add(IP, "255.255.255.255");

        // check plain http listener is setup
        Assert.assertTrue(checkHttpListener());

        // check ssl listener has not been started
        Assert.assertFalse(Files.exists(keyStorePath));
        Assert.assertFalse(checkHttpsListener());

        // check sslService callback has been set
        Assert.assertNotNull(sslStateListener);
    }

    private boolean checkHttpListener() {
        try (Socket socket = new Socket("127.0.0.1", PORT)) {
            checkListener(socket);
            return true;
        } catch (IOException e) {
            logger.info("http listener not running", e);
            return false;
        }
    }

    private boolean checkHttpsListener() {
        try {
            // setup client context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(PKI.generateTrustStore(new X509Certificate[]{ eblockerCa.getCertificate() }));
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("127.0.0.1", SSL_PORT);
            SSLParameters parameters = new SSLParameters();
            parameters.setServerNames(Collections.singletonList(new SNIHostName("unit.test")));
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            checkListener(socket);
            return true;
        } catch (IOException | CryptoException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            logger.info("ssl socket failed", e);
            return false;
        }
    }

    private void checkListener(Socket socket) throws IOException {
        PrintWriter writer = new PrintWriter(socket.getOutputStream());
        writer.print("GET /abc/def?key=value HTTP/1.1\r\n");
        writer.print("Host: www.google.de\r\n");
        writer.print("Accept-Encoding: deflate, gzip\r\n");
        writer.print("User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:54.0) Gecko/20100101 Firefox/54.0\r\n");
        writer.print("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n");
        writer.print("Accept-Language: en-US,en;q=0.7,de;q=0.3\r\n");
        writer.print("Cookie: abc\r\n");
        writer.print("Connection: keep-alive\r\n");
        writer.print("Upgrade-Insecure-Requests: 1\r\n");
        writer.print("\r\n");
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Assert.assertEquals("HTTP/1.1 200 OK", reader.readLine());
    }

    @Test
    public void testKeyPairGeneration() throws Exception {
        // init service
        redirectService.init();

        sslStateListener.onInit(true);

        // check key-pair has been generated
        Assert.assertTrue(Files.exists(keyStorePath));
        CertificateAndKey cak = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), SslTestUtils.UNIT_TEST_CA_PASSWORD);
        Assert.assertNotNull(cak.getCertificate());
        Assert.assertNotNull(cak.getKey());

        // check ssl listener has been setup
        Assert.assertTrue(checkHttpsListener());
    }

    @Test
    public void testKeyPairLoading() throws Exception {
        // init service
        redirectService.init();

        copyResourceToFile(KEYSTORE_RESOURCE, keyStorePath);
        sslStateListener.onInit(true);

        // check key-store has not been modified
        Assert.assertTrue(Files.exists(keyStorePath));
        CertificateAndKey expected = SslTestUtils.loadCertificateAndKey(KEYSTORE_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
        CertificateAndKey current = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), SslTestUtils.UNIT_TEST_CA_PASSWORD);
        Assert.assertEquals(expected.getKey(), current.getKey());
        Assert.assertEquals(expected.getCertificate(), current.getCertificate());
    }

    @Test
    public void testExpiredKeyPair() throws Exception {
        // init service
        redirectService.init();

        copyResourceToFile(KEYSTORE_EXPIRED_RESOURCE, keyStorePath);
        sslStateListener.onInit(true);

        // check key-store has been updated with new key
        Assert.assertTrue(Files.exists(keyStorePath));
        CertificateAndKey unexpected = SslTestUtils.loadCertificateAndKey(KEYSTORE_EXPIRED_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
        CertificateAndKey current = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), SslTestUtils.UNIT_TEST_CA_PASSWORD);
        Assert.assertNotEquals(unexpected.getKey(), current.getKey());
        Assert.assertNotEquals(unexpected.getCertificate(), current.getCertificate());
    }

    @Test
    public void testCaChange() throws Exception {
        // init service
        redirectService.init();

        sslStateListener.onInit(true);
        Assert.assertTrue(checkHttpsListener());

        eblockerCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.ALTERNATIVE_CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        sslStateListener.onCaChange();
        Assert.assertTrue(checkHttpsListener());
    }

    @Test
    public void testOnEnable() {
        // init service
        redirectService.init();

        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        sslStateListener.onInit(false);

        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        sslStateListener.onEnable();

        // check ssl listener has been setup again
        Assert.assertTrue(checkHttpsListener());
    }

    @Test
    public void testOnDisable() {
        // init service
        redirectService.init();

        sslStateListener.onInit(true);
        Assert.assertTrue(checkHttpsListener());

        sslStateListener.onDisable();

        // check ssl listener has been stopped
        Assert.assertFalse(checkHttpsListener());
    }

    private void copyResourceToFile(EblockerResource src, Path target) {
        try (InputStream is = ResourceHandler.getInputStream(src)) {
            try (FileOutputStream os = new FileOutputStream(target.toString())) {
                ByteStreams.copy(is, os);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ChannelHandler.Sharable
    private static class EchoHandler extends SimpleChannelInboundHandler<HttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
            ctx.writeAndFlush(new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
        }
    }
}
