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
package org.eblocker.server.upnp;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;
import org.fourthline.cling.support.model.PortMapping;
import org.fourthline.cling.support.model.PortMapping.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class UpnpManagementService {
    private static final DeviceType IGD_DEVICE_TYPE = new UDADeviceType("InternetGatewayDevice", 1);
    static final DeviceType CONNECTION_DEVICE_TYPE = new UDADeviceType("WANConnectionDevice", 1);

    static final ServiceType IP_SERVICE_TYPE = new UDAServiceType("WANIPConnection", 1);
    static final ServiceType PPP_SERVICE_TYPE = new UDAServiceType("WANPPPConnection", 1);

    private static final String ERROR_NO_DEVICE_FOUND = "No device found";

    static final String RESULT_KEY_PORT_MAPPING_DESCRIPTION = "NewPortMappingDescription";
    static final String RESULT_KEY_PORT_MAPPING_ENABLED = "NewEnabled";
    static final String RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT = "NewExternalPort";
    static final String RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT = "NewInternalClient";
    static final String RESULT_KEY_PORT_MAPPING_INTERNAL_PORT = "NewInternalPort";
    static final String RESULT_KEY_PORT_MAPPING_DURATION = "NewLeaseDuration";
    static final String RESULT_KEY_PORT_MAPPING_PROTOCOL = "NewProtocol";
    static final String RESULT_KEY_PORT_MAPPING_REMOTE_HOST = "NewRemoteHost";
    private static final int PORT_FORWARDINGS_MAX_NUM_EXPECTED = 250;
    private final int maxDiscoverySteps;
    private final int discoveryWaitingTime;

    private static final Logger log = LoggerFactory.getLogger(UpnpManagementService.class);
    private final List<UpnpPortForwarding> activePortForwardings;
    private IpAddress ip;
    private final NetworkInterfaceWrapper networkInterfaceWrapper;

    private final UpnpService upnpService;
    private final UpnpPortForwardingAddFactory upnpPortForwardingAddFactory;
    private final UpnpPortForwardingDeleteFactory upnpPortForwardingDeleteFactory;
    private final UpnpActionCallbackFactory upnpActionCallbackFactory;
    private final UpnpActionInvocationFactory upnpActionInvocationFactory;

    @Inject
    public UpnpManagementService(NetworkInterfaceWrapper networkInterfaceWrapper, UpnpService upnpService,
                                 UpnpPortForwardingAddFactory upnpPortForwardingAddFactory,
                                 UpnpPortForwardingDeleteFactory upnpPortForwardingDeleteFactory,
                                 UpnpActionCallbackFactory upnpActionCallbackFactory, UpnpActionInvocationFactory upnpActionInvocationFacory,
                                 @Named("openvpn.server.portforwarding.upnp.discovery.max.steps") int upnpDiscoveryMaxSteps,
                                 @Named("openvpn.server.portforwarding.upnp.discovery.waiting.time") int upnpDiscoveryWaitingTime) {
        this.discoveryWaitingTime = upnpDiscoveryWaitingTime;
        this.maxDiscoverySteps = upnpDiscoveryMaxSteps;
        activePortForwardings = new ArrayList<>();
        this.networkInterfaceWrapper = networkInterfaceWrapper;
        this.upnpService = upnpService;
        this.upnpPortForwardingAddFactory = upnpPortForwardingAddFactory;
        this.upnpPortForwardingDeleteFactory = upnpPortForwardingDeleteFactory;
        this.upnpActionCallbackFactory = upnpActionCallbackFactory;
        this.upnpActionInvocationFactory = upnpActionInvocationFacory;
        this.upnpService.getControlPoint().search();

        networkInterfaceWrapper.addIpAddressChangeListener(this::onIpAddressChange);
        ip = networkInterfaceWrapper.getFirstIPv4Address();
    }

    public List<UpnpPortForwardingResult> addPortForwarding(int externalPort, int internalPort, int duration,
                                                            String description) {
        String eblockerIp = networkInterfaceWrapper.getFirstIPv4Address().toString();

        List<UpnpPortForwarding> portForwardings = new ArrayList<>();
        portForwardings.add(new UpnpPortForwarding(externalPort, internalPort, eblockerIp, duration, description,
                Protocol.UDP, true));
        return addPortForwardings(portForwardings);
    }

    public synchronized List<UpnpPortForwardingResult> addPortForwardings(List<UpnpPortForwarding> portForwardings) {
        List<UpnpPortForwardingResult> results = new ArrayList<>();
        for (UpnpPortForwarding portForwarding : portForwardings) {
            results.addAll(addPortForwarding(portForwarding));
        }
        return results;
    }

    @SuppressWarnings("rawtypes")
    private Collection<Device> getGatewayDevices() {
        Collection<Device> gatewayDevices = Collections.emptyList();
        for (int i = 0; i < maxDiscoverySteps; i++) {
            gatewayDevices = upnpService.getRegistry().getDevices(IGD_DEVICE_TYPE);
            if (gatewayDevices.isEmpty()) {
                try {
                    Thread.sleep(discoveryWaitingTime);
                } catch (InterruptedException e) {
                    log.error("Wait for upnp-devices interrupted", e);
                    Thread.currentThread().interrupt();
                    return Collections.emptyList();
                }
            } else {
                break;
            }
        }
        return gatewayDevices;
    }

    public synchronized List<UpnpPortForwardingResult> addPortForwarding(UpnpPortForwarding portForwarding) {
        Collection<Device> gatewayDevices = getGatewayDevices();
        List<UpnpPortForwardingResult> results = new ArrayList<>();
        for (Device gatewayDevice : gatewayDevices) {
            Device[] connectionDevices = gatewayDevice.findDevices(CONNECTION_DEVICE_TYPE);
            if (connectionDevices.length == 0 || connectionDevices[0] == null) {
                log.error("Could not find connection devices for device {}", gatewayDevice);
                continue;
            }
            Device connectionDevice = connectionDevices[0];
            Service service = findService(connectionDevice);
            if (service == null) {
                log.error("Could not find proper service for device {}", gatewayDevice);
                continue;
            }
            UpnpPortForwardingResult tmpResult = installPortForwarding(portForwarding, service);
            results.add(tmpResult);
            // There might be several devices found that all can handle port
            // forwarding requests. If a request for a port forwarding was
            // successful on such a device, skip trying the other devices.
            if (tmpResult.isSuccess()) {
                break;
            }
        }
        if (results.isEmpty()) {
            log.error("No gateway devices found on which to establish a port forwarding");
            results.add(new UpnpPortForwardingResult(portForwarding, false, ERROR_NO_DEVICE_FOUND));
        }
        return results;
    }

    private UpnpPortForwardingResult installPortForwarding(UpnpPortForwarding portForwarding, Service service) {
        UpnpPortForwardingAdd upnpPortForwardingAdd = upnpPortForwardingAddFactory.create(service,
                upnpService.getControlPoint(), portForwarding, this);
        upnpPortForwardingAdd.run();
        return upnpPortForwardingAdd.getResult();
    }

    void addOrUpdateForwardingForService(UpnpPortForwarding forwarding) {
        boolean found = false;
        for (UpnpPortForwarding existingForwarding : activePortForwardings) {
            // Only port and protocol needed for identification, other
            // attributes can change
            if (existingForwarding.getExternalPort().equals(forwarding.getExternalPort())
                    && existingForwarding.getProtocol().equals(forwarding.getProtocol())) {
                existingForwarding.setDescription(forwarding.getDescription());
                existingForwarding.setInternalClient(forwarding.getInternalClient());
                existingForwarding.setInternalPort(forwarding.getInternalPort());
                existingForwarding.setLeaseDurationSeconds(forwarding.getLeaseDurationSeconds());
                existingForwarding.setPermanent(forwarding.isPermament());
                found = true;
            }
        }
        if (!found) {
            activePortForwardings.add(forwarding);
        }
    }

    private Service<?, RemoteService> findService(Device gatewayDevice) {
        Service service = gatewayDevice.findService(IP_SERVICE_TYPE);
        if (service == null) {
            service = gatewayDevice.findService(PPP_SERVICE_TYPE);
        }
        // FUTURE: More fallbacks to other service types?
        return service;
    }

    public synchronized List<UpnpPortForwardingResult> removePortForwardings(List<UpnpPortForwarding> portForwardings) {
        List<UpnpPortForwardingResult> results = new ArrayList<>();
        for (UpnpPortForwarding portForwarding : portForwardings) {
            results.addAll(removePortForwarding(portForwarding));
        }
        return results;
    }

    public synchronized List<UpnpPortForwardingResult> removePortForwarding(UpnpPortForwarding portForwarding) {
        Collection<Device> gatewayDevices = getGatewayDevices();
        List<UpnpPortForwardingResult> results = new ArrayList<>();
        for (Device gatewayDevice : gatewayDevices) {
            Device[] connectionDevices = gatewayDevice.findDevices(CONNECTION_DEVICE_TYPE);
            if (connectionDevices.length == 0 || connectionDevices[0] == null) {
                log.error("Could not find connection devices for device {}", gatewayDevice);
                continue;
            }
            Device connectionDevice = connectionDevices[0];
            Service service = findService(connectionDevice);
            if (service == null) {
                log.error("Could not find proper service for device {}", gatewayDevice);
            } else {
                UpnpPortForwardingResult tmpResult = deletePortForwarding(portForwarding, service);
                results.add(tmpResult);
                // There could be several such devices present, do not try to
                // remove a forwarding from additional devices after is has been
                // removed from one
                if (tmpResult.isSuccess()) {
                    break;
                }
            }
        }
        if (results.isEmpty()) {
            results.add(new UpnpPortForwardingResult(portForwarding, false, ERROR_NO_DEVICE_FOUND));
        } else if (results.stream().filter(f -> !f.isSuccess()).findAny().isPresent()) {
            // If there are errors, load all existing forwardings. See if
            // the failures are still existing or not (if they did not exist in
            // the first place, they could not be removed, thus causing a
            // failure. But since they are gone now, that is alright)
            List<UpnpPortForwarding> existingForwardings = getPortForwardings();
            // Any failure not in the existingForwardings is not a failure
            for (UpnpPortForwardingResult result : results) {
                if (!result.isSuccess()) {
                    // If there is one with identical ports and host, it really
                    // is a failure.
                    result.setSuccess(!existingForwardings.stream()
                            .filter(e -> e == result.getCorrespondingPortForwarding()).findAny().isPresent());
                    if (result.isSuccess()) {
                        log.debug("Error during removal considered success due to forwarding not existing anymore",
                                result);
                        // This forwarding was not removed from the cached list
                        // of forwardings since it was not considered a success
                        // yet, do it now
                        removeForwardingForService(result.getCorrespondingPortForwarding());
                    }
                }
            }
        }
        return results;
    }

    public synchronized void watchdog() {
        // The search for actually active port forwardings is expensive and
        // generates network traffic.
        // So we use a short cut, if we do not expect and need any port
        // forwardings.
        if (activePortForwardings.isEmpty()) {
            return;
        }
        // Find out if any expected forwardings are missing
        List<UpnpPortForwarding> actual = getPortForwardings();
        List<UpnpPortForwarding> refresh = new ArrayList<>();
        for (UpnpPortForwarding expectedForwarding : activePortForwardings) {
            if (!actual.contains(expectedForwarding)) {
                // Forwarding lost for some unknown reason
                refresh.add(expectedForwarding);
                log.debug("Watchdog: Refresh forwarding ext port {} int port {} int host {}",
                        expectedForwarding.getExternalPort(), expectedForwarding.getInternalPort(),
                        expectedForwarding.getInternalClient());
            }
        }
        // All forwardings in refresh must be refreshed
        addPortForwardings(refresh);
    }

    private synchronized void onIpAddressChange(IpAddress newIp) {
        if (!newIp.isIpv4()) {
            log.error("Need IPv4 address for UPnP, but got {} - ignoring IP change", newIp);
            return;
        }
        log.info("Changed IP from {} to {}", ip, newIp);
        List<UpnpPortForwarding> changedForwardings = new ArrayList<>();
        List<UpnpPortForwarding> toBeDeleted = new ArrayList<>();
        for (UpnpPortForwarding forwarding : activePortForwardings) {
            if (forwarding.getInternalClient().equals(ip.toString())
                    && !forwarding.getInternalClient().equals(newIp.toString())) {
                toBeDeleted.add(forwarding);
                UpnpPortForwarding newForwarding = new UpnpPortForwarding(forwarding);
                newForwarding.setInternalClient(newIp.toString());
                changedForwardings.add(newForwarding);
            }
        }
        removePortForwardings(toBeDeleted);
        addPortForwardings(changedForwardings);
        ip = newIp;
    }

    private UpnpPortForwardingResult deletePortForwarding(UpnpPortForwarding portForwarding, Service service) {
        UpnpPortForwardingDelete upnpPortForwardingDelete = upnpPortForwardingDeleteFactory.create(service,
                upnpService.getControlPoint(), portForwarding, this);
        upnpPortForwardingDelete.run();
        return upnpPortForwardingDelete.getResult();
    }

    void removeForwardingForService(UpnpPortForwarding forwarding) {
        // Only keep forwardings that are different
        activePortForwardings.removeAll(Collections.singleton(forwarding));
    }

    public List<UpnpPortForwarding> getPortForwardings() {
        List<UpnpPortForwarding> results = new ArrayList<>();
        Collection<Device> gatewayDevices = getGatewayDevices();
        for (Device gatewayDevice : gatewayDevices) {
            Device[] connectionDevices = gatewayDevice.findDevices(CONNECTION_DEVICE_TYPE);
            if (connectionDevices.length == 0 || connectionDevices[0] == null) {
                log.error("Could not find connection devices for device {}", gatewayDevice);
                continue;
            }
            Device connectionDevice = connectionDevices[0];
            Service service = findService(connectionDevice);
            if (service == null) {
                log.error("Could not find proper service for device {}", gatewayDevice);
                continue;
            }
            Action<RemoteService> action = service.getAction("GetGenericPortMappingEntry");
            // Try all entries...
            for (int currEntry = 0; currEntry < PORT_FORWARDINGS_MAX_NUM_EXPECTED; currEntry++) {
                ActionInvocation<RemoteService> getForwardingInvocation = upnpActionInvocationFactory.create(action);
                // Set parameter
                getForwardingInvocation.setInput("NewPortMappingIndex", new UnsignedIntegerTwoBytes(currEntry));
                upnpActionCallbackFactory.create(getForwardingInvocation, upnpService.getControlPoint()).run();
                if (getForwardingInvocation.getFailure() != null) {
                    break;
                }
                results.add(convertInvocationToPortForwarding(getForwardingInvocation));
            }
        }
        return results;
    }

    private UpnpPortForwarding convertInvocationToPortForwarding(ActionInvocation<?> getForwardingInvocation) {
        UpnpPortForwarding newForwarding = new UpnpPortForwarding();
        newForwarding.setDescription(getStringValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_DESCRIPTION));
        newForwarding.setEnabled(getBooleanValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_ENABLED));
        newForwarding
                .setExternalPort(getUIntTwoBytesValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT));
        newForwarding
                .setInternalClient(getStringValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT));
        newForwarding
                .setInternalPort(getUIntTwoBytesValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_INTERNAL_PORT));
        newForwarding.setLeaseDurationSeconds(
                getUIntFourBytesValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_DURATION));
        newForwarding.setProtocol(PortMapping.Protocol
                .valueOf(getStringValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_PROTOCOL)));
        newForwarding.setRemoteHost(getStringValue(getForwardingInvocation, RESULT_KEY_PORT_MAPPING_REMOTE_HOST));

        return newForwarding;
    }

    private boolean getBooleanValue(final ActionInvocation<?> response, final String argumentName) {
        return (boolean) response.getOutput(argumentName).getValue();
    }

    private UnsignedIntegerTwoBytes getUIntTwoBytesValue(final ActionInvocation<?> response,
                                                         final String argumentName) {
        return (UnsignedIntegerTwoBytes) response.getOutput(argumentName).getValue();
    }

    private UnsignedIntegerFourBytes getUIntFourBytesValue(final ActionInvocation<?> response,
                                                           final String argumentName) {
        return (UnsignedIntegerFourBytes) response.getOutput(argumentName).getValue();
    }

    private String getStringValue(final ActionInvocation<?> response, final String argumentName) {
        return (String) response.getOutput(argumentName).getValue();
    }

    public List<UpnpPortForwarding> findExistingForwardingsBlockingRequest(int externalPort, int internalPort) {
        String eblockerIp = networkInterfaceWrapper.getFirstIPv4Address().toString();
        UnsignedIntegerTwoBytes extPort = new UnsignedIntegerTwoBytes(externalPort);
        UnsignedIntegerTwoBytes intPort = new UnsignedIntegerTwoBytes(internalPort);

        List<UpnpPortForwarding> existingForwardings = getPortForwardings();

        return existingForwardings.stream()
                .filter(f -> (f.getExternalPort().equals(extPort)
                        || (f.getInternalPort().equals(intPort) && f.getInternalClient().equals(eblockerIp))))
                .collect(Collectors.toList());
    }

}
