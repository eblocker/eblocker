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
package org.eblocker.server.icap.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.blacklist.CachingFilter;
import org.eblocker.server.common.blacklist.DomainBlacklistService;
import org.eblocker.server.common.blacklist.DomainFilter;
import org.eblocker.server.common.blacklist.Filters;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.http.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Service for managing non-parental control related whitelist filter for pattern filtered devices
 */
@Singleton
public class CustomDomainFilterWhitelistService {

    private final Logger log = LoggerFactory.getLogger(CustomDomainFilterWhitelistService.class);

    private final DomainBlacklistService domainBlacklistService;
    private final UserService userService;

    private final Cache<Integer, DomainFilter<String>> filtersById;

    @Inject
    public CustomDomainFilterWhitelistService(DomainBlacklistService domainBlacklistService, UserService userService) {
        this.domainBlacklistService = domainBlacklistService;
        this.userService = userService;

        this.filtersById = CacheBuilder.newBuilder().concurrencyLevel(1).build();

        domainBlacklistService.addListener(filtersById::invalidateAll);
        userService.addListener(user -> filtersById.invalidate(user.getId()));
    }

    public DomainFilter<String> getWhitelistFilter(int userId) {
        try {
            return filtersById.get(userId, () -> createFilter(userId));
        } catch (ExecutionException e) {
            if (e.getCause() != null && e.getCause() instanceof NoFilterException) {
                log.debug("no filter for {}", userId, e);
            } else {
                log.error("failed to retrieve filter for {}", userId, e);
            }
            return Filters.staticFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private DomainFilter<String> createFilter(Integer userId) throws NoFilterException {
        UserModule user = userService.getUserById(userId);
        if (user == null || user.getCustomWhitelistId() == null) {
            throw new NoFilterException();
        }

        DomainFilter<String> filter = domainBlacklistService.getFilter(user.getCustomWhitelistId());
        if (filter == null) {
            throw new NoFilterException();
        }
        return Filters.cache(256, CachingFilter.CacheMode.NON_BLOCKED, Filters.hostname(filter));
    }

    private class NoFilterException extends Exception {
    }

}
