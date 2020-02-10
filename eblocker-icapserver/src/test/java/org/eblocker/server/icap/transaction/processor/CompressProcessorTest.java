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
package org.eblocker.server.icap.transaction.processor;

import ch.mimo.netty.handler.codec.icap.DefaultIcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapMethod;
import ch.mimo.netty.handler.codec.icap.IcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapVersion;
import org.eblocker.server.common.data.CompressionMode;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.ContentEncoding;
import org.eblocker.server.icap.transaction.Transaction;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class CompressProcessorTest {
    private static byte[] UNCOMPRESSED;
    private static StringBuilder UNCOMPRESSED_CONTENT;

    private DeviceService deviceService;
    private FeatureServiceSubscriber featureService;
    private CompressProcessor processor;

    private Session session;

    @BeforeClass
    public static void beforeClass() throws IOException {
        UNCOMPRESSED = loadResource("test-data/compressed/html");
        UNCOMPRESSED_CONTENT = new StringBuilder(new String(UNCOMPRESSED, StandardCharsets.ISO_8859_1));
    }

    @Before
    public void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        featureService = Mockito.mock(FeatureServiceSubscriber.class);
        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.ALWAYS);
        processor = new CompressProcessor(deviceService, featureService);

        session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn("device:1234");
    }

    @Test
    public void testGzipCompression() throws IOException {
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.GZIP, "gzip");

        processor.process(transaction);

        GZIPInputStream in = new GZIPInputStream(new ByteBufInputStream(transaction.getResponse().content()));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testDeflateCompression() throws IOException {
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.DEFLATE, "deflate");

        processor.process(transaction);

        InflaterInputStream in = new InflaterInputStream(new ByteBufInputStream(transaction.getResponse().content()));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testDeflateNoWrapCompression() throws IOException {
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.DEFLATE_NO_WRAP, "deflate");

        processor.process(transaction);

        InflaterInputStream in = new InflaterInputStream(new ByteBufInputStream(transaction.getResponse().content()), new Inflater(true));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testNoCompression() {
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.NONE, null);

        processor.process(transaction);

        byte[] uncompressedResponse = toBytes(transaction.getResponse().content());

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testUnknownCompression() {
        Transaction transaction = makeTransaction(null, ContentEncoding.UNKNOWN, "abc");

        FullHttpResponse response = transaction.getResponse().copy();
        processor.process(transaction);

        Assert.assertEquals(response, transaction.getResponse());
    }

    @Test
    public void testBrotliCompression() {
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.BROTLI, "abc");

        processor.process(transaction);
        byte[] uncompressedResponse = toBytes(transaction.getResponse().content());

        Assert.assertFalse(transaction.getResponse().headers().contains("Content-Encoding"));
        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testBrotliRecompressionGZIP() throws IOException {
        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.ALWAYS);
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.BROTLI, "abc");
        transaction.getRequest().headers().set("Accept-Encoding", "x-bzip, gzip, deflate");

        processor.process(transaction);

        InputStream in = new GZIPInputStream(new ByteBufInputStream(transaction.getResponse().content()));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testBrotliRecompressionDeflate() throws IOException {
        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.ALWAYS);
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.BROTLI, "abc");
        transaction.getRequest().headers().set("Accept-Encoding", "x-bzip, deflate, gzip");

        processor.process(transaction);

        InputStream in = new InflaterInputStream(new ByteBufInputStream(transaction.getResponse().content()));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testCompressionOff() {
        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.OFF);
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.GZIP, "abc");

        processor.process(transaction);

        byte[] uncompressedResponse = toBytes(transaction.getResponse().content());

        Assert.assertFalse(transaction.getResponse().headers().contains("Content-Encoding"));
        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testCompressionVpnClientsOnlyVpnClient() throws IOException {
        Device device = new Device();
        device.setIsVpnClient(true);
        Mockito.when(deviceService.getDeviceById("device:1234")).thenReturn(device);

        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.VPN_CLIENTS_ONLY);
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.GZIP, "abc");

        processor.process(transaction);

        GZIPInputStream in = new GZIPInputStream(new ByteBufInputStream(transaction.getResponse().content()));
        byte[] uncompressedResponse = ByteStreams.toByteArray(in);

        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    @Test
    public void testCompressionVpnClientsOnlyNoneVpnClient() throws IOException {
        Mockito.when(deviceService.getDeviceById("device:1234")).thenReturn(new Device());
        Mockito.when(featureService.getCompressionMode()).thenReturn(CompressionMode.VPN_CLIENTS_ONLY);
        Transaction transaction = makeTransaction(UNCOMPRESSED_CONTENT, ContentEncoding.GZIP, "abc");

        processor.process(transaction);

        byte[] uncompressedResponse = toBytes(transaction.getResponse().content());

        Assert.assertFalse(transaction.getResponse().headers().contains("Content-Encoding"));
        Assert.assertArrayEquals(UNCOMPRESSED, uncompressedResponse);
    }

    private Transaction makeTransaction(StringBuilder content, ContentEncoding contentEncoding, String contentEncodingHeader) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, "/some/path", "myhost");

        Transaction transaction = new IcapTransaction(request);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE,"text/html");

        if (contentEncodingHeader != null) {
            httpResponse.headers().add("Content-Encoding", contentEncodingHeader);
        }

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/some/path");
        transaction.setRequest(httpRequest);
        transaction.setResponse(httpResponse);
        transaction.setContentEncoding(contentEncoding);
        transaction.setContent(content);

        transaction.setSession(session);

        return transaction;
    }

    private static byte[] loadResource(String resource) throws IOException {
        return ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream(resource));
    }

    private byte[] toBytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        return bytes;
    }
}
