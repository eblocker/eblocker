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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.http.service.DeviceService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

public class SslTestRequestHandlerTest {

    private final String deviceId = "device:000000000001";
    private final String encodedDeviceId= "device%3a000000000001";
    private final BigInteger serialNumber = BigInteger.valueOf(12345);
    private final String userAgent = "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.7.3) Gecko/20041007 Epiphany/1.4.7";

    private DeviceService deviceService;
    private SslCertificateClientInstallationTracker tracker;
    private SslTestRequestHandler handler;
    private EmbeddedChannel embeddedChannel;

    @Before
    public void setup() {
        deviceService = Mockito.mock(DeviceService.class);
        Device device = new Device();
        device.setId(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);

        tracker = Mockito.mock(SslCertificateClientInstallationTracker.class);
        handler = new SslTestRequestHandler(deviceService, tracker, serialNumber);
        embeddedChannel = new EmbeddedChannel(handler);
    }

    @Test
    public void testRequest() {
        HttpResponse response = handleRequest("/" + encodedDeviceId, userAgent);

        Mockito.verify(tracker).markCertificateAsInstalled(deviceId, userAgent, serialNumber, true);
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        Assert.assertEquals("text/plain", response.headers().get("Content-Type"));
    }

    @Test
    public void testRequestNoUserAgent() {
        HttpResponse response = handleRequest("/" + encodedDeviceId, null);
        Mockito.verifyZeroInteractions(tracker);
        Assert.assertNull(response);
        Assert.assertFalse(embeddedChannel.isActive());
    }

    @Test
    public void testRequestUnknownDevice() {
        handleRequest("/device%3A000000000002", userAgent);
        Mockito.verify(deviceService).getDeviceById("device:000000000002");
        Mockito.verifyZeroInteractions(tracker);
    }

    @Test
    public void testRequestMissingDevice() {
        handleRequest("/", userAgent);
        Mockito.verifyZeroInteractions(deviceService);
        Mockito.verifyZeroInteractions(tracker);
    }

    private HttpResponse handleRequest(String uri, String userAgent) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        if (userAgent != null) {
            request.headers().set("User-Agent", userAgent);
        }
        embeddedChannel.writeInbound(request);
        embeddedChannel.checkException();
        return embeddedChannel.readOutbound();
    }
}
