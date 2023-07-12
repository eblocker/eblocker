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

import org.bouncycastle.cert.ocsp.OCSPReq;
import org.eblocker.certificate.validator.http.HttpTestUtils;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilder;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilderFactory;
import org.eblocker.crypto.CryptoException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

public class OcspCacheTest {

    private static X509Certificate[] CERTIFICATES;
    private static X509Certificate[] ISSUER_CERTIFICATES;

    private static byte[] OCSP_RESPONSE_SUCCESS_GOOD;
    private static byte[] OCSP_RESPONSE_SUCCESS_REVOKED;
    private static byte[] OCSP_RESPONSE_FAIL;

    private HttpUrlConnectionBuilder connectionBuilder;
    private HttpUrlConnectionBuilderFactory connectionBuilderFactory;

    @BeforeClass
    public static void beforeClass() throws IOException, CryptoException {
        CERTIFICATES = new X509Certificate[2];
        ISSUER_CERTIFICATES = new X509Certificate[2];
        CERTIFICATES[0] = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/revoked.badssl.com.cert");
        ISSUER_CERTIFICATES[0] = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/DigiCertSHA2SecureServerCA.cert");
        CERTIFICATES[1] = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/www.microsoft.com.cert");
        ISSUER_CERTIFICATES[1] = CertificateValidatorTestUtil.loadCertificateResource("sample-certs/MicrosoftAzureTLSIssuingCA06.cert");
        OCSP_RESPONSE_SUCCESS_GOOD = CertificateValidatorTestUtil.loadResource("sample-certs/ocsp-response-good.der");
        OCSP_RESPONSE_SUCCESS_REVOKED = CertificateValidatorTestUtil.loadResource("sample-certs/ocsp-response-revoked.der");
        OCSP_RESPONSE_FAIL = CertificateValidatorTestUtil.loadResource("sample-certs/ocsp-response-fail.der");
    }

    @Before
    public void setUp() throws MalformedURLException {
        connectionBuilder = HttpTestUtils.createConnectionBuilderMock();
        connectionBuilderFactory = () -> connectionBuilder;
    }

