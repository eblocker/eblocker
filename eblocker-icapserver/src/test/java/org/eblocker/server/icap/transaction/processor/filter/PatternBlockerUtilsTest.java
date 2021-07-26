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
package org.eblocker.server.icap.transaction.processor.filter;

import org.eblocker.server.common.blacklist.BlockedDomainLog;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.service.DomainRecordingService;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PatternBlockerUtilsTest {

    private DeviceService deviceService;
    private BlockedDomainLog blockedDomainLog;
    private FilterStatisticsService filterStatisticsService;
    private DomainRecordingService domainRecordingService;
    private Session session;
    private Transaction transaction;
    private PatternBlockerUtils patternBlockerUtils;
    private Device device = new Device();

    @Before
    public void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        blockedDomainLog = Mockito.mock(BlockedDomainLog.class);
        filterStatisticsService = Mockito.mock(FilterStatisticsService.class);
        domainRecordingService = Mockito.mock(DomainRecordingService.class);

        session = Mockito.mock(Session.class);
        String deviceId = "device:000000000000";
        device.setId(deviceId);
        Mockito.when(session.getDeviceId()).thenReturn(deviceId);
        Mockito.when(deviceService.getDeviceById(deviceId)).thenReturn(device);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getUrl()).thenReturn("https://www.evil.com/tracking.js");
        Mockito.when(transaction.getOriginalClientIP()).thenReturn(IpAddress.parse("192.168.3.4"));
        patternBlockerUtils = new PatternBlockerUtils(true, deviceService, blockedDomainLog, filterStatisticsService, domainRecordingService);
    }

    @Test
    public void countBlockedDomainFilterDomainFirstParty() {
        Mockito.when(transaction.isThirdParty()).thenReturn(false);
        FilterResult result = FilterResult.block(mockFilter("evil.com"));
        patternBlockerUtils.countBlockedDomain(Category.TRACKERS, result, session, transaction);
        Mockito.verifyNoInteractions(blockedDomainLog);
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("192.168.3.4"), Category.TRACKERS.name());
    }

    @Test
    public void countBlockedDomainFilterDomainThirdParty() {
        Mockito.when(transaction.isThirdParty()).thenReturn(true);
        FilterResult result = FilterResult.block(mockFilter("evil.com"));
        patternBlockerUtils.countBlockedDomain(Category.TRACKERS, result, session, transaction);
        Mockito.verify(blockedDomainLog).addEntry("device:000000000000", "evil.com", Category.TRACKERS);
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("192.168.3.4"), Category.TRACKERS.name());
        Mockito.verify(domainRecordingService).log(device, "www.evil.com", true, true);
    }

    @Test
    public void countBlockedDomainFilterWithoutDomainFirstParty() {
        FilterResult result = FilterResult.block(mockFilter(null));
        patternBlockerUtils.countBlockedDomain(Category.TRACKERS, result, session, transaction);
        Mockito.verifyNoInteractions(blockedDomainLog);
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("192.168.3.4"), Category.TRACKERS.name());
    }

    @Test
    public void countBlockedDomainFilterWithoutDomainThirdParty() {
        Mockito.when(transaction.isThirdParty()).thenReturn(true);
        FilterResult result = FilterResult.block(mockFilter(null));
        patternBlockerUtils.countBlockedDomain(Category.TRACKERS, result, session, transaction);
        Mockito.verify(blockedDomainLog).addEntry("device:000000000000", "www.evil.com", Category.TRACKERS);
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("192.168.3.4"), Category.TRACKERS.name());
    }

    @Test
    public void countPassedDomain() {
        Mockito.when(transaction.getUrl()).thenReturn("https://www.good.com/index.html");
        patternBlockerUtils.countPassedDomain(session, transaction);
        Mockito.verify(domainRecordingService).log(device, "www.good.com", false, true);
    }

    private Filter mockFilter(String domain) {
        Filter filter = Mockito.mock(Filter.class);
        Mockito.when(filter.getDomain()).thenReturn(domain);
        return filter;
    }
}
