/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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

import com.google.inject.Inject;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.dashboard.ParentalControlCard;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrates a database that was created on eBlockerOS 2.4.
 * Paths and package names containing "brightmammoth" or "moonshine" must be updated.
 */
public class SchemaMigrationVersion47 implements SchemaMigration {
    private static final String PACKAGE_DASHBOARD_OLD = "com.brightmammoth.moonshine.common.data.dashboard";
    private static final String PACKAGE_DASHBOARD_NEW = "org.eblocker.server.common.data.dashboard";
    private static final String ICAPSERVER_PATH_OLD = "/opt/moonshine-icap/";
    private static final String ICAPSERVER_PATH_NEW = "/opt/eblocker-icap/";

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    @Inject
    public SchemaMigrationVersion47(DataSource dataSource, JedisPool jedisPool) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
    }

    @Override
    public String getSourceVersion() {
        return "46";
    }

    @Override
    public String getTargetVersion() {
        return "47";
    }

    @Override
    public void migrate() {
        updateUiCards();
        updateFilterPaths();
        dataSource.setVersion(getTargetVersion());
    }

    private void updateUiCards() {
        dataSource.getIds(UiCard.class).forEach(this::updatePackageName);
        dataSource.getIds(ParentalControlCard.class).forEach(this::updatePackageName);
    }

    private void updateFilterPaths() {
        dataSource.getAll(ExternalDefinition.class).forEach(this::updateExternalDefinition);
        dataSource.getAll(FilterStoreConfiguration.class).forEach(this::updateFilterStoreConfiguration);
        dataSource.getAll(ParentalControlFilterMetaData.class).forEach(this::updatePCFilterMetaData);
    }

    private void updateFilterStoreConfiguration(FilterStoreConfiguration filterStoreConfiguration) {
        boolean modified = false;
        String[] resources = filterStoreConfiguration.getResources();
        if (resources != null) {
            for (int i = 0; i < resources.length; i++) {
                String resource = resources[i];
                if (resource.contains(ICAPSERVER_PATH_OLD)) {
                    resources[i] = resource.replace(ICAPSERVER_PATH_OLD, ICAPSERVER_PATH_NEW);
                    modified = true;
                }
            }
        }
        if (modified) {
            dataSource.save(filterStoreConfiguration, filterStoreConfiguration.getId());
        }
    }

    private void updatePCFilterMetaData(ParentalControlFilterMetaData pcFilterMetaData) {
        boolean modified = false;
        List<String> filenames = pcFilterMetaData.getFilenames();
        List<String> filenamesNew = new ArrayList<>(filenames.size());
        if (filenames != null) {
            for (String filename : filenames) {
                if (filename.contains(ICAPSERVER_PATH_OLD)) {
                    filenamesNew.add(filename.replace(ICAPSERVER_PATH_OLD, ICAPSERVER_PATH_NEW));
                    modified = true;
                } else {
                    filenamesNew.add(filename);
                }
            }
        }
        if (modified) {
            pcFilterMetaData.setFilenames(filenamesNew);
            dataSource.save(pcFilterMetaData, pcFilterMetaData.getId());
        }
    }

    private void updateExternalDefinition(ExternalDefinition externalDefinition) {
        String file = externalDefinition.getFile();
        if (file != null && file.contains(ICAPSERVER_PATH_OLD)) {
            externalDefinition.setFile(file.replace(ICAPSERVER_PATH_OLD, ICAPSERVER_PATH_NEW));
            dataSource.save(externalDefinition, externalDefinition.getId());
        }
    }

    /**
     * We cannot load the entities, since the package com.brightmammoth does not exist any more.
     * So we must work directly on the JSON strings.
     *
     * @param key
     */
    private void updatePackageName(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json.contains(PACKAGE_DASHBOARD_OLD)) {
                String jsonNew = json.replace(PACKAGE_DASHBOARD_OLD, PACKAGE_DASHBOARD_NEW);
                jedis.set(key, jsonNew);
            }
        }
    }
}
