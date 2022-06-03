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
import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.unix.firewall.IpAddressFilter;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorIp4;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorBase;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Set;

/**
 * Writes configuration files for "iptables-restore".
 */
public class FirewallConfigurationIp4 extends FirewallConfigurationBase {

    private static final Logger LOG = LoggerFactory.getLogger(FirewallConfigurationIp4.class);

    private final TableGeneratorIp4 tableGenerator;
    private final NetworkServices networkServices;
    private final ParentalControlAccessRestrictionsService restrictionsService;


    @Inject
    public FirewallConfigurationIp4(@Named("network.unix.firewall.config.full.path") String configFullPath,
                                    @Named("network.unix.firewall.config.delta.path") String configDeltaPath,
                                    TableGeneratorIp4 tableGenerator,
                                    NetworkServices networkServices,
                                    ParentalControlAccessRestrictionsService restrictionsService,
                                    Environment environment) {
        super(Paths.get(configFullPath), Paths.get(configDeltaPath), environment);
        this.tableGenerator = tableGenerator;
        this.networkServices = networkServices;
        this.restrictionsService = restrictionsService;
    }

    @Override
    protected IpAddressFilter getIpAddressFilter(Set<Device> devices) {
        return new IpAddressFilter(devices, IpAddress::isIpv4, restrictionsService);
    }

    @Override
    protected TableGeneratorBase getTableGenerator() {
        NetworkConfiguration netConfig = networkServices.getCurrentNetworkConfiguration();

        // Set IP/network addresses
        tableGenerator.setOwnIpAddress(netConfig.getIpAddress());
        tableGenerator.setNetworkMask(netConfig.getNetworkMask());
        tableGenerator.setGatewayIpAddress(netConfig.getGateway());
        tableGenerator.setMobileVpnIpAddress(netConfig.getVpnIpAddress());
        return tableGenerator;
    }
}
