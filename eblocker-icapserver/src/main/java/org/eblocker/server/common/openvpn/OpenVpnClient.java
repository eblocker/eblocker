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
package org.eblocker.server.common.openvpn;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.KeepAliveMode;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.executor.NamedRunnable;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class OpenVpnClient implements VpnClient, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(OpenVpnClient.class);

    private final String startInstanceScript;
    private final String killProcessScript;

    private final OpenVpnProfileFiles profileFiles;
    private final ScriptRunner scriptRunner;
    private final NetworkStateMachine networkStateMachine;
    private final DataSource dataSource;
    private final SquidConfigController squidConfigController;
    private final RoutingController routingController;
    private final Executor executor;
    private final EblockerDnsServer eblockerDnsServer;
    private final DeviceService deviceService;
    private final VpnKeepAliveFactory vpnKeepAliveFactory;

    private enum State {STOPPED, STARTING, OVPN_RUNNING, VPN_UP, VPN_DOWN, KILLING, OVPN_DEAD, CLOSED}

    private volatile State state = State.STOPPED;

    private OpenVpnChannel subscriber;

    private OpenVpnClientState clientState;
    private LoggingProcess process;
    private Integer exitStatus;
    private Instant stopInstant;

    private VpnProfile vpnProfile;
    private Set<String> deviceIds = new HashSet<>();
    private VpnKeepAlive vpnKeepAlive;
    private boolean restartOnExit;

    @Inject
    OpenVpnClient(@Named("start.openvpn.instance.command") String startInstanceScript,
                  @Named("kill.process.command") String killProcessScript,
                  OpenVpnProfileFiles profileFiles,
                  ScriptRunner scriptRunner,
                  NetworkStateMachine networkStateMachine,
                  DataSource dataSource,
                  SquidConfigController squidConfigController,
                  RoutingController routingController,
                  OpenVpnChannelFactory openVpnChannelSubscriberFactory,
                  @Named("unlimitedCachePoolExecutor") Executor executor,
                  EblockerDnsServer eblockerDnsServer,
                  DeviceService deviceService,
                  VpnKeepAliveFactory vpnKeepAliveFactory,
                  @Assisted VpnProfile vpnProfile) {
        this.startInstanceScript = startInstanceScript;
        this.killProcessScript = killProcessScript;

        this.profileFiles = profileFiles;
        this.scriptRunner = scriptRunner;
        this.networkStateMachine = networkStateMachine;
        this.dataSource = dataSource;
        this.squidConfigController = squidConfigController;
        this.routingController = routingController;
        this.executor = executor;
        this.eblockerDnsServer = eblockerDnsServer;
        this.deviceService = deviceService;
        this.vpnKeepAliveFactory = vpnKeepAliveFactory;

        this.vpnProfile = vpnProfile;

        subscriber = openVpnChannelSubscriberFactory.create(vpnProfile.getId(), new ChannelListener());

        clientState = new OpenVpnClientState();
        clientState.setId(vpnProfile.getId());
        clientState.setDevices(deviceIds);
        clientState.setRoute(routingController.createRoute());
        saveClientState();
    }

    @Override
    public VpnProfile getProfile() {
        return vpnProfile;
    }

    @Override
    public boolean isUp() {
        return state == State.VPN_UP;
    }

    @Override
    public boolean isStopped() {
        return state == State.STOPPED;
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public Instant getStopInstant() {
        return stopInstant;
    }

    @Override
    public List<String> getLog() {
        try {
            return profileFiles.readLogFile(vpnProfile.getId());
        } catch (IOException e) {
            logger.error("Reading log file failed", e);
            return Collections.singletonList("Reading log file failed.");
        }
    }

    @Override
    public Integer getExitStatus() {
        return exitStatus;
    }

    @Override
    public Set<String> getDeviceIds() {
        return Collections.unmodifiableSet(deviceIds);
    }

    @Override
    public void addDevices(Collection<Device> devices) {
        executeLocked("addDevices", () -> {
            reloadProfile();
            synchronized (OpenVpnClient.class) {
                for (Device device : devices) {
                    logger.info("device {} added to vpn {}", device.getHardwareAddress(), vpnProfile.getId());
                    if (deviceIds.contains(device.getId())) {
                        logger.error("device {} already using vpn {}!", device, vpnProfile.getId());
                        continue;
                    }

                    deviceIds.add(device.getId());
                    clientState.setDevices(deviceIds);
                    saveClientState();
                    if (vpnProfile.isNameServersEnabled()) {
                        eblockerDnsServer.useVpnResolver(device, vpnProfile.getId());
                    }
                }

                if (state == State.STOPPED) {
                    start();
                }

                reconfigureAclsAndSquid();
                networkStateMachine.deviceStateChanged();
            }
        });
    }

    @Override
    public void stopDevices(Collection<Device> devices) {
        executeLocked("stopDevices", () -> {
            synchronized (OpenVpnClient.class) {
                clearAndResetDevices(devices.stream().map(Device::getId).collect(Collectors.toSet()));
                if (deviceIds.isEmpty() && state != State.STOPPED) {
                    stop();
                }
            }
        });
    }

    @Override
    public synchronized void close() {
        if (state == State.STOPPED) {
            state = State.CLOSED;

            logger.info("returning route {} to route controller", clientState.getRoute());
            routingController.deleteRoute(clientState.getRoute());
        }
    }

    private void clearAndResetDevices(Collection<String> deviceIds) {
        for (String deviceId : deviceIds) {
            logger.info("stopping device {} from using vpn {}", deviceId, vpnProfile.getId());

            if (!this.deviceIds.remove(deviceId)) {
                logger.warn("cannot remove device {} from vpn {}: it is not using it!", deviceId, vpnProfile.getId());
            }

            Device device = deviceService.getDeviceById(deviceId);
            if (device == null) {
                logger.warn("device {} using vpn {} unknown?!", deviceId, vpnProfile.getId());
            } else {
                eblockerDnsServer.useDefaultResolver(device);
            }
        }

        clientState.setDevices(this.deviceIds);
        saveClientState();
        reconfigureAclsAndSquid();
        networkStateMachine.deviceStateChanged();
    }

    @Override
    public void start() {
        executeLocked("start", () -> {
            assertTransitionValidToFrom(State.STARTING, State.STOPPED);

            state = State.STARTING;
            logger.info("starting vpn {}", vpnProfile.getId());

            // reload profile to ensure it's up-to-date
            reloadProfile();

            File credentialsFile = profileFiles.writeTransientCredentialsFile(vpnProfile);
            subscriber.start();

            try {
                int id = vpnProfile.getId();
                profileFiles.truncateLogFile(id);
                exitStatus = null;
                stopInstant = null;

                process = scriptRunner.startScript(startInstanceScript, String.valueOf(id), profileFiles.getConfig(id), profileFiles.getLogFile(id));

                assertTransitionValidToFrom(State.OVPN_RUNNING, State.STARTING);
                state = State.OVPN_RUNNING;

                executor.execute(new NamedRunnable("openvpn-" + id + "-process", () -> {
                    try {
                        exitStatus = process.waitFor();
                        logger.info("openvpn for vpn {} process exited with status {}", vpnProfile.getId(), exitStatus);
                    } catch (InterruptedException e) {
                        logger.debug("openvpn for vpn {} thread interrupted", vpnProfile.getId(), e);
                        Thread.currentThread().interrupt();
                    }
                    handleProcessExit(credentialsFile);
                }));
            } catch (IOException e) {
                logger.warn("failed to start openvpn {}", vpnProfile.getId(), e);
                handleProcessExit(credentialsFile);
            }
        });
    }

    private void reloadProfile() {
        vpnProfile = dataSource.get(OpenVpnProfile.class, vpnProfile.getId());
    }

    private synchronized void handleProcessExit(File credentialsFile) {
        assertTransitionValidToFrom(State.STOPPED, State.OVPN_RUNNING, State.VPN_UP, State.VPN_DOWN, State.OVPN_DEAD);

        logger.debug("handling process exit with {}", exitStatus);

        if (exitStatus != 0) {
            logger.warn("openvpn instance died with exit value {}!", exitStatus);
        }

        if (restartOnExit) {
            clientState.setState(OpenVpnClientState.State.PENDING_RESTART);
        } else {
            clientState.setState(OpenVpnClientState.State.INACTIVE);
            clearAndResetDevices(deviceIds);
        }

        // unroute the clients
        unrouteClients();
        subscriber.stop();
        stopKeepAlive();
        if (credentialsFile != null) {
            credentialsFile.delete();
        }

        state = State.STOPPED;
        stopInstant = Instant.now();

        logger.debug("process exit done");

        if (restartOnExit) {
            logger.debug("requesting restart");
            start();
            restartOnExit = false;
        }
    }

    @Override
    public void stop() {
        executeLocked("stop", () -> {
            assertTransitionValidToFrom(State.OVPN_DEAD, State.OVPN_RUNNING, State.VPN_UP, State.VPN_DOWN, State.STARTING);

            logger.info("killing openvpn profile {} process.", vpnProfile.getId());
            stopKeepAlive();

            if (process == null) {
                logger.warn("no running openvpn process known!");
            } else if (!process.isAlive()) {
                logger.info("process already dead");
            } else {
                logger.info("killing process {}", process.getPid());
                // using process.destroy or process.destroyForcibly does not kill the process reliable
                try {
                    scriptRunner.runScript(killProcessScript, String.valueOf(process.getPid()));
                } catch (IOException e) {
                    logger.error("failed to stop vpn instance!", e);
                    state = State.STOPPED;
                    stopInstant = Instant.now();
                } catch (InterruptedException e) {
                    logger.debug("Stopping vpn instance interruppted!", e);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private synchronized void stopKeepAlive() {
        if (vpnKeepAlive != null) {
            vpnKeepAlive.stop();
            vpnKeepAlive = null;
        }
    }

    private void reconfigureAclsAndSquid() {
        Set<Device> devices = deviceIds.stream().map(deviceService::getDeviceById).filter(Objects::nonNull).collect(Collectors.toSet());
        squidConfigController.updateVpnDevicesAcl(vpnProfile.getId(), devices);
    }

    private void routeClients() {
        logger.info("VPN profile/instance (connection) just came (back) up... Tell clients they can use it again...");

        logger.info("setting up routes ...");
        routingController.setClientRoute(clientState.getRoute(), clientState.getVirtualInterfaceName(), clientState.getRouteNetGateway(), clientState.getRouteVpnGateway(), clientState.getTrustedIp());

        //rewrite squid config to integrate "SNAT" to link-local address for all MAC addresses from ACL file
        logger.info("Telling squid to adapt its squid.conf, cause there might be new activated VPN profiles" +
                " and/or changed list of MAC address in any of the VPN ACL files!");
        squidConfigController.updateSquidConfig();

        //add firewall rules to redirect traffic of this client/device
        //  (and the connected link-local address when squid is done) through VPN tunnel
        logger.info("Adapting firewall rules to enable the routing of the packets!");
        networkStateMachine.deviceStateChanged();

        //configure name servers (if available)
        if (!clientState.getNameServers().isEmpty()) {
            eblockerDnsServer.addVpnResolver(clientState.getId(), clientState.getNameServers(), clientState.getLocalEndpointIp());
        }
    }

    private void unrouteClients() {
        logger.info("Unrouting all client(s)...");

        if (clientState.getTrustedIp() != null) {
            logger.info("tear-down routes ...");
            routingController.clearClientRoute(clientState.getRoute(), clientState.getTrustedIp());
        } else {
            logger.info("no route active");
        }

        //adapt firewall rules
        networkStateMachine.deviceStateChanged();

        //TODO clean, delete acl files necessary or cause they will be overwritten anyway we dont have to take care here?!
        //tell squid to adapt the squid.conf
        squidConfigController.updateSquidConfig();

        // remove nameservers
        eblockerDnsServer.removeVpnResolver(clientState.getId());
    }

    private void saveClientState() {
        dataSource.save(clientState, clientState.getId());
    }

    //
    // callbacks from state listener
    //
    private class ChannelListener implements OpenVpnChannelListener {
        public void reportPid(int pid) {
            logger.debug("vpn {} reported pid: {}", vpnProfile.getId(), pid);
            if (pid != process.getPid()) {
                logger.error("vpn reported pid {} does not match process pid {}", pid, process.getPid());
            }
        }

        public void up(String virtualInterfaceName, String routeNetGateway, String routeVpnGateway, String ifconfigLocal, String trustedIp, List<String> nameServers) {
            executeLocked("up", () -> {
                assertTransitionValidToFrom(State.VPN_UP, State.OVPN_RUNNING, State.VPN_DOWN);

                logger.info("vpn {} up", vpnProfile.getId());
                state = State.VPN_UP;
                clientState.setState(OpenVpnClientState.State.ACTIVE);
                clientState.setVirtualInterfaceName(virtualInterfaceName);
                clientState.setRouteNetGateway(routeNetGateway);
                clientState.setRouteVpnGateway(routeVpnGateway);
                clientState.setLocalEndpointIp(ifconfigLocal);
                clientState.setTrustedIp(trustedIp);
                clientState.setNameServers(nameServers);
                saveClientState();
                routeClients();

                stopKeepAlive();
                if (KeepAliveMode.DISABLED != vpnProfile.getKeepAliveMode()) {
                    logger.info("starting keep alive ping to {}", vpnProfile.getKeepAlivePingTarget());
                    vpnKeepAlive = vpnKeepAliveFactory.create(virtualInterfaceName, vpnProfile.getKeepAlivePingTarget(), () -> {
                        restartOnExit = true;
                        stop();
                    });
                    vpnKeepAlive.start();
                }
            });
        }

        public void down(String reason) {
            executeLocked("down", () -> {
                assertTransitionValidToFrom(State.VPN_DOWN, State.VPN_UP, State.OVPN_RUNNING);

                logger.info("vpn {} down", vpnProfile.getId());
                state = State.VPN_DOWN;
            });
        }
    }

    private void executeLocked(String taskName, Runnable runnable) {
        executor.execute(new NamedRunnable("openvpn-" + vpnProfile.getId() + "-" + taskName, () -> {
            synchronized (OpenVpnClient.this) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    logger.error("exception running task", e);
                }
            }
        }));
    }

    //
    // exceptions
    //
    private void assertTransitionValidToFrom(State target, State... sourceStates) {
        for (State validSourceState : sourceStates) {
            if (validSourceState == state) {
                return;
            }
        }

        throw new IllegalTransitionException(state, target);
    }

    @SuppressWarnings("serial")
    public class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(State source, State target) {
            super("Illegal state transition from " + source + " to " + target + " !");
        }
    }
}
