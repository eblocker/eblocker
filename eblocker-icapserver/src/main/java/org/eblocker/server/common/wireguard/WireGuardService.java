/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.wireguard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.openvpn.RoutingController;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfigurationParser;
import org.eblocker.server.common.wireguard.configuration.WireGuardPeer;
import org.eblocker.server.common.wireguard.configuration.WireGuardRuntimeConfigurationRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.HTTPS_SERVER, initPriority = 200)
public class WireGuardService {
    private static final Logger log = LoggerFactory.getLogger(WireGuardService.class);

    private final ScriptRunner scriptRunner;
    private final DataSource dataSource;
    private final RoutingController routingController;
    private final SquidConfigController squidConfigController;
    private final NetworkStateMachine networkStateMachine;
    private final EblockerDnsServer eblockerDnsServer;
    private final WireGuardConfigurationParser parser;
    private final WireGuardRuntimeConfigurationRenderer renderer;
    private final WireGuardProfileFiles profileFiles;
    private final String killAllInstancesScript;
    private final String startInstanceScript;
    private final String stopInstanceScript;
    private final String setClientRouteScript;
    private final String clearClientRouteScript;
    private final String keepAliveTarget;
    private final Map<Integer, ClientState> clientStatesByProfileId = new HashMap<>();

    @Inject
    public WireGuardService(ScriptRunner scriptRunner,
                            DataSource dataSource,
                            RoutingController routingController,
                            SquidConfigController squidConfigController,
                            NetworkStateMachine networkStateMachine,
                            EblockerDnsServer eblockerDnsServer,
                            WireGuardConfigurationParser parser,
                            WireGuardRuntimeConfigurationRenderer renderer,
                            WireGuardProfileFiles profileFiles,
                            @Named("killall.wireguard.command") String killAllInstancesScript,
                            @Named("start.wireguard.instance.command") String startInstanceScript,
                            @Named("stop.wireguard.instance.command") String stopInstanceScript,
                            @Named("wireguard.set.client.route.command") String setClientRouteScript,
                            @Named("wireguard.clear.client.route.command") String clearClientRouteScript,
                            @Named("vpn.keepalive.ping.target") String keepAliveTarget) {
        this.scriptRunner = scriptRunner;
        this.dataSource = dataSource;
        this.routingController = routingController;
        this.squidConfigController = squidConfigController;
        this.networkStateMachine = networkStateMachine;
        this.eblockerDnsServer = eblockerDnsServer;
        this.parser = parser;
        this.renderer = renderer;
        this.profileFiles = profileFiles;
        this.killAllInstancesScript = killAllInstancesScript;
        this.startInstanceScript = startInstanceScript;
        this.stopInstanceScript = stopInstanceScript;
        this.setClientRouteScript = setClientRouteScript;
        this.clearClientRouteScript = clearClientRouteScript;
        this.keepAliveTarget = keepAliveTarget;
    }

    @SubSystemInit
    public void init() {
        cleanUpProfiles();
        killDanglingInterfaces();
    }

    public Collection<VpnProfile> getVpnProfiles() {
        return dataSource.getAll(WireGuardProfile.class).stream()
                .filter(profile -> !profile.isDeleted())
                .collect(Collectors.toList());
    }

    public VpnProfile getVpnProfileById(int id) {
        WireGuardProfile profile = dataSource.get(WireGuardProfile.class, id);
        if (profile == null || profile.isDeleted()) {
            return null;
        }
        return profile;
    }

    public WireGuardProfile saveProfile(WireGuardProfile profile) throws IOException {
        WireGuardProfile storedProfile;
        if (profile.getId() == null) {
            int id = dataSource.nextId(WireGuardProfile.class);
            storedProfile = new WireGuardProfile();
            storedProfile.setId(id);
            storedProfile.setConfigurationFileVersion(1);
        } else {
            storedProfile = dataSource.get(WireGuardProfile.class, profile.getId());
            if (storedProfile == null) {
                throw new IOException("cannot update non-existing profile " + profile.getId());
            }
        }

        storedProfile.setDescription(profile.getDescription());
        storedProfile.setEnabled(profile.isEnabled());
        storedProfile.setName(profile.getName());
        storedProfile.setTemporary(profile.isTemporary());
        storedProfile.setNameServersEnabled(profile.isNameServersEnabled());
        updateKeepAlive(storedProfile, profile);

        dataSource.save(storedProfile, storedProfile.getId());
        return storedProfile;
    }

    public WireGuardConfiguration getProfileClientConfig(int id) throws IOException {
        return profileFiles.readParsedConfiguration(id);
    }

