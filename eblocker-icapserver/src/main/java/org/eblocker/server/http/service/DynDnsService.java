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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DynDnsConfig;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.registration.DynDnsEntry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynDnsService {

    private static final Logger log = LoggerFactory.getLogger(DynDnsService.class);

    private final DataSource dataSource;
    private final DeviceRegistrationClient client;

    @Inject
    public DynDnsService(DataSource dataSource, DeviceRegistrationClient client) {
        this.dataSource = dataSource;
        this.client = client;
    }

    public boolean isEnabled() {
        return loadConfig().isEnabled();
    }

    public synchronized void enable() {
        DynDnsConfig config = loadConfig();
        config.setEnabled(true);
        saveConfig(config);
    }

    public synchronized void disable() {
        saveConfig(new DynDnsConfig());
    }

    public String getHostname() {
        return loadConfig().getHostname();
    }

    public synchronized void update() {
        DynDnsConfig config = loadConfig();
        if (!config.isEnabled()) {
            log.debug("dyndns disabled - no update");
            return;
        }

        try {
            DynDnsEntry entry = client.updateDynDnsIpAddress();
            config.setHostname(entry.getHostname());
            config.setIpAddress(entry.getIpAddress());
            saveConfig(config);

            log.debug("dyndns hostname {} updated to {}", entry.getHostname(), entry.getIpAddress());
        } catch (Exception e) {
            log.warn("dyndns update failed:", e);
        }
    }

    private DynDnsConfig loadConfig() {
        DynDnsConfig config = dataSource.get(DynDnsConfig.class);
        return config != null ? config : new DynDnsConfig();
    }

    private void saveConfig(DynDnsConfig config) {
        dataSource.save(config);
    }

}
