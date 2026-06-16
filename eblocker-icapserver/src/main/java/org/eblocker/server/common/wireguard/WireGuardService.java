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
import org.eblocker.server.common.data.openvpn.KeepAliveMode;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfigurationParser;
import org.eblocker.server.common.wireguard.configuration.WireGuardPeer;
import org.eblocker.server.common.wireguard.configuration.WireGuardRuntimeConfigurationRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
public class WireGuardService {
    private static final Logger log = LoggerFactory.getLogger(WireGuardService.class);

    private final ScriptRunner scriptRunner;
    private final DataSource dataSource;
    private final WireGuardConfigurationParser parser;
    private final WireGuardRuntimeConfigurationRenderer renderer;
    private final WireGuardProfileFiles profileFiles;
    private final String killAllInstancesScript;
    private final String startInstanceScript;
    private final String stopInstanceScript;
    private final String keepAliveTarget;

    @Inject
    public WireGuardService(ScriptRunner scriptRunner,
                            DataSource dataSource,
                            WireGuardConfigurationParser parser,
                            WireGuardRuntimeConfigurationRenderer renderer,
                            WireGuardProfileFiles profileFiles,
                            @Named("killall.wireguard.command") String killAllInstancesScript,
                            @Named("start.wireguard.instance.command") String startInstanceScript,
                            @Named("stop.wireguard.instance.command") String stopInstanceScript,
                            @Named("vpn.keepalive.ping.target") String keepAliveTarget) {
        this.scriptRunner = scriptRunner;
        this.dataSource = dataSource;
        this.parser = parser;
        this.renderer = renderer;
        this.profileFiles = profileFiles;
        this.killAllInstancesScript = killAllInstancesScript;
        this.startInstanceScript = startInstanceScript;
        this.stopInstanceScript = stopInstanceScript;
        this.keepAliveTarget = keepAliveTarget;
    }

    public void init() {
        cleanUpProfiles();
        killDanglingInterfaces();
    }

    public Collection<VpnProfile> getVpnProfiles() {
        return dataSource.getAll(WireGuardProfile.class).stream()
                .filter(profile -> !profile.isDeleted())
                .collect(Collectors.toList());
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
}
