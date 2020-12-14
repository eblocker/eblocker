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
import com.google.common.io.ByteStreams;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.ContentEncoding;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DecompressProcessorTest {

    private static String UNCOMPRESSED_CONTENT;

    private DecompressProcessor processor;

    @BeforeClass
    public static void beforeClass() throws IOException {
        UNCOMPRESSED_CONTENT = new String(loadResource("test-data/compressed/html"), StandardCharsets.ISO_8859_1);
    }

    @Before
    public void setUp() {
        processor = new DecompressProcessor();
    }

    @Test
    public void gzippedContent() throws IOException {
        byte[] content = loadResource("test-data/compressed/html.gz");
        Transaction transaction = makeTransaction(content, "gzip");

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.GZIP, transaction.getContentEncoding());
        Assert.assertEquals(UNCOMPRESSED_CONTENT, transaction.getContent().toString());
    }

    @Test
    public void badGzippedContent() {
        byte[] content = "NOT GZIPPED".getBytes(StandardCharsets.ISO_8859_1);

        Transaction transaction = makeTransaction(content, "gzip");
        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.UNKNOWN, transaction.getContentEncoding());
        Assert.assertNull(transaction.getContent());
    }

    @Test
    public void deflatedContent() throws IOException {
        byte[] content = loadResource("test-data/compressed/html.deflate");
        Transaction transaction = makeTransaction(content, "deflate");

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.DEFLATE, transaction.getContentEncoding());
        Assert.assertEquals(UNCOMPRESSED_CONTENT, transaction.getContent().toString());
    }

    /*
     * Some servers send Content-Encoding: deflate, but omit the zlib wrapper (RFC 1950).
     * Example: sueddeutsche.de
     * See also: https://zoompf.com/blog/2012/02/lose-the-wait-http-compression*/

    @Test
    public void deflatedNotWrappedContent() throws IOException {
        byte[] content = loadResource("test-data/compressed/html.deflate-noWrap");
        Transaction transaction = makeTransaction(content, "deflate");

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.DEFLATE_NO_WRAP, transaction.getContentEncoding());
        Assert.assertEquals(UNCOMPRESSED_CONTENT, transaction.getContent().toString());
    }

    @Test
    public void badDeflatedContent() {
        byte[] content = "NOT DEFLATED".getBytes(StandardCharsets.ISO_8859_1);
        Transaction transaction = makeTransaction(content, "deflate");

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.UNKNOWN, transaction.getContentEncoding());
        Assert.assertNull(transaction.getContent());
    }

    @Test
    public void testNoCompression() {
        String content = "NOT COMPRESSED";
        Transaction transaction = makeTransaction(content.getBytes(StandardCharsets.ISO_8859_1), null);

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.NONE, transaction.getContentEncoding());
        Assert.assertEquals("NOT COMPRESSED", transaction.getContent().toString());
    }

    @Test
    public void testUnknown() {
        String content = "UNKNOWN";
        Transaction transaction = makeTransaction(content.getBytes(StandardCharsets.ISO_8859_1), "x-lzh");

        processor.process(transaction);

        Assert.assertEquals(ContentEncoding.UNKNOWN, transaction.getContentEncoding());
        Assert.assertNull(transaction.getContent());
    }

    private Transaction makeTransaction(byte[] content, String contentEncoding) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, "/some/path", "myhost");

        Transaction transaction = new IcapTransaction(request);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");

        if (contentEncoding != null) {
            httpResponse.headers().add("Content-Encoding", contentEncoding);
        }

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/some/path");
        transaction.setRequest(httpRequest);
        transaction.setResponse(httpResponse);

        return transaction;
    }

    private static byte[] loadResource(String resource) throws IOException {
        return ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream(resource));
    }
}
