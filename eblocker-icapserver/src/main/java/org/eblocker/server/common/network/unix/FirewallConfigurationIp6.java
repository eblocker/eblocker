/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.unix.firewall.IpAddressFilter;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorBase;
import org.eblocker.server.common.network.unix.firewall.TableGeneratorIp6;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;

import java.nio.file.Paths;
import java.util.Set;

public class FirewallConfigurationIp6 extends FirewallConfigurationBase {
    private final TableGeneratorIp6 tableGenerator;
    private final ParentalControlAccessRestrictionsService restrictionsService;
    private final NetworkInterfaceWrapper networkInterface;

    @Inject
    public FirewallConfigurationIp6(
            @Named("network.unix.firewall.ip6.config.full.path") String configFullPath,
            @Named("network.unix.firewall.ip6.config.delta.path") String configDeltaPath,
            TableGeneratorIp6 tableGenerator,
            NetworkInterfaceWrapper networkInterface,
            ParentalControlAccessRestrictionsService restrictionsService,
            Environment environment) {
        super(Paths.get(configFullPath), Paths.get(configDeltaPath), environment);
        this.tableGenerator = tableGenerator;
        this.networkInterface = networkInterface;
        this.restrictionsService = restrictionsService;
    }

    @Override
    protected IpAddressFilter getIpAddressFilter(Set<Device> devices) {
        return new IpAddressFilter(devices, IpAddress::isIpv6, restrictionsService);
    }

    @Override
    protected TableGeneratorBase getTableGenerator() {
        tableGenerator.setOwnIpAddress(networkInterface.getIp6LinkLocalAddress().toString());
        return tableGenerator;
    }
}
