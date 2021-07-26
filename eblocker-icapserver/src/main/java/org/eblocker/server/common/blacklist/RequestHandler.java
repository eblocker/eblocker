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
package org.eblocker.server.common.blacklist;

import com.google.inject.Inject;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.service.DomainRecordingService;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@ChannelHandler.Sharable
public class RequestHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private static final String ERR_MESSAGE = "ERR\n";

    private final DomainBlockingService domainBlockingService;
    private final BlockedDomainLog blockedDomainLog;
    private final DeviceService deviceService;
    private final FilterStatisticsService filterStatisticsService;
    private final DomainRecordingService domainRecordingService;

    private final AtomicInteger requestId = new AtomicInteger();

    @Inject
    public RequestHandler(BlockedDomainLog blockedDomainLog,
                          DomainBlockingService domainBlockingService,
                          DeviceService deviceService,
                          FilterStatisticsService filterStatisticsService,
                          DomainRecordingService domainRecordingService) {
        this.domainBlockingService = domainBlockingService;
        this.blockedDomainLog = blockedDomainLog;
        this.deviceService = deviceService;
        this.filterStatisticsService = filterStatisticsService;
        this.domainRecordingService = domainRecordingService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) throws Exception {
        int id = requestId.getAndIncrement();
        if (logger.isDebugEnabled()) {
            logger.debug("id {} request {}", id, line);
        }

        String[] items = line.split(" ");

        if (items.length < 4) {
            throw new IOException("malformed request: " + line);
        }

        IpAddress clientIp = IpAddress.parse(items[0]);
        String proto = items[1];
        String requestedHost = items[2];
        String sslSni = items[3];

        String hostname;
        if ("http".equals(proto) || "dns".equals(proto)) {
            hostname = requestedHost;
        } else if (!"-".equals(sslSni)) {
            hostname = sslSni;
        } else {
            logger.debug("id {} no ssl host to check", id);
            ctx.writeAndFlush(ERR_MESSAGE);
            return;
        }

        Device device = deviceService.getDeviceByIp(clientIp);
        if (device == null) {
            ctx.writeAndFlush(ERR_MESSAGE);
            return;
        }

        DomainBlockingService.Decision decision = domainBlockingService.isBlocked(device, hostname);
        domainRecordingService.log(device, hostname, decision.isBlocked(), false);

        if (decision.isBlocked()) {
            ctx.writeAndFlush("OK message=" + toString(decision.getProfileId()) + "," + toString(decision.getListId()) + "," + decision.getDomain() + "," + device.getOperatingUser() + "," + toString(decision.getTarget()) + "\n");
            blockedDomainLog.addEntry(device.getId(), decision.getDomain(), decision.getListId());
            if ("http".equals(proto) || "-".equals(proto)) {
                filterStatisticsService.countQuery("pattern", clientIp);
                filterStatisticsService.countBlocked("pattern", clientIp, String.valueOf(decision.getListId()));
            }
        } else {
            ctx.writeAndFlush(ERR_MESSAGE);
            return;
        }

        logger.debug("id {} device {} user {} profile {} hostname {} blocked {}", id, device, device.getOperatingUser(), decision.getProfileId(), hostname, decision.isBlocked());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("exception while processing requests", cause);
        ctx.close();
    }

    private String toString(Object o) {
        return Objects.toString(o, "");
    }
}
