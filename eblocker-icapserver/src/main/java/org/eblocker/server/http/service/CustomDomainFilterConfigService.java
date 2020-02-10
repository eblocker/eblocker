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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service for configuring custom non-parental control related black- and whitelists  for pattern and dns filters.
 */
@Singleton
public class CustomDomainFilterConfigService {

    private final ParentalControlFilterListsService filterListsService;
    private final UserService userService;

    @Inject
    public CustomDomainFilterConfigService(ParentalControlFilterListsService filterListsService,
                                           UserService userService) {
        this.filterListsService = filterListsService;
        this.userService = userService;
    }

    public CustomDomainFilterConfig getCustomDomainFilterConfig(int userId) {
        UserModule user = userService.getUserById(userId);
        if (user == null) {
            return new CustomDomainFilterConfig();
        }

        return new CustomDomainFilterConfig(
            getDomains(user.getCustomBlacklistId()),
            getDomains(user.getCustomWhitelistId()));
    }

    public CustomDomainFilterConfig setCustomDomainFilterConfig(int userId, CustomDomainFilterConfig customDomainFilterConfig) {
        UserModule user = userService.getUserById(userId);
        if (user == null) {
            return null;
        }

        Integer blacklistId = updateDnsFilter(user.getCustomBlacklistId(), userId, "blacklist", customDomainFilterConfig
            .getBlacklistedDomains());
        Integer whitelistId = updateDnsFilter(user.getCustomWhitelistId(), userId, "whitelist", customDomainFilterConfig
            .getWhitelistedDomains());
        if (!Objects.equals(blacklistId, user.getCustomBlacklistId()) || !Objects.equals(whitelistId, user.getCustomWhitelistId())) {
            userService.updateUser(user.getId(), blacklistId, whitelistId);
        }
        return customDomainFilterConfig;
    }

    private Set<String> getDomains(Integer listId) {
        if (listId == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(filterListsService.getFilterListDomains(listId));
    }

    private Integer updateDnsFilter(Integer id, int userId, String type, Set<String> domains) {
        if (domains.isEmpty()) {
            if (id != null) {
                filterListsService.deleteFilterList(id);
            }
            return null;
        }

        ParentalControlFilterSummaryData data = null;
        if (id != null) {
            data = filterListsService.getFilterById(id);
            List<String> storedDomains = filterListsService.getFilterListDomains(id);
            if (storedDomains.size() == domains.size() && domains.containsAll(storedDomains)) {
                return id;
            }
        }

        Instant now = Instant.now();
        if (data == null) {
            data = filterListsService.createFilterList(
                new ParentalControlFilterSummaryData(
                    null,
                    null,
                    null,
                    now.toString(),
                    Date.from(now),
                    type,
                    false,
                    false,
                    new ArrayList<>(domains),
                    "user-" + userId + "-" + type,
                    "user-" + userId + "-" + type,
                    Category.CUSTOM),
                type);
        } else {
            data = new ParentalControlFilterSummaryData(
                data.getId(),
                data.getName(),
                data.getDescription(),
                now.toString(),
                Date.from(now),
                data.getFilterType(),
                data.isBuiltin(),
                data.isDisabled(),
                new ArrayList<>(domains),
                data.getCustomerCreatedName(),
                data.getCustomerCreatedDescription(),
                data.getCategory());
            data = filterListsService.updateFilterList(data, data.getFilterType());
        }

        return data.getId();
    }
}
