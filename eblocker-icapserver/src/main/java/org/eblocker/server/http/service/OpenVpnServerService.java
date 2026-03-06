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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.openvpn.server.OpenVpnCa;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.upnp.UpnpManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class OpenVpnServerService extends VpnServerService {
    private final DataSource dataSource;
    private final ScriptRunner scriptRunner;
    private final DeviceService deviceService;
    private final EblockerDnsServer dnsServer;
    private final DnsService dnsService;
    private final DynDnsService dynDnsService;
    private final ScheduledExecutorService executorService;
    private final EventLogger eventLogger;
    private final String openVpnServerCommand;
    private final OpenVpnCa openVpnCa;

    private static final Logger log = LoggerFactory.getLogger(OpenVpnServerService.class);

    @Inject
    public OpenVpnServerService(ScriptRunner scriptRunner, DataSource dataSource,
                                DeviceService deviceService,
                                UpnpManagementService upnpService,
                                EblockerDnsServer dnsServer,
                                DnsService dnsService,
                                DynDnsService dynDnsService,
                                @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                EventLogger eventLogger,
                                @Named("openvpn.server.command") String openVpnServerCommand,
                                @Named("openvpn.server.port") int port,
                                @Named("openvpn.server.portforwarding.duration.initial") int tempDuration,
                                @Named("openvpn.server.portforwarding.duration.use") int duration,
                                @Named("openvpn.server.portforwarding.description") String portForwardingDescription,
                                OpenVpnCa openVpnCa) {
        super(upnpService, port, portForwardingDescription, tempDuration, duration);
        this.dataSource = dataSource;
        this.scriptRunner = scriptRunner;
        this.deviceService = deviceService;
        this.dnsServer = dnsServer;
        this.executorService = executorService;
        this.dnsService = dnsService;
        this.dynDnsService = dynDnsService;
        this.openVpnServerCommand = openVpnServerCommand;
        this.eventLogger = eventLogger;
        this.openVpnCa = openVpnCa;
    }

    @SubSystemInit
    public void init() {
        initDeviceListener();
        // Start server if needed
        if (isOpenVpnServerEnabled()) {
            Runnable task = this::initStartOpenVpnServer;
            executorService.execute(task);
        }
    }

    private void initDeviceListener() {
        this.deviceService.addListener(new DeviceService.DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                // Nothing to do here.
            }

            @Override
            public void onDelete(Device device) {
                try {
                    if (getDeviceIdsWithCertificates().contains(device.getId())) {
                        revokeClientCertificate(device.getId());
                    }
                } catch (IOException e) {
                    log.error("Could not find out whether device {} has any client certificates for OpenVPN server.", device.getId(), e);
                }
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });
    }

    private void initStartOpenVpnServer() {
        startOpenVpnServer();
        try {
            enablePortForwarding();
        } catch (UpnpPortForwardingException e) {
            log.error("Problem starting the OpenVPN Server occured", e);
            eventLogger.log(Events.upnpPortForwardingFailed());
        }
    }

    private boolean startOpenVpnServer() {
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
            dataSource.setOpenVpnServerState(true);
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

    public VpnServerStatus getOpenVpnServerStatus() {
        VpnServerStatus result = new VpnServerStatus();
        result.setFirstStart(isOpenVpnServerfirstRun());
        result.setHost(getOpenVpnServerHost());
        result.setRunning(isOpenVpnServerRunning());
        result.setExternalAddressType(getOpenVpnExternalAddressType());
        result.setMappedPort(getOpenVpnMappedPort());
        result.setPortForwardingMode(getOpenVpnPortForwardingMode());
        return result;
    }

    public VpnServerStatus setOpenVpnServerStatus(VpnServerStatus requestedStatus) {
        VpnServerStatus result = updateServerAccess(requestedStatus);

        // Status of the server:
        if (isOpenVpnServerRunning() == requestedStatus.isRunning()) {
            result.setRunning(requestedStatus.isRunning()); // no update of server status necessary
        } else {
            if (requestedStatus.isRunning()) {
                // start server
                result.setRunning(startOpenVpnServer());
            } else {
                // stop server
                boolean stopped = stopOpenVpnServer();
                result.setRunning(!stopped);
                if (stopped) {
                    disableOpenVpnServer();
                }
            }
        }

        result.setFirstStart(isOpenVpnServerfirstRun());

        return result;

    }

    private VpnServerStatus updateServerAccess(VpnServerStatus requestedStatus) {
        VpnServerStatus result = new VpnServerStatus();
        if (requestedStatus.getExternalAddressType() == ExternalAddressType.EBLOCKER_DYN_DNS) {
            if (!dynDnsService.isEnabled()) {
                dynDnsService.enable();
                dynDnsService.update();
            }
            setOpenVpnServerHost(dynDnsService.getHostname());
        } else {
            if (dynDnsService.isEnabled()) {
                dynDnsService.disable();
            }
            String newHost = requestedStatus.getHost() != null ? requestedStatus.getHost() : "";
            setOpenVpnServerHost(newHost);
        }
        result.setHost(getOpenVpnServerHost());

        setOpenVpnExternalAddressType(requestedStatus.getExternalAddressType());
        result.setExternalAddressType(requestedStatus.getExternalAddressType());

        Integer mappedPort = requestedStatus.getMappedPort();
        if (mappedPort != null) {
            setOpenVpnMappedPort(mappedPort);
        }
        result.setMappedPort(mappedPort);
        result.setPortForwardingMode(requestedStatus.getPortForwardingMode());

        setOpenVpnPortForwardingMode(requestedStatus.getPortForwardingMode());
        return result;
    }

    private void disableOpenVpnServer() {
        deviceService.getDevices(false).stream()
                .forEach(device -> device.setIsVpnClient(false));

        dataSource.setOpenVpnServerState(false);
    }

    public boolean isOpenVpnServerEnabled() {
        return dataSource.getOpenVpnServerState();
    }

    private boolean isOpenVpnServerfirstRun() {
        return dataSource.getOpenVpnServerFirstRun();
    }

    private void setOpenVpnServerfirstRun(boolean state) {
        dataSource.setOpenVpnServerFirstRun(state);
    }

    private void setOpenVpnServerHost(String host) {
        dataSource.setOpenVpnServerHost(host);
    }

    public String getOpenVpnServerHost() {
        return dataSource.getOpenVpnServerHost();
    }

    private ExternalAddressType getOpenVpnExternalAddressType() {
        return dataSource.getOpenVpnExternalAddressType();
    }

    private void setOpenVpnExternalAddressType(ExternalAddressType type) {
        dataSource.setOpenVpnExternalAddressType(type);
    }

    /**
     * Reset the OpenVPN server and CA to the factory state. The server is stopped and disabled.
     * All CA, server and client certificates and keys are removed.
     * @return true if reset was successful
     */
    public boolean resetOpenVpnServer() {
        boolean result;

        // first we set 'first-run' to true. So if anything goes wrong during the purge, the next restart
        // of eBlocker mobile should clean up anything that is left.
        setOpenVpnServerfirstRun(true);
        result = stopOpenVpnServer();
        if (result) {
            // save consistent reset-state in redis: to avoid eBlocker mobile to be re-enabled
            // when the ICAP server boots after the reset.
            disableOpenVpnServer();

            try {
                disablePortForwarding();
            } catch (UpnpPortForwardingException e) {
                log.error("Unable to reset port forwarding during eBlocker mobile reset", e);
            }
            // even if port forwarding has not been removed, we have already disabled the server,
            // so we want to continue the reset.
            result = purgeOpenVpnServer();
        }
        return result;
    }

    /**
     * Stops the OpenVPN server
     * @return returns true if the server was stopped or not running, false in case of an error
     */
    private boolean stopOpenVpnServer() {
        return vpnServerControl("stop");
    }

    /**
     * Get running status of the OpenVPN server
     * @return true if the OpenVPN server is running, false otherwise
     */
    private boolean isOpenVpnServerRunning() {
        return vpnServerControl("status");
    }

    private boolean purgeOpenVpnServer() {
        openVpnCa.tearDown();
        return vpnServerControl("purge");
    }

    /**
     * Restores key material for OpenVpnServer:
     * <ul>
     *     <li>CA certificate</li>
     *     <li>Server key and certificate</li>
     *     <li>CRL</li>
     *     <li>Diffie-Hellman parameters</li>
     *     <li>Shared secret</li>
     * </ul>
     * @return
     */
    public boolean restoreOpenVpnServer() {
        return vpnServerControl("restore");
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

    @Override
    protected int getMappedPort() {
        return getOpenVpnMappedPort();
    }

    @Override
    protected PortForwardingMode getPortForwardingMode() {
        return getOpenVpnPortForwardingMode();
    }

    public Integer getOpenVpnMappedPort() {
        Integer mappedPort = dataSource.getOpenVpnMappedPort();
        return mappedPort != null ? mappedPort : this.serverPort;
    }

    private void setOpenVpnMappedPort(Integer port) {
        dataSource.setOpenVpnMappedPort(port);
    }

    public PortForwardingMode getOpenVpnPortForwardingMode() {
        return dataSource.getOpenVpnPortForwardingMode();
    }

    private void setOpenVpnPortForwardingMode(PortForwardingMode mode) {
        dataSource.setOpenVpnPortForwardingMode(mode);
    }
}