    public WireGuardConfiguration setProfileClientConfig(int id, String config) throws IOException {
        WireGuardProfile profile = dataSource.get(WireGuardProfile.class, id);
        if (profile == null) {
            return null;
        }

        try {
            WireGuardConfiguration configuration = parser.parse(config);
            String runtimeConfig = renderer.render(configuration);

            profileFiles.createProfileDirectory(profile.getId());
            profileFiles.writeImportedConfig(profile.getId(), config);
            profileFiles.writeParsedConfiguration(profile.getId(), configuration);
            profileFiles.writeRuntimeConfig(profile.getId(), runtimeConfig);

            String endpointHost = getFirstEndpointHost(configuration);
            if (endpointHost != null && !Objects.equals(profile.getKeepAlivePingTarget(), endpointHost)) {
                profile.setKeepAlivePingTarget(endpointHost);
                dataSource.save(profile, profile.getId());
            }

            return configuration;
        } catch (WireGuardConfigurationParser.ParseException e) {
            log.warn("failed to parse WireGuard configuration: {}", e.getMessage());
            return null;
        }
    }

    public void deleteVpnProfile(int id) {
        deleteProfile(id);
    }

    public void startVpn(VpnProfile profile) {
        runScript(startInstanceScript, profile);
    }

    public void stopVpn(VpnProfile profile) {
        runScript(stopInstanceScript, profile);
    }

    public synchronized void routeClientThroughVpnTunnel(Device device, VpnProfile vpnProfile) {
        WireGuardProfile profile = getStoredProfile(vpnProfile);
        ClientState state = clientStatesByProfileId.computeIfAbsent(profile.getId(), id -> createClientState(profile));

        state.devicesById.put(device.getId(), device);
        if (!state.up) {
            startVpn(profile);
            configureDnsResolver(profile);
            runRouteScript(setClientRouteScript, state.routeId, getInterfaceName(profile.getId()));
            state.active = true;
            state.up = true;
        }

        if (profile.isNameServersEnabled()) {
            eblockerDnsServer.useVpnResolver(device, profile.getId());
        }
        reconfigureAclsAndRouting(profile.getId(), state);
    }

    public synchronized void restoreNormalRoutingForClient(Device device) {
        ClientState state = clientStatesByProfileId.values().stream()
                .filter(clientState -> clientState.devicesById.containsKey(device.getId()))
                .findFirst()
                .orElse(null);
        if (state == null) {
            eblockerDnsServer.useDefaultResolver(device);
            return;
        }

        state.devicesById.remove(device.getId());
        eblockerDnsServer.useDefaultResolver(device);
        reconfigureAclsAndRouting(state.profile.getId(), state);

        if (state.devicesById.isEmpty()) {
            runRouteScript(clearClientRouteScript, state.routeId);
            stopVpn(state.profile);
            eblockerDnsServer.removeVpnResolver(state.profile.getId());
            routingController.deleteRoute(state.routeId);
            state.active = false;
            state.up = false;
            clientStatesByProfileId.remove(state.profile.getId());
        }
    }

    public synchronized VpnStatus getStatus(VpnProfile profile) {
        ClientState state = clientStatesByProfileId.get(profile.getId());
        if (state == null) {
            return createStatus(profile.getId(), false, false, Collections.emptySet());
        }
        return createStatus(state.profile.getId(), state.active, state.up, state.devicesById.keySet());
    }

    public synchronized VpnStatus getStatusByDevice(Device device) {
        return clientStatesByProfileId.values().stream()
                .filter(state -> state.devicesById.containsKey(device.getId()))
                .findFirst()
                .map(state -> createStatus(state.profile.getId(), state.active, state.up, state.devicesById.keySet()))
                .orElse(null);
    }

    private VpnStatus createStatus(int profileId, boolean active, boolean up, Set<String> devices) {
        VpnStatus status = new VpnStatus();
        status.setProfileId(profileId);
        status.setActive(active);
        status.setUp(up);
        status.setDevices(Set.copyOf(devices));
        return status;
    }

    private ClientState createClientState(WireGuardProfile profile) {
        Integer route = routingController.createRoute();
        if (route == null) {
            throw new IllegalStateException("no WireGuard policy route available");
        }
        return new ClientState(profile, route);
    }

    private WireGuardProfile getStoredProfile(VpnProfile vpnProfile) {
        WireGuardProfile profile = dataSource.get(WireGuardProfile.class, vpnProfile.getId());
        if (profile != null) {
            return profile;
        }
        if (vpnProfile instanceof WireGuardProfile) {
            return (WireGuardProfile) vpnProfile;
        }
        throw new IllegalArgumentException("unknown WireGuard profile " + vpnProfile.getId());
    }

