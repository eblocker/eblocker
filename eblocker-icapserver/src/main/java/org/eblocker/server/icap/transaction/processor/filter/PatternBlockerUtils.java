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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.blacklist.BlockedDomainLog;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.service.DomainRecordingService;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;

@Singleton
public class PatternBlockerUtils {
    private final DeviceService deviceService;
    private final BlockedDomainLog blockedDomainLog;
    private final FilterStatisticsService filterStatisticsService;
    private final DomainRecordingService domainRecordingService;
    private final boolean ignoreFirstParty;

    @Inject
    public PatternBlockerUtils(@Named("filter.stats.ignore.first.party") boolean ignoreFirstParty,
                               DeviceService deviceService,
                               BlockedDomainLog blockedDomainLog,
                               FilterStatisticsService filterStatisticsService,
                               DomainRecordingService domainRecordingService) {
        this.ignoreFirstParty = ignoreFirstParty;
        this.deviceService = deviceService;
        this.blockedDomainLog = blockedDomainLog;
        this.filterStatisticsService = filterStatisticsService;
        this.domainRecordingService = domainRecordingService;
    }

    public void countBlockedDomain(Category category, FilterResult filterResult, Session session, Transaction transaction) {
        Device device = deviceService.getDeviceById(session.getDeviceId());
        String domain = getDomain(filterResult, transaction);
        filterStatisticsService.countBlocked("pattern", transaction.getOriginalClientIP(), category.name());
        if (transaction.isThirdParty() || !ignoreFirstParty) {
            blockedDomainLog.addEntry(device.getId(), domain, category);
        }
        domainRecordingService.log(device, UrlUtils.getHostname(transaction.getUrl()), true, true);
    }

    public void countPassedDomain(Session session, Transaction transaction) {
        Device device = deviceService.getDeviceById(session.getDeviceId());
        domainRecordingService.log(device, UrlUtils.getHostname(transaction.getUrl()), false, true);
    }

    private String getDomain(FilterResult filterResult, Transaction transaction) {
        String domain = filterResult.getDecider().getDomain();
        return domain != null ? domain : UrlUtils.getHostname(transaction.getUrl());
    }

}
