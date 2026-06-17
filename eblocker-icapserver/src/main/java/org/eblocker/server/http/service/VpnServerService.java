/*
 * Copyright 2026 eBlocker Open Source GmbH
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

import org.eblocker.server.common.data.vpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.upnp.UpnpManagementService;
import org.eblocker.server.upnp.UpnpPortForwardingResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for eBlocker Mobile services that handles common tasks,
 * e.g. port forwarding.
 */
public abstract class VpnServerService {
    private static final String ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS = "ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CONFLICTING_FORWARDINGS";

    protected final UpnpManagementService upnpService;
    protected final int serverPort;
    protected final String portForwardingDescription;
    protected final int portForwardingTempDuration;
    protected final int duration;
    protected List<UpnpPortForwardingResult> openedPorts;

    protected abstract int getMappedPort();
    protected abstract PortForwardingMode getPortForwardingMode();

    public VpnServerService(UpnpManagementService upnpService, int serverPort, String portForwardingDescription, int tempDuration, int duration) {
        this.upnpService = upnpService;
        this.serverPort = serverPort;
        this.portForwardingDescription = portForwardingDescription;
        this.portForwardingTempDuration = tempDuration;
        this.duration = duration;
    }

    public void enablePortForwarding() throws UpnpPortForwardingException {
        // Enable port forwarding
        if (getPortForwardingMode() == PortForwardingMode.AUTO) {
            int externalPort = getMappedPort();
            openedPorts = upnpService.addPortForwarding(externalPort, serverPort, duration, portForwardingDescription);

            // Check if opening the ports succeeded or if there was a problem
            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, serverPort).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS);
                }
                throw new UpnpPortForwardingException(failedOpening.getErrorMsg());
            }
        } else if (openedPorts != null) {
            openedPorts.clear();
        }
    }

    public void disablePortForwarding() throws UpnpPortForwardingException {
        // Remove port forwarding
        if (openedPorts != null && !openedPorts.isEmpty()) {
            List<UpnpPortForwardingResult> closedPorts = upnpService.removePortForwardings(
                    openedPorts.stream().map(UpnpPortForwardingResult::getCorrespondingPortForwarding).collect(Collectors.toList()));

            // If the closing failed, notify the user
            UpnpPortForwardingResult failedRemoval = closedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedRemoval != null) {
                throw new UpnpPortForwardingException(failedRemoval.getErrorMsg());
            }
        }
    }

    public void setAndMapExternalPortTemporarily(Integer externalPort) throws UpnpPortForwardingException {
        if (getPortForwardingMode() == PortForwardingMode.AUTO) {
            openedPorts = upnpService.addPortForwarding(externalPort, serverPort, portForwardingTempDuration,
                    portForwardingDescription);

            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, serverPort).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS);
                }
                throw new UpnpPortForwardingException(failedOpening.getErrorMsg());
            }
        }
    }
}
