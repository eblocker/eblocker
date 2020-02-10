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
package org.eblocker.server.common.network;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A service that registers the HTTP admin console as a Zeroconf (AKA Bonjour) service
 */
public class ZeroconfRegistrationService {
    private static final String SERVICE_TYPE = "_http._tcp.local.";

    private static final Logger log = LoggerFactory.getLogger(ZeroconfRegistrationService.class);

    private final int httpPort;
    private final String serviceDefaultName;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final NetworkInterfaceWrapper networkInterface;
    private JmDNS jmdns = null;

    @Inject
    public ZeroconfRegistrationService(@Named("httpPort") int httpPort,
                                      @Named("zeroconf.service.default.name") String serviceName,
                                      DeviceRegistrationProperties deviceRegistrationProperties,
                                      NetworkInterfaceWrapper networkInterface) {
        this.httpPort = httpPort;
        this.serviceDefaultName = serviceName;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.networkInterface = networkInterface;
    }

    /**
     * Registers the HTTP service
     */
    public void registerConsoleService() {
        try {
            InetAddress address = InetAddress.getByName(networkInterface.getFirstIPv4Address().toString());
            jmdns = JmDNS.create(address, "eblocker");
            String name = getServiceName();
            ServiceInfo info = ServiceInfo.create(SERVICE_TYPE, name, httpPort, "");
            log.info("Registering service '{}' at port {} ...", name, httpPort);
            jmdns.registerService(info);
            log.info("Registered Zeroconf/Bonjour service of type {} at port {}", SERVICE_TYPE, httpPort);
        } catch (IOException e) {
            log.error("Could not register Zeroconf/Bonjour HTTP service", e);
        }        
    }

    /**
     * Unregisters the HTTP service
     */
    public void unregisterConsoleService() {
        if (jmdns == null) {
            log.error("Could not unregister Zeroconf/Bonjour HTTP service. Registration seems to have failed.");
            return;
        }
        jmdns.unregisterAllServices();
        log.info("Unregistered all Zeroconf/Bonjour services.");
    }

    public String getServiceName() {
        String name = deviceRegistrationProperties.getDeviceName();
        if (name == null) {
            name = serviceDefaultName;
        }

        return name.replace('.', '_'); // JmDNS does not work correctly when dots are in the service instance name
    }
}
