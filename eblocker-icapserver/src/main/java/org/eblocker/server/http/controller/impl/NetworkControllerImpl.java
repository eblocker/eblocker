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

import com.google.inject.Inject;
import com.strategicgains.syntaxe.ValidationEngine;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkIp6Configuration;
import org.eblocker.server.common.data.SetupPageNetworkInfo;
import org.eblocker.server.common.exceptions.DhcpDiscoveryException;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.DhcpDiscoveryService;
import org.eblocker.server.http.controller.NetworkController;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Performs basic network configuration of the system (e.g. IP address, gateway, DHCP, ...)
 */
public class NetworkControllerImpl implements NetworkController {
    private final Logger log = LoggerFactory.getLogger(NetworkControllerImpl.class);

    private final DhcpDiscoveryService dhcpDiscoveryService;
    private final NetworkStateMachine networkStateMachine;
    private final NetworkServices networkServices;

    @Inject
    public NetworkControllerImpl(DhcpDiscoveryService dhcpDiscoveryService,
                                 NetworkStateMachine networkStateMachine,
                                 NetworkServices networkServices) {
        this.dhcpDiscoveryService = dhcpDiscoveryService;
        this.networkStateMachine = networkStateMachine;
        this.networkServices = networkServices;
    }

    @Override
    public Object getConfiguration(Request request, Response response) {
        return networkServices.getCurrentNetworkConfiguration();
    }

    @Override
    public Object getSetupPageInfo(Request request, Response response) {
        IpAddress currentIp = ControllerUtils.getRequestIPAddress(request);
        NetworkConfiguration config = networkServices.getCurrentNetworkConfiguration();
        SetupPageNetworkInfo networkInfo = new SetupPageNetworkInfo();
        // Copy data
        networkInfo.setUserIpAddress(currentIp.toString());
        networkInfo.setGateway(config.getGateway());
        networkInfo.setIpAddress(config.getIpAddress());
        networkInfo.setAutomatic(config.isAutomatic());
        networkInfo.setGlobalIp6AddressAvailable(config.isGlobalIp6AddressAvailable());
        return networkInfo;
    }

    @Override
    public Object updateConfiguration(Request request, Response response) {
        NetworkConfiguration networkConfiguration = request.getBodyAs(NetworkConfiguration.class);

        // validate new configuration:
        ValidationEngine.validateAndThrow(networkConfiguration);

        boolean reboot = networkStateMachine.updateConfiguration(networkConfiguration);
        networkConfiguration.setRebootNecessary(reboot);
        return networkConfiguration;
    }

    @Override
    public boolean getDHCPActive(Request request, Response response) {
        return networkServices.getDHCPActive();
    }

    @Override
    public Set<String> getDhcpServers(Request request, Response response) {
        try {
            return dhcpDiscoveryService.getDhcpServers();
        } catch (DhcpDiscoveryException e) {
            log.warn("dhcp discovery failed", e);
            throw new ServiceException("dhcp server discovery failed");
        }
    }

    @Override
    public Object getConfigurationIp6(Request request, Response response) {
        return networkServices.getNetworkIp6Configuration();
    }

    @Override
    public Object updateConfigurationIp6(Request request, Response response) {
        NetworkIp6Configuration networkIp6Configuration = request.getBodyAs(NetworkIp6Configuration.class);
        networkServices.updateNetworkIp6Configuration(networkIp6Configuration);
        return networkIp6Configuration;
    }
}
