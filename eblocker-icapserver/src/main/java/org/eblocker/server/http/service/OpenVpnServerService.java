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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.openvpn.server.OpenVpnCa;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.upnp.UpnpManagementService;
import org.eblocker.server.upnp.UpnpPortForwardingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

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
    private final OpenVpnCa openVpnCa;
    private final int port;
    private final String portForwardingDescription;
    private final int portForwardingTempDuration;
    private final int duration;
    private List<UpnpPortForwardingResult> openedPorts;

    private int tempPort;
    private static final Logger log = LoggerFactory.getLogger(OpenVpnServerService.class);
    private static final String ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS = "ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CONFLICTING_FORWARDINGS";

    @Inject
    public OpenVpnServerService(ScriptRunner scriptRunner, DataSource dataSource,
                                UpnpManagementService upnpService,
                                EblockerDnsServer dnsServer,
                                DnsService dnsService,
                                @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                EventLogger eventLogger,
                                @Named("openvpn.server.command") String openVpnServerCommand,
                                @Named("openvpn.server.port") int port,
                                @Named("openvpn.server.portforwarding.duration.initial") int tempDuration,
                                @Named("openvpn.server.portforwarding.duration.use") int duration,
                                @Named("openvpn.server.portforwarding.description") String portForwardingDescription,
                                OpenVpnCa openVpnCa) {
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
        this.openVpnCa = openVpnCa;
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

        if (isOpenVpnServerfirstRun()) {
            try {
                openVpnCa.generateCa();
                openVpnCa.generateServerCertificate();
            } catch (Exception e) {
                log.error("OpenVPN CA could not be initialized.", e);
                openVpnCa.tearDown();
                return false;
            }
            if (!vpnServerControl("init")) {
                log.error("OpenVPN-server could not be initialized.");
                return false;
            }
        }

        if (vpnServerControl("start")) {
            setOpenVpnServerfirstRun(false);
            enableOpenVpnServer();

            result = true;
        }

        return result;
    }

    private boolean vpnServerControl(String mode) {
        try {
            return (scriptRunner.runScript(openVpnServerCommand, mode) == 0);
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
            openedPorts = upnpService.addPortForwarding(externalPort, port, duration, portForwardingDescription);

            // Check if opening the ports succeeded or if there was a problem
            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, port).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS);
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
                    portForwardingDescription);

            UpnpPortForwardingResult failedOpening = openedPorts.stream().filter(res -> !res.isSuccess()).findFirst()
                    .orElse(null);
            if (failedOpening != null) {
                // Analyse situation - maybe we can give the user a hint
                if (!upnpService.findExistingForwardingsBlockingRequest(externalPort, port).isEmpty()) {
                    throw new UpnpPortForwardingException(ERROR_MSG_POTENTIALLY_CONFLICTING_FORWARDINGS);
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

    /**
     * Get running status of the OpenVPN server
     * @return true if the OpenVPN server is running, false otherwise
     */
    public boolean getOpenVpnServerStatus() {
        return vpnServerControl("status");
    }

    public boolean purgeOpenVpnServer() {
        openVpnCa.tearDown();
        return vpnServerControl("purge");
    }

    public boolean createClientCertificate(String deviceId) {
        try {
            openVpnCa.generateClientCertificate(deviceId);
        } catch (Exception e) {
            log.error("Could not generate client certificate for {}", deviceId, e);
            return false;
        }
        return true;
    }

    public boolean revokeClientCertificate(String deviceId) {
        try {
            openVpnCa.revokeClientCertificate(deviceId);
        } catch (Exception e) {
            log.error("Could not revoke client certificate for {}", deviceId, e);
            return false;
        }
        return vpnServerControl("update-crl");
    }

    public Set<String> getDeviceIdsWithCertificates() throws IOException {
        return openVpnCa.getActiveClientIds();
    }
}