    private void configureDnsResolver(WireGuardProfile profile) {
        if (!profile.isNameServersEnabled()) {
            return;
        }

        try {
            WireGuardConfiguration configuration = profileFiles.readParsedConfiguration(profile.getId());
            if (!configuration.getDnsServers().isEmpty()) {
                eblockerDnsServer.addVpnResolver(profile.getId(), configuration.getDnsServers(), getFirstAddressWithoutCidr(configuration));
            }
        } catch (IOException e) {
            log.warn("failed to configure WireGuard DNS resolver for profile {}", profile.getId(), e);
        }
    }

    private String getFirstAddressWithoutCidr(WireGuardConfiguration configuration) {
        return configuration.getAddresses().stream()
                .findFirst()
                .map(address -> {
                    int cidrSeparator = address.indexOf('/');
                    return cidrSeparator >= 0 ? address.substring(0, cidrSeparator) : address;
                })
                .orElse(null);
    }

    private void reconfigureAclsAndRouting(int profileId, ClientState state) {
        squidConfigController.updateVpnDevicesAcl(profileId, Set.copyOf(state.devicesById.values()));
        networkStateMachine.deviceStateChanged();
    }

    private void runRouteScript(String script, int routeId, String... additionalArguments) {
        String[] arguments = new String[1 + additionalArguments.length];
        arguments[0] = Integer.toString(routeId);
        System.arraycopy(additionalArguments, 0, arguments, 1, additionalArguments.length);
        try {
            scriptRunner.runScript(script, arguments);
        } catch (IOException e) {
            throw new IllegalStateException("failed to run WireGuard routing script " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted running WireGuard routing script " + script, e);
        }
    }

    private String getInterfaceName(int profileId) {
        return "wg" + profileId;
    }

    private void runScript(String script, VpnProfile profile) {
        try {
            scriptRunner.runScript(script,
                    Integer.toString(profile.getId()),
                    profileFiles.getRuntimeConfig(profile.getId()),
                    profileFiles.getLogFile(profile.getId()));
        } catch (IOException e) {
            throw new IllegalStateException("failed to run WireGuard script " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted running WireGuard script " + script, e);
        }
    }

    private void cleanUpProfiles() {
        dataSource.getAll(WireGuardProfile.class).stream()
                .filter(profile -> profile.isTemporary() || profile.isDeleted())
                .map(WireGuardProfile::getId)
                .forEach(this::deleteProfile);
    }

    private void deleteProfile(int id) {
        try {
            dataSource.delete(WireGuardProfile.class, id);
            profileFiles.removeProfileDirectory(id);
        } catch (IOException e) {
            log.error("failed to delete WireGuard profile {}", id, e);
        }
    }

    private void killDanglingInterfaces() {
        try {
            scriptRunner.runScript(killAllInstancesScript, profileFiles.getProfilesPath());
        } catch (IOException e) {
            throw new IllegalStateException("failed to stop WireGuard instances", e);
        } catch (InterruptedException e) {
            log.error("killDanglingInterfaces stopped", e);
            Thread.currentThread().interrupt();
        }
    }

    private void updateKeepAlive(WireGuardProfile storedProfile, WireGuardProfile profile) {
        storedProfile.setKeepAliveMode(profile.getKeepAliveMode());
        switch (profile.getKeepAliveMode()) {
            case CUSTOM:
                storedProfile.setKeepAlivePingTarget(profile.getKeepAlivePingTarget());
                break;
            case EBLOCKER:
                storedProfile.setKeepAlivePingTarget(keepAliveTarget);
                break;
            case DISABLED:
            case OPENVPN_REMOTE:
            default:
                storedProfile.setKeepAlivePingTarget(profile.getKeepAlivePingTarget());
                break;
        }
    }

    private String getFirstEndpointHost(WireGuardConfiguration configuration) {
        return configuration.getPeers().stream()
                .map(WireGuardPeer::getEndpoint)
                .filter(Objects::nonNull)
                .map(this::extractEndpointHost)
                .findFirst()
                .orElse(null);
    }

    private String extractEndpointHost(String endpoint) {
        if (endpoint.startsWith("[")) {
            int closingBracket = endpoint.indexOf(']');
            if (closingBracket > 1) {
                return endpoint.substring(1, closingBracket);
            }
        }
        int portSeparator = endpoint.lastIndexOf(':');
        if (portSeparator > 0) {
            return endpoint.substring(0, portSeparator);
        }
        return endpoint;
    }

    private static class ClientState {
        private final WireGuardProfile profile;
        private final int routeId;
        private final Map<String, Device> devicesById = new LinkedHashMap<>();
        private boolean active;
        private boolean up;

        private ClientState(WireGuardProfile profile, int routeId) {
            this.profile = profile;
            this.routeId = routeId;
        }
    }
}
