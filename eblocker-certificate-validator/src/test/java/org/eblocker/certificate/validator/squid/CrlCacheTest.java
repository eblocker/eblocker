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
package org.eblocker.certificate.validator.squid;

import org.eblocker.certificate.validator.http.HttpTestUtils;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilder;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.cert.CRL;
import java.security.cert.X509CRL;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Semaphore;

class CrlCacheTest {
    private static byte[] CRL_171011;
    private static byte[] CRL_171017;

    private HttpUrlConnectionBuilderFactory connectionBuilderFactory;
    private HttpUrlConnectionBuilder connectionBuilder;

    @BeforeAll
    static void beforeClass() throws IOException {
        CRL_171011 = CertificateValidatorTestUtil.loadResource("sample-certs/AmazonServerCa1b-2017-10-11.crl");
        CRL_171017 = CertificateValidatorTestUtil.loadResource("sample-certs/AmazonServerCa1b-2017-10-17.crl");
    }

    @BeforeEach
    void setUp() throws MalformedURLException {
        connectionBuilder = HttpTestUtils.createConnectionBuilderMock();
        connectionBuilderFactory = () -> connectionBuilder;
    }

    @Test
    void testCaching() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, CRL_171011);
        Mockito.when(connectionBuilder.get()).thenReturn(connection);

        CrlCache crlCache = new CrlCache(4, 1, 600000, Clock.systemUTC(), connectionBuilderFactory);
        CRL crl = crlCache.get("http://127.0.0.1:18080/ca.crl");

        Mockito.verify(connectionBuilder).setUrl("http://127.0.0.1:18080/ca.crl");
        Assertions.assertEquals(1, crlCache.size());
        Assertions.assertNotNull(crl);
        Assertions.assertInstanceOf(X509CRL.class, crl);
        Assertions.assertEquals("CN=Amazon, OU=Server CA 1B, O=Amazon, C=US", ((X509CRL) crl).getIssuerDN().getName());

        CRL crl2 = crlCache.get("http://127.0.0.1:18080/ca.crl");
        Assertions.assertSame(crl, crl2);

        Mockito.verify(connectionBuilder).get();
    }

    @Test
    void testLoadError() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(404);
        Mockito.when(connectionBuilder.get()).thenReturn(connection);

        CrlCache crlCache = new CrlCache(4, 1, 600000, Clock.systemUTC(), connectionBuilderFactory);
        Assertions.assertNull(crlCache.get("http://127.0.0.1:18080/crl2"));
    }

    @Test
    void testCacheMaxSizeEvicition() throws IOException {
        CrlCache crlCache = new CrlCache(1, 1, 600000, Clock.systemUTC(), connectionBuilderFactory);

        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, CRL_171011);
        Mockito.when(connectionBuilder.get()).thenReturn(connection);
        CRL crl0 = crlCache.get("http://127.0.0.1:18080/ca-0.crl");
        Assertions.assertEquals(1, crlCache.size());

        HttpURLConnection connection1 = HttpTestUtils.createMockResponse(200, CRL_171017);
        Mockito.when(connectionBuilder.get()).thenReturn(connection1);
        CRL crl1 = crlCache.get("http://127.0.0.1:18080/ca-1.crl");
        Assertions.assertEquals(1, crlCache.size());

        HttpURLConnection connection2 = HttpTestUtils.createMockResponse(200, CRL_171011);
        Mockito.when(connectionBuilder.get()).thenReturn(connection2);
        CRL crl2 = crlCache.get("http://127.0.0.1:18080/ca-0.crl");
        Assertions.assertEquals(1, crlCache.size());

        Assertions.assertNotSame(crl0, crl1);
        //Assert.assertTrue(crl0 != crl2); // this does not work as CertificateFactory has a cache and returns the same crl
        Assertions.assertNotSame(crl1, crl2);
    }

    @Test
    void testCacheRefresh() throws IOException {
        // avoid reloading the crl on first refresh because "Next Update" is already in the past
        Instant now = LocalDateTime.of(2017, 10, 12, 12, 0, 0).toInstant(ZoneOffset.UTC);
        TestClock clock = new TestClock(now);
        long lastModified = now.minusSeconds(3600).toEpochMilli();

        HttpURLConnection connectionInitial = HttpTestUtils.createMockResponse(200, CRL_171011);
        Mockito.when(connectionInitial.getLastModified()).thenReturn(lastModified);
        HttpURLConnection connectionIfModified = HttpTestUtils.createMockResponse(304);
        HttpURLConnection connectionModifiedCrl = HttpTestUtils.createMockResponse(200, CRL_171017);

        // load cache
        Mockito.when(connectionBuilder.get()).thenReturn(connectionInitial);
        CrlCache crlCache = new CrlCache(4, 1, 600000, clock, connectionBuilderFactory);
        CRL crl0 = crlCache.get("http://127.0.0.1:18080/ca.crl");
        Mockito.verify(connectionBuilder).setIfModifiedSince(null);
        Mockito.verify(connectionBuilder, Mockito.times(1)).get();

        // this refresh should do nothing as entries are fresh
        crlCache.refresh();

        Mockito.verify(connectionBuilder, Mockito.times(1)).get();

        // advance clock and refresh again, this time the entry has to be validated
        Mockito.when(connectionBuilder.get()).thenReturn(connectionIfModified);
        clock.setInstant(now.plusSeconds(1200000));
        crlCache.refresh();
        CRL crl1 = crlCache.get("http://127.0.0.1:18080/ca.crl");
        Assertions.assertSame(crl0, crl1);

        Mockito.verify(connectionBuilder).setIfModifiedSince(lastModified);
        Mockito.verify(connectionBuilder, Mockito.times(2)).get();

        // reset mock for validation request to return a new crl
        Mockito.when(connectionBuilder.get()).thenReturn(connectionModifiedCrl);

        // advance clock and check validation has updated the crl
        clock.setInstant(now.plusSeconds(1200000));
        crlCache.refresh();
        CRL crl2 = crlCache.get("http://127.0.0.1:18080/ca.crl");
        Mockito.verify(connectionBuilder, Mockito.times(2)).setIfModifiedSince(lastModified);
        Mockito.verify(connectionBuilder, Mockito.times(3)).get();

        Assertions.assertNotSame(crl0, crl2);
    }

    @Test
    void testCacheRefreshFailure() throws IOException {
        Instant now = LocalDateTime.of(2017, 10, 31, 10, 0).toInstant(ZoneOffset.UTC);
        TestClock clock = new TestClock(now);

        long lastModified = now.minusSeconds(3600).toEpochMilli();

        HttpURLConnection connectionInitial = HttpTestUtils.createMockResponse(200, CRL_171011);
        Mockito.when(connectionInitial.getLastModified()).thenReturn(lastModified);
        HttpURLConnection connectionIfModifiedFail = HttpTestUtils.createMockResponse(404);
        HttpURLConnection connectionReload = HttpTestUtils.createMockResponse(200, CRL_171011);

        InOrder inOrder = Mockito.inOrder(connectionBuilder);

        // load cache
        Mockito.when(connectionBuilder.get()).thenReturn(connectionInitial);
        CrlCache crlCache = new CrlCache(4, 1, 600000, clock, connectionBuilderFactory);
        crlCache.get("http://127.0.0.1:18080/ca.crl");

        inOrder.verify(connectionBuilder).get();

        // advance clock and refresh, this must validate the entry (and remote it because it fails)
        Mockito.when(connectionBuilder.get()).thenReturn(connectionIfModifiedFail);
        clock.setInstant(now.plusSeconds(1200000));
        crlCache.refresh();

        inOrder.verify(connectionBuilder).setIfModifiedSince(lastModified);
        inOrder.verify(connectionBuilder).get();
        Assertions.assertEquals(0, crlCache.size());

        // check entry is correctly reloaded
        Mockito.when(connectionBuilder.get()).thenReturn(connectionReload);
        Assertions.assertNotNull(crlCache.get("http://127.0.0.1:18080/ca.crl"));
        inOrder.verify(connectionBuilder).setIfModifiedSince(null);
        inOrder.verify(connectionBuilder).get();
    }

    @Test
    void testSerialization() throws IOException, InterruptedException {
        HttpURLConnection connection0 = HttpTestUtils.createMockResponse(200, CRL_171011);
        HttpURLConnection connection1 = HttpTestUtils.createMockResponse(200, CRL_171017);

        InOrder inOrder = Mockito.inOrder(connectionBuilder);

        CrlCache crlCache = new CrlCache(4, 1, 600000, Clock.systemUTC(), connectionBuilderFactory);
        Mockito.when(connectionBuilder.get()).thenReturn(connection0);
        CRL crl0 = crlCache.get("http://127.0.0.1:18080/ca-0.crl");
        inOrder.verify(connectionBuilder).setUrl("http://127.0.0.1:18080/ca-0.crl");
        inOrder.verify(connectionBuilder).get();
        Mockito.when(connectionBuilder.get()).thenReturn(connection1);
        CRL crl1 = crlCache.get("http://127.0.0.1:18080/ca-1.crl");
        inOrder.verify(connectionBuilder).setUrl("http://127.0.0.1:18080/ca-1.crl");
        inOrder.verify(connectionBuilder).get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        crlCache.writeToStream(baos);
        byte[] serialized = baos.toByteArray();

        // cache deserialization is asynchronous so we use a semaphore to synchronize assertions
        final Semaphore streamClosedSemaphore = new Semaphore(1);
        streamClosedSemaphore.acquire();
        InputStream serializedStream = new ByteArrayInputStream(serialized) {
            @Override
            public void close() throws IOException {
                streamClosedSemaphore.release();
            }
        };
        CrlCache crlCache2 = new CrlCache(4, 1, 600000, Clock.systemUTC(), connectionBuilderFactory, serializedStream);

        // wait until stream has been consumed
        streamClosedSemaphore.acquire();

        Assertions.assertEquals(2, crlCache2.size());
        Assertions.assertEquals(crl0, crlCache2.get("http://127.0.0.1:18080/ca-0.crl"));
        Assertions.assertEquals(crl1, crlCache2.get("http://127.0.0.1:18080/ca-1.crl"));
        inOrder.verifyNoMoreInteractions();
    }
}
