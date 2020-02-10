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
package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModuleOld;
import com.google.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This migration ensures no custom black/whitelist are shared across standard users (EB1-1826)
 */
public class SchemaMigrationVersion35 implements SchemaMigration {

    private final DataSource dataSource;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion35(DataSource dataSource,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "34";
    }

    @Override
    public String getTargetVersion() {
        return "35";
    }

    @Override
    public void migrate() {
        List<UserModuleOld> users = userMigrationService.getAll();
        Set<Integer> sharedBlacklistIds = getSharedListsIds(users, UserModuleOld::getCustomBlacklistId);
        Set<Integer> sharedWhitelistIds = getSharedListsIds(users, UserModuleOld::getCustomWhitelistId);

        for(UserModuleOld user : users) {
            boolean modified = false;
            if (sharedBlacklistIds.contains(user.getCustomBlacklistId())) {
                modified = true;
                user.setCustomBlacklistId(null);
            }
            if (sharedWhitelistIds.contains(user.getCustomWhitelistId())) {
                modified = true;
                user.setCustomWhitelistId(null);
            }

            if (modified) {
                userMigrationService.save(user, user.getId());
            }
        }

        dataSource.setVersion("35");
    }

    private Set<Integer> getSharedListsIds(List<UserModuleOld> users, Function<UserModuleOld, Integer> getListId) {
        return users.stream()
            .filter(user -> getListId.apply(user) != null)
            .collect(Collectors.groupingBy(getListId, Collectors.toList()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
}
