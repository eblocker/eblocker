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
package org.eblocker.server.common.network.unix;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.dns.DnsCheckDone;
import org.eblocker.server.common.network.NetworkServices;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

public class DnsEnableByDefaultChecker {

    private final boolean enabledByDefault;
    private final String defaultDisableFile;
    private final DataSource dataSource;
    private final NetworkServices networkServices;

    @Inject
    public DnsEnableByDefaultChecker(@Named("dns.server.default.enabled") boolean enabledByDefault,
                                     @Named("dns.server.default.disable.file") String defaultDisableFile,
                                     DataSource dataSource,
                                     NetworkServices networkServices) {
        this.enabledByDefault = enabledByDefault;
        this.defaultDisableFile = defaultDisableFile;
        this.dataSource = dataSource;
        this.networkServices = networkServices;
    }

    public void check() {
        boolean alreadyChecked = dataSource.get(DnsCheckDone.class) != null;
        if (!alreadyChecked) {
            boolean enable = enabledByDefault && !Files.exists(Paths.get(defaultDisableFile));
            if (enable) {
                NetworkConfiguration configuration = networkServices.getCurrentNetworkConfiguration();
                configuration.setDnsServer(true);
                networkServices.configureEblockerDns(configuration);
                networkServices.applyNetworkConfiguration(configuration);
            }
            dataSource.save(new DnsCheckDone(new Date(), enable));
        }
    }
}
