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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.upnp.UpnpManagementService;
import org.eblocker.server.upnp.UpnpPortForwardingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.http.utils.NormalizationUtils;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class OpenVpnServerService {
    private final ScriptRunner scriptRunner;
    private final DataSource dataSource;
    private final UpnpManagementService upnpService;
    private final EblockerDnsServer dnsServer;
    private final DnsService dnsService;
    private final ScheduledExecutorService executorService;
    private final EventLogger eventLogger;
    private final String openVpnServerCommand;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final int port;
    private final String portForwardingDescription;
    private final int portForwardingTempDuration;
    private final int duration;
    private List<UpnpPortForwardingResult> openedPorts;

    private int tempPort;
    private static final Logger log = LoggerFactory.getLogger(OpenVpnServerService.class);
    private static final String ERROR_MSG_POTENTIALLY_CONFLICITNG_FORWARDINGS = "ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CONFLICTING_FORWARDINGS";

    @Inject
    public OpenVpnServerService(ScriptRunner scriptRunner,DataSource dataSource,
                                UpnpManagementService upnpService,
                                EblockerDnsServer dnsServer,
                                DnsService dnsService,
                                @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                EventLogger eventLogger,
                                DeviceRegistrationProperties deviceRegistrationProperties,
                                @Named("openvpn.server.command") String openVpnServerCommand,
                                @Named("openvpn.server.port") int port,
                                @Named("openvpn.server.portforwarding.duration.initial") int tempDuration,
                                @Named("openvpn.server.portforwarding.duration.use") int duration,
                                @Named("openvpn.server.portforwarding.description")String portForwardingDescription) {
        this.scriptRunner = scriptRunner;
        this.dataSource = dataSource;
        this.upnpService = upnpService;
        this.dnsServer = dnsServer;
        this.executorService = executorService;
        this.dnsService = dnsService;
        this.openVpnServerCommand = openVpnServerCommand;
        this.port = port;
        this.tempPort = getOpenVpnMappedPort(); // This fixes EB1-2250 TODO check if tempPort is still needed: here we essentially set tempPort = port
        this.portForwardingDescription = portForwardingDescription;
        this.duration = duration;
        this.portForwardingTempDuration = tempDuration;
        this.eventLogger = eventLogger;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
    }

    @SubSystemInit
    public void init() {
        // Start server if needed
        if (isOpenVpnServerEnabled()) {
            Runnable task = this::initStartOpenVpnServer;
            executorService.execute(task);
        }
    }

    private void initStartOpenVpnServer() {
        try {
            startOpenVpnServer();
        } catch (UpnpPortForwardingException e) {
            log.error("Problem starting the OpenVPN Server occured", e);
            eventLogger.log(Events.upnpPortForwardingFailed());
            // FUTURE: Additional handling?
        }
    }

    public boolean startOpenVpnServer() throws UpnpPortForwardingException {
        boolean result = false;

        if (!dnsServer.isEnabled() && !dnsService.setStatus(true)) {
            log.error("DNS-Server could not be started, so refuse to start openVPN-server.");
            return false;
        }

        if (isOpenVpnServerfirstRun() && !vpnServerControl("init",
                deviceRegistrationProperties.getDeviceRegisteredBy(),
                deviceRegistrationProperties.getDeviceId().substring(0, 8),
                NormalizationUtils.normalizeStringForShellScript(deviceRegistrationProperties.getDeviceName()))) {
            log.error("OpenVPN-server could not be initialized.");
            return false;
        }

        if (vpnServerControl("start")) {
            setOpenVpnServerfirstRun(false);
            enableOpenVpnServer();

            result = true;
        }

        return result;
    }

    private boolean vpnServerControl(String mode, String... parameter) {
        try {
            List<String> list = new ArrayList<>(Arrays.asList(mode));
            list.addAll(Arrays.asList(parameter));

            return (scriptRunner.runScript(openVpnServerCommand, list.toArray(new String[0])) == 0);
        } catch (IOException e) {
            log.error("Could not {} openVPN server", mode, e);
        } catch (InterruptedException e) {
            log.error("VPN server control interrupted while mode: {}", mode, e);
            Thread.currentThread().interrupt();
        }

        return false;
    }

    public void enableOpenVpnServer() throws UpnpPortForwardingException {
        dataSource.setOpenVpnServerState(true);
        // Enable port forwarding
        if (getOpenVpnPortForwardingMode() == PortForwardingMode.AUTO) {
            int externalPort = dataSource.getOpenVpnMappedPort();
            openedPorts = upnpService.addPortForwarding(externalPort, port, duration, portForwardingDescription, true);

            // Check if opening the ports succeeded or if there was a problem
            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, port).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICITNG_FORWARDINGS);
                }
                throw new UpnpPortForwardingException(failedOpening.getErrorMsg());
            }
        } else if (openedPorts != null) {
            openedPorts.clear();
        }
    }

    public void disableOpenVpnServer() throws UpnpPortForwardingException {
        dataSource.setOpenVpnServerState(false);
        // Remove port forwarding
        if (openedPorts != null && !openedPorts.isEmpty()) {
            List<UpnpPortForwardingResult> closedPorts = upnpService.removePortForwardings(
                    openedPorts.stream().map(res -> res.getCorrespondingPortForwarding()).collect(Collectors.toList()));

            // If the closing failed, notify the user
            UpnpPortForwardingResult failedRemoval = closedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedRemoval != null) {
                throw new UpnpPortForwardingException(failedRemoval.getErrorMsg());
            }
        }
    }

    public boolean isOpenVpnServerEnabled() {
        return dataSource.getOpenVpnServerState();
    }

    public boolean isOpenVpnServerfirstRun() {
        return dataSource.getOpenVpnServerFirstRun();
    }

    public void setOpenVpnServerfirstRun(boolean state) {
        dataSource.setOpenVpnServerFirstRun(state);
    }

    public void setOpenVpnServerHost(String host) {
        dataSource.setOpenVpnServerHost(host);
    }

    public String getOpenVpnServerHost() {
        return dataSource.getOpenVpnServerHost();
    }

    public Integer getOpenVpnMappedPort() {
        Integer mappedPort = dataSource.getOpenVpnMappedPort();
        return mappedPort != null ? mappedPort : this.port;
    }

    public void setOpenVpnMappedPort(Integer port) {
        dataSource.setOpenVpnMappedPort(port);
    }

    public void setOpenVpnTempMappedPort(Integer port) {
        tempPort = port;
    }

    public int getOpenVpnTempMappedPort() {
        return tempPort;
    }

    public PortForwardingMode getOpenVpnPortForwardingMode() {
        return dataSource.getOpenVpnPortForwardingMode();
    }

    public void setOpenVpnPortForwardingMode(PortForwardingMode mode) {
        dataSource.setOpenVpnPortForwardingMode(mode);
    }

    public void setAndMapExternalPortTemporarily(Integer externalPort) throws UpnpPortForwardingException {
        tempPort = externalPort;
        if (getOpenVpnPortForwardingMode() == PortForwardingMode.AUTO) {
            openedPorts = upnpService.addPortForwarding(externalPort, port, portForwardingTempDuration,
                    portForwardingDescription, false);

            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, port).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICITNG_FORWARDINGS);
                }
                throw new UpnpPortForwardingException(failedOpening.getErrorMsg());
            }
        }
    }

    public ExternalAddressType getOpenVpnExternalAddressType() {
        return dataSource.getOpenVpnExternalAddressType();
    }

    public void setOpenVpnExternalAddressType(ExternalAddressType type) {
        dataSource.setOpenVpnExternalAddressType(type);
    }

    public boolean stopOpenVpnServer() {
        return vpnServerControl("stop");
    }

    public boolean getOpenVpnServerStatus() {
        return vpnServerControl("status");
    }

    public boolean purgeOpenVpnServer() {
        return vpnServerControl("purge");
    }

    public boolean createClientCertificate(String deviceId, String eBlockerRegisteredBy, String eBlockerShortDeviceId, String eBlockerName) {
        return vpnServerControl("create-client", deviceId, eBlockerRegisteredBy, eBlockerShortDeviceId, eBlockerName);
    }

    public boolean revokeClientCertificate(String deviceId) {
        return vpnServerControl("revoke", deviceId);
    }
}