    @Test
    public void testCaching() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);

        OcspCache cache = new OcspCache(4, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        byte[] response = cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);
        Mockito.verify(connectionBuilder).setUrl("http://ocsp.digicert.com");
        Mockito.verify(connectionBuilder).setRequestProperty("Accept", "application/ocsp-response");
        Mockito.verify(connectionBuilder).setRequestProperty("Content-Type", "application/ocsp-request");
        Mockito.verify(connectionBuilder).post(Mockito.any(byte[].class));
        Mockito.verifyNoMoreInteractions(connectionBuilder);

        byte[] cachedResponse = cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);
        Assert.assertEquals(response, cachedResponse);
    }

    @Test
    public void testRequest() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_GOOD);
        ArgumentCaptor<byte[]> requestCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.when(connectionBuilder.post(requestCaptor.capture())).thenReturn(connection);

        OcspCache cache = new OcspCache(4, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);

        OCSPReq request = new OCSPReq(requestCaptor.getValue());
        Assert.assertEquals(1, request.getRequestList().length);
        Assert.assertEquals(CERTIFICATES[0].getSerialNumber(), request.getRequestList()[0].getCertID().getSerialNumber());
    }

    @Test
    public void testHttpResponseError() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(500);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);

        OcspCache cache = new OcspCache(4, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        Assert.assertNull(cache.getOcspResponse(CERTIFICATES[0], CERTIFICATES[1]));
    }

    @Test
    public void testConnectionError() throws IOException {
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenThrow(new IOException("unit-test"));

        OcspCache cache = new OcspCache(4, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        Assert.assertNull(cache.getOcspResponse(CERTIFICATES[0], CERTIFICATES[1]));
    }

    @Test
    public void testOcspResponseParseError() throws IOException {
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, new byte[0]);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);

        OcspCache cache = new OcspCache(4, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        Assert.assertNull(cache.getOcspResponse(CERTIFICATES[0], CERTIFICATES[1]));
    }

    @Test
    public void testMaxSizeEviction() throws IOException {
        HttpURLConnection connectionLoad = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);
        HttpURLConnection connectionEvict = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_GOOD);
        HttpURLConnection connectionReload = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);

        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connectionLoad);
        OcspCache cache = new OcspCache(1, 1, 10000, 10000, TestClock.systemUTC(), connectionBuilderFactory);
        byte[] response0 = cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[0]);
        Mockito.verify(connectionBuilder).setUrl("http://ocsp.digicert.com");
        Mockito.verify(connectionBuilder).setRequestProperty("Accept", "application/ocsp-response");
        Mockito.verify(connectionBuilder).setRequestProperty("Content-Type", "application/ocsp-request");
        Mockito.verify(connectionBuilder).post(Mockito.any(byte[].class));

        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connectionEvict);
        byte[] response1 = cache.getOcspResponse(CERTIFICATES[1], ISSUER_CERTIFICATES[1]);
        Assert.assertEquals(1, cache.size());
        Mockito.verify(connectionBuilder).setUrl("http://ocsp.digicert.com");
        Mockito.verify(connectionBuilder, Mockito.times(2)).post(Mockito.any(byte[].class));
        Assert.assertNotEquals(response0, response1);

        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connectionReload);
        byte[] response2 = cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[0]);
        Assert.assertEquals(1, cache.size());
        Mockito.verify(connectionBuilder, Mockito.times(2)).setUrl("http://ocsp.digicert.com");
        Mockito.verify(connectionBuilder, Mockito.times(3)).post(Mockito.any(byte[].class));

        Assert.assertNotEquals(response0, response1);
        Assert.assertFalse(Arrays.equals(response0, response1));
        Assert.assertNotEquals(response1, response2);
        Assert.assertFalse(Arrays.equals(response1, response2));
        Assert.assertNotEquals(response0, response2);
        Assert.assertTrue(Arrays.equals(response0, response2));
    }

    @Test
    public void testExpirationOnNextUpdate() throws IOException {
        TestClock clock = new TestClock(LocalDateTime.of(2017, 11, 8, 0, 0, 0).toInstant(ZoneOffset.UTC));

        // create cache which will not evict entries before "next update"
        OcspCache cache = new OcspCache(4, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, clock, connectionBuilderFactory);

        // load cache
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);
        cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);
        Assert.assertEquals(1, cache.size());

        // advance time, response still valid
        clock.setInstant(LocalDateTime.of(2017, 11, 14, 0, 0, 0).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(1, cache.size());

        // advance time past "next update"
        clock.setInstant(LocalDateTime.of(2017, 11, 15, 0, 0, 0).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testExpirationOnSuccessMaxAge() throws IOException {
        TestClock clock = new TestClock(LocalDateTime.of(2017, 11, 8, 0, 0, 0).toInstant(ZoneOffset.UTC));
        OcspCache cache = new OcspCache(4, 1, 10, Integer.MAX_VALUE, clock, connectionBuilderFactory);

        // load cache
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);
        cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);
        Assert.assertEquals(1, cache.size());

        // advance time, response still valid
        clock.setInstant(LocalDateTime.of(2017, 11, 8, 0, 0, 9).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(1, cache.size());

        // advance time past expiration time
        clock.setInstant(LocalDateTime.of(2017, 11, 8, 0, 0, 11).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testExpirationOnErrorMaxAge() throws IOException {
        TestClock clock = new TestClock(LocalDateTime.of(2017, 11, 8, 0, 0, 0).toInstant(ZoneOffset.UTC));
        OcspCache cache = new OcspCache(4, 1, Integer.MAX_VALUE, 10, clock, connectionBuilderFactory);

        // load cache
        HttpURLConnection connection = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_FAIL);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection);
        cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[1]);
        Assert.assertEquals(1, cache.size());

        // advance time, response still valid
        clock.setInstant(LocalDateTime.of(2017, 11, 8, 0, 0, 9).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(1, cache.size());

        // advance time past max error time
        clock.setInstant(LocalDateTime.of(2017, 11, 8, 0, 0, 11).toInstant(ZoneOffset.UTC));
        cache.refresh();
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testSerialization() throws IOException, InterruptedException {
        OcspCache cache = new OcspCache(4, 1, 10000, 10000, Clock.systemUTC(), connectionBuilderFactory);

        HttpURLConnection connection0 = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_REVOKED);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection0);
        cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[0]);

        HttpURLConnection connection1 = HttpTestUtils.createMockResponse(200, OCSP_RESPONSE_SUCCESS_GOOD);
        Mockito.when(connectionBuilder.post(Mockito.any(byte[].class))).thenReturn(connection1);
        cache.getOcspResponse(CERTIFICATES[1], ISSUER_CERTIFICATES[1]);

        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        cache.writeToStream(serialized);

        // cache deserialization is asynchronous so we use a semaphore to synchronize assertions
        final Semaphore streamClosedSemaphore = new Semaphore(1);
        streamClosedSemaphore.acquire();
        InputStream serializedStream = new ByteArrayInputStream(serialized.toByteArray()) {
            @Override
            public void close() throws IOException {
                streamClosedSemaphore.release();
            }
        };

        OcspCache deserializedCache = new OcspCache(4, 1, 10000, 10000, Clock.systemUTC(), connectionBuilderFactory, serializedStream);
        streamClosedSemaphore.acquire();

        Assert.assertEquals(cache.size(), deserializedCache.size());
        Assert.assertArrayEquals(cache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[0]), deserializedCache.getOcspResponse(CERTIFICATES[0], ISSUER_CERTIFICATES[0]));
        Assert.assertArrayEquals(cache.getOcspResponse(CERTIFICATES[1], ISSUER_CERTIFICATES[1]), deserializedCache.getOcspResponse(CERTIFICATES[1], ISSUER_CERTIFICATES[1]));
        Mockito.verify(connectionBuilder, Mockito.times(2)).post(Mockito.any(byte[].class));
    }
}
