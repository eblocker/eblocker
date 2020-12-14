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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.math.BigInteger;

@ChannelHandler.Sharable
public class SslTestRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {
    private static final Logger log = LoggerFactory.getLogger(SslTestRequestHandler.class);

    private static final String USER_AGENT_HEADER = "User-Agent";

    private final DeviceService deviceService;
    private final SslCertificateClientInstallationTracker tracker;
    private final BigInteger serialNumber;

    @Inject
    public SslTestRequestHandler(DeviceService deviceService,
                                 SslCertificateClientInstallationTracker tracker,
                                 @Assisted BigInteger serialNumber) {
        this.deviceService = deviceService;
        this.tracker = tracker;
        this.serialNumber = serialNumber;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        log.debug("handling request from {}", ctx.channel().remoteAddress());
        if (!validateRequest(request)) {
            ctx.close();
            return;
        }
        updateInstallationStatus(request.uri(), request.headers().get(USER_AGENT_HEADER));
        ctx.writeAndFlush(createResponse(request)).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean validateRequest(HttpRequest request) {
        if (request == null) {
            log.debug("empty request");
            return false;
        }

        if (request.headers().get(USER_AGENT_HEADER) == null) {
            log.debug("request missing user-agent");
            return false;
        }

        if (!UrlUtils.urlDecode(request.uri()).matches(".*/device:([0-9a-fA-F]){12}$")) {
            log.debug("missing device id");
            return false;
        }

        return true;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause.getCause() instanceof SSLException) {
            log.debug("client does not know ca", cause);
        } else {
            log.warn("exception in ssl test", cause);
        }
    }

    private HttpResponse createResponse(HttpRequest request) {
        HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        response.headers()
                .set("Access-Control-Allow-Origin", "*")
                .set("Content-Type", "text/plain");
        return response;
    }

    private void updateInstallationStatus(String uri, String userAgent) {
        String deviceId = UrlUtils.urlDecode(uri.substring(uri.lastIndexOf('/') + 1)).toLowerCase();
        Device device = deviceService.getDeviceById(deviceId);
        if (device != null) {
            log.debug("marking certificate installed for {} / {}", deviceId, userAgent);
            tracker.markCertificateAsInstalled(device.getId(), userAgent, serialNumber, true);
        } else {
            log.debug("no device known with id {}", deviceId);
        }
    }
}
