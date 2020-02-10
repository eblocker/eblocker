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

import org.eblocker.server.common.blacklist.CachingFilter;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.http.controller.DomainBlockingController;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;

public class DomainBlockingControllerImpl implements DomainBlockingController {

    private final boolean statisticsAllowIncludeDomains;
    private final DomainBlockingService domainBlockingService;

    @Inject
    public DomainBlockingControllerImpl(@Named("domainblacklist.statistics.allowIncludeDomains") Boolean statisticsAllowIncludeDomains,
                                        DomainBlockingService domainBlockingService) {
        this.domainBlockingService = domainBlockingService;
        this.statisticsAllowIncludeDomains = statisticsAllowIncludeDomains;
    }

    @Override
    public List<CachingFilter.Stats> getCacheStats(Request request, Response response) {
        boolean includeDomains = statisticsAllowIncludeDomains && Boolean.parseBoolean(request.getHeader("includeDomains"));
        return domainBlockingService.getCacheStats(includeDomains);
    }

    @Override
    public void clearCaches(Request request, Response response) {
        domainBlockingService.clearCaches();
    }

}
