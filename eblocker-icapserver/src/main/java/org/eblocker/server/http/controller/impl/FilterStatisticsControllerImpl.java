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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.BlockedDomainsStats;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterStats;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.statistic.BlockedDomainsStatisticService;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.util.FilterModeUtils;
import org.eblocker.server.http.controller.FilterStatisticsController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Singleton
public class FilterStatisticsControllerImpl implements FilterStatisticsController {

    private static final Logger LOG = LoggerFactory.getLogger(FilterStatisticsControllerImpl.class);

    private final BlockedDomainsStatisticService blockedDomainsStatisticService;
    private final DeviceService deviceService;
    private final FilterStatisticsService filterStatisticsService;
    private final SslService sslService;

    @Inject
    public FilterStatisticsControllerImpl(BlockedDomainsStatisticService blockedDomainsStatisticService,
                                          DeviceService deviceService,
                                          FilterStatisticsService filterStatisticsService,
                                          SslService sslService) {
        this.blockedDomainsStatisticService = blockedDomainsStatisticService;
        this.deviceService = deviceService;
        this.filterStatisticsService = filterStatisticsService;
        this.sslService = sslService;
    }

    @Override
    public FilterStats getStats(Request request, Response response) {
        int binSizeMinutes = getBinSizeMinutes(request);
        int numberOfBins = getNumberOfBins(request);
        int diagramWidth = numberOfBins * binSizeMinutes;
        String type = getType(request);
        IpAddress ipAddress = getIpAddress(request);

        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        // Midnight should always be a bin border, so that we do not have floating bins.
        int minuteOfDay = now.getMinute() + 60 * now.getHour();
        int offset = (minuteOfDay / binSizeMinutes + 1) * binSizeMinutes - minuteOfDay;
        Instant startTime = now.minusMinutes(diagramWidth).plusMinutes(offset).toInstant();
        Instant endTime = now.plusMinutes(offset).toInstant();

        LOG.info("Filter Stats from {} to {} / {} - {} / {} - {}",
            startTime, endTime, binSizeMinutes, diagramWidth, minuteOfDay, offset);
        return filterStatisticsService.getStatistics(startTime, endTime, binSizeMinutes, type, ipAddress);
    }

    @Override
    public FilterStats getTotalStats(Request request, Response response) {
        String type = request.getHeader("type");
        return filterStatisticsService.getTotalStatistics(type);
    }

    @Override
    public void resetTotalStats(Request request, Response response) {
        filterStatisticsService.resetTotalStatistics();
        blockedDomainsStatisticService.resetStats();
    }

    @Override
    public BlockedDomainsStats getBlockedDomainsStats(Request request, Response response) {
        return blockedDomainsStatisticService.getStatsByDeviceId(getDeviceId(request));
    }

    @Override
    public BlockedDomainsStats resetBlockedDomainsStats(Request request, Response response) {
        return blockedDomainsStatisticService.resetStats(getDeviceId(request));
    }

    private String getDeviceId(Request request) {
        IpAddress ipAddress = ControllerUtils.getRequestIPAddress(request);
        Device device = deviceService.getDeviceByIp(ipAddress);
        return device.getId();
    }

    private int getBinSizeMinutes(Request request) {
        return Integer.parseInt(ControllerUtils.getQueryParameter(request, "binSizeMinutes", "1440"));
    }

    private int getNumberOfBins(Request request) {
        return Integer.parseInt(ControllerUtils.getQueryParameter(request, "numberOfBins", "31"));
    }

    private IpAddress getIpAddress(Request request) {
        return request.getHeader("device") != null ? ControllerUtils.getRequestIPAddress(request) : null;
    }

    private String getType(Request request) {
        String type = request.getHeader("type");
        if (type != null) {
            return type;
        }

        Device device = deviceService.getDeviceByIp(ControllerUtils.getRequestIPAddress(request));
        switch (FilterModeUtils.getEffectiveFilterMode(sslService.isSslEnabled(), device)) {
            case ADVANCED:
                return null; // parental control / user blacklist are filtered by dns if enabled so display both stats
            case PLUG_AND_PLAY:
                return "dns";
            case NONE:
                return "none";
            case AUTOMATIC:
                throw new IllegalArgumentException("AUTOMATIC is not a valid effective filter mode!");
        }

        return null;
    }
}
