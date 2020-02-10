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
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.Transaction;
import com.google.common.io.ByteStreams;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebRTCBlockerTest {
    private FeatureServiceSubscriber featureService;
    private Session session;
    private WebRTCBlocker processor;
    private Transaction transaction;

    @Before
    public void setUp() throws IOException {
        featureService = Mockito.mock(FeatureServiceSubscriber.class);
        session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn("device:1234");
        processor = new WebRTCBlocker(featureService);
    }

    @Test
    public void testWebRtcDisabled() throws IOException {
        byte[] content = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/webrtc"));
        transaction = makeTransaction(new StringBuilder(new String(content, StandardCharsets.ISO_8859_1)), false);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(false);

        assertTrue(processor.process(transaction));
    }

    @Test
    public void testWithoutRtc() throws IOException {
        byte[] content = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/no-webrtc"));
        transaction = makeTransaction(new StringBuilder(new String(content, StandardCharsets.ISO_8859_1)), false);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(true);

        assertTrue(processor.process(transaction));
    }

    @Test
    public void testWebRtc() throws IOException {
        byte[] content = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/webrtc"));
        transaction = makeTransaction(new StringBuilder(new String(content, StandardCharsets.ISO_8859_1)), false);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(true);
        String expected = new String(ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/webrtc-replaced")));

        assertTrue(processor.process(transaction));
        assertEquals(expected, new String(transaction.getContent()));

        content = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/webrtc2"));
        transaction = makeTransaction(new StringBuilder(new String(content, StandardCharsets.ISO_8859_1)), false);
        assertTrue(processor.process(transaction));
        assertEquals(expected, new String(transaction.getContent()));

        content = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/webrtc/webrtc3"));
        transaction = makeTransaction(new StringBuilder(new String(content, StandardCharsets.ISO_8859_1)), false);
        assertTrue(processor.process(transaction));
        assertEquals(expected, new String(transaction.getContent()));
    }

    @Test
    public void testWebRtcEmptyContent() throws IOException {
        transaction = makeTransaction(null, false);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(true);

        assertTrue(processor.process(transaction));
    }

    @Test
    public void testWebRtcMultipleCommandsInLine() throws IOException {
        transaction = makeTransaction(new StringBuilder("foo; new RTCPeerConnection(); bar();"), false);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(true);

        assertTrue(processor.process(transaction));
        assertEquals("foo; undefined; bar();", new String(transaction.getContent()));
    }

    @Test
    public void testWebRtcPreview() throws IOException {
        transaction = makeTransaction(null, true);
        Mockito.when(featureService.getWebRTCBlockingState()).thenReturn(true);

        assertFalse(processor.process(transaction));
    }
    
    private Transaction makeTransaction(StringBuilder content, boolean preview) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, "/some/path", "myhost");

        if (preview) {
            request.addHeader("Preview", "10");
        }

        transaction = new IcapTransaction(request);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE,"text/html");

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/some/path");
        transaction.setRequest(httpRequest);
        transaction.setResponse(httpResponse);
        transaction.setContent(content);

        transaction.setSession(session);

        return transaction;
    }
}
