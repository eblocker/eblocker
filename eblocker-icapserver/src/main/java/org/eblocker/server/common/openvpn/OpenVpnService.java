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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.KeepAliveMode;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnLoginCredentials;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationParser;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationVersion0;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurator;
import org.eblocker.server.common.openvpn.configuration.OpenVpnFileOptionValidator;
import org.eblocker.server.common.openvpn.configuration.Option;
import org.eblocker.server.common.openvpn.configuration.SimpleOption;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemShutdown;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class makes all the work which is asked for by the OpenVpnController (REST Interface).
 * It will trigger/tell the other objects to adapt the firewall configuration, rewrite squids config and so on...
 */
@SubSystemService(value = SubSystem.HTTPS_SERVER, initPriority = 200)
public class OpenVpnService {
    private static final Logger log = LoggerFactory.getLogger(OpenVpnService.class);

    private Map<Integer, VpnClient> vpnClientsById = new HashMap<>();

    private final ScriptRunner scriptRunner;
    private final DataSource dataSource;
    private final OpenVpnClientFactory openVpnClientFactory;
    private final OpenVpnProfileFiles profileFiles;
    private final OpenVpnConfigurator configurator;
    private final OpenVpnFileOptionValidator fileOptionValidator;

    private final String keepAliveTarget;
    private final String killAllInstancesScript;
    private final long stoppedClientTimeout;

    private final String passwordMask;

    @Inject
    public OpenVpnService(ScriptRunner scriptRunner,
                          DataSource dataSource,
                          OpenVpnClientFactory openVpnClientFactory,
                          OpenVpnConfigurator configurator,
                          OpenVpnFileOptionValidator fileOptionValidator,
                          @Named("vpn.keepalive.ping.target") String keepAliveTarget,
                          @Named("killall.openvpn.command") String killAllInstancesScript,
                          OpenVpnProfileFiles profileFiles,
                          @Named("openvpn.profile.password.mask") String passwordMask,
                          @Named("openvpn.cache.stoppedClientTimeout") Integer stoppedClientTimeout) {
        this.openVpnClientFactory = openVpnClientFactory;
        this.scriptRunner = scriptRunner;
        this.dataSource = dataSource;
        this.configurator = configurator;
        this.fileOptionValidator = fileOptionValidator;
        this.keepAliveTarget = keepAliveTarget;
        this.killAllInstancesScript = killAllInstancesScript;
        this.profileFiles = profileFiles;
        this.passwordMask = passwordMask;
        this.stoppedClientTimeout = stoppedClientTimeout;
    }

    @SubSystemInit
    public void init() {
        cleanUpProfiles();
        killDanglingProcesses();
        upgradeConfigurations();
        cleanUpClientStates();
    }

    /**
     * Deletes all temporary and deleted profiles from database and file system
     */
    private void cleanUpProfiles() {
        dataSource.getAll(OpenVpnProfile.class).stream()
                .filter(p -> p.isTemporary() || p.isDeleted())
                .map(OpenVpnProfile::getId)
                .forEach(this::deleteProfile);
    }

    private void deleteProfile(int id) {
        try {
            dataSource.delete(OpenVpnProfile.class, id);
            profileFiles.removeProfileDirectory(id);
        } catch (IOException e) {
            log.error("failed to delete vpn profile {}", id, e);
        }
    }

    /**
     * Kills all running openvpn processes which may have been shutdown at icapserver exit
     */
    private void killDanglingProcesses() {
        try {
            scriptRunner.runScript(killAllInstancesScript, profileFiles.getProfilesPath());
        } catch (IOException e) {
            throw new IllegalStateException("failed to start stop all instances script", e);
        } catch (InterruptedException e) {
            log.error("killDanglingProcesses stopped", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Upgrades previous profile configuration files
     */
    private void upgradeConfigurations() {
        upgradeConfigurations(null, this::upgradeConfigurationToV1);
        upgradeConfigurations(1, this::upgradeConfigurationToV2);
        upgradeConfigurations(2, this::upgradeConfigurationToV3);
    }

    private void upgradeConfigurations(Integer version, Consumer<OpenVpnProfile> upgradeMethod) {
        dataSource.getAll(OpenVpnProfile.class).stream()
                .filter(p -> Objects.equals(version, p.getConfigurationFileVersion()))
                .filter(p -> profileFiles.hasParsedConfiguration(p.getId()))
                .forEach(upgradeMethod);
    }

    /**
     * V1 configurations contain all user supplied options to be able to indicate changes done by the eblocker and
     * allows inlining of external files. For this it needs the original parsed options.
     * To match the new format required this migration tries to populates the original ones on the currently used. This
     * does not resemble the originally uploaded file but it will be usable.
     */
    private void upgradeConfigurationToV1(OpenVpnProfile profile) {
        try {
            OpenVpnConfigurationVersion0 oldConfiguration = profileFiles.readParsedConfigurationVersion0(profile.getId());
            List<Option> userOptions = Stream.concat(oldConfiguration.getBlacklistedOptions().stream(),
                    Stream.concat(oldConfiguration.getEffectiveOptions().stream(),
                            oldConfiguration.getIgnoredOptions().stream()))
                    .sorted(Comparator.comparingInt(Option::getLineNumber))
                    .collect(Collectors.toList());

            OpenVpnConfiguration newConfiguration = new OpenVpnConfiguration();
            newConfiguration.setUserOptions(userOptions);
            newConfiguration.setSourceConfig("# generated by eBlocker migration\n"
                    + userOptions.stream().map(Option::toString).collect(Collectors.joining("\n")));

            profileFiles.writeParsedConfiguration(profile.getId(), newConfiguration);
            profileFiles.writeConfigFile(profile.getId(), configurator.getActiveConfiguration(newConfiguration, profileFiles.getCredentials(profile.getId()), getOptionFileByOption(profile, newConfiguration)));
            profile.setConfigurationFileVersion(1);
            dataSource.save(profile, profile.getId());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * In v2 inlining has been disabled in favor of storing files on disk. This migration stores all previously inlined
     * content on disk.
     */
    private void upgradeConfigurationToV2(OpenVpnProfile profile) {
        try {
            OpenVpnConfiguration configuration = getProfileClientConfig(profile.getId());
            if (!configuration.getInlinedContentByName().isEmpty()) {
                for (Map.Entry<String, String> e : configuration.getInlinedContentByName().entrySet()) {
                    profileFiles.writeConfigOptionFile(profile.getId(), e.getKey(), e.getValue().getBytes());
                    e.setValue(null);
                }
                profileFiles.writeParsedConfiguration(profile.getId(), configuration);

                List<Option> activeConfiguration = configurator.getActiveConfiguration(
                        configuration,
                        profileFiles.getCredentials(profile.getId()),
                        getOptionFileByOption(profile, configuration));
                profileFiles.writeConfigFile(profile.getId(), activeConfiguration);
            }
            profile.setConfigurationFileVersion(2);
            dataSource.save(profile, profile.getId());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Obsolete. but still set version to 3 to avoid errors
     */
    private void upgradeConfigurationToV3(OpenVpnProfile profile) {
        profile.setConfigurationFileVersion(3);
        dataSource.save(profile, profile.getId());
    }

    /**
     * Removes all client states of previously used vpns
     */
    private void cleanUpClientStates() {
        dataSource.deleteAll(OpenVpnClientState.class);
    }

    Collection<VpnClient> getVpnClients() {
        return Collections.unmodifiableCollection(vpnClientsById.values());
    }

    /**
     * Get all VPN profiles
     *
     * @return
     */
    public Collection<VpnProfile> getVpnProfiles() {
        return dataSource.getAll(OpenVpnProfile.class).stream().filter(p -> !p.isDeleted()).map(this::maskPassword).collect(Collectors.toList());
    }

    public OpenVpnProfile saveProfile(OpenVpnProfile profile) throws IOException {
        OpenVpnProfile storedProfile;
        if (profile.getId() == null) {
            int id = dataSource.nextId(OpenVpnProfile.class);
            storedProfile = new OpenVpnProfile();
            storedProfile.setId(id);
            storedProfile.setConfigurationFileVersion(3);
        } else {
            storedProfile = dataSource.get(OpenVpnProfile.class, profile.getId());
            if (storedProfile == null) {
                throw new IOException("cannot update non-existing profile " + profile.getId());
            }
        }

        // copy attributes
        storedProfile.setDescription(profile.getDescription());
        storedProfile.setEnabled(profile.isEnabled());
        storedProfile.setName(profile.getName());
        storedProfile.setTemporary(profile.isTemporary());
        storedProfile.setNameServersEnabled(profile.isNameServersEnabled());

        VpnLoginCredentials loginCredentials = storedProfile.getLoginCredentials() != null ? storedProfile.getLoginCredentials() : new VpnLoginCredentials();

        // only update password if it has been changed
        if (profile.getLoginCredentials() != null && !passwordMask.equals(profile.getLoginCredentials().getPassword())) {
            loginCredentials.setPassword(profile.getLoginCredentials().getPassword());
        }
        // only update username if it has been changed
        if (profile.getLoginCredentials() != null && storedProfile.getLoginCredentials() != null &&
                (storedProfile.getLoginCredentials().getUsername() == null || !storedProfile.getLoginCredentials().getUsername().equals(profile.getLoginCredentials().getUsername()))) {
            loginCredentials.setUsername(profile.getLoginCredentials().getUsername());
        }
        storedProfile.setLoginCredentials(loginCredentials);

        // update keep alive target if it has been changed
        if (profile.getKeepAliveMode() != storedProfile.getKeepAliveMode() || KeepAliveMode.CUSTOM == profile.getKeepAliveMode() && !Objects.equals(profile.getKeepAlivePingTarget(), storedProfile.getKeepAlivePingTarget())) {
            storedProfile.setKeepAliveMode(profile.getKeepAliveMode());
            switch (profile.getKeepAliveMode()) {
                case CUSTOM:
                    storedProfile.setKeepAlivePingTarget(profile.getKeepAlivePingTarget());
                    break;
                case DISABLED:
                    break;
                case EBLOCKER:
                    storedProfile.setKeepAlivePingTarget(keepAliveTarget);
                    break;
                case OPENVPN_REMOTE:
                    OpenVpnConfiguration config = profileFiles.readParsedConfiguration(profile.getId());
                    storedProfile.setKeepAlivePingTarget(getFirstRemoteHost(config));
                    break;
            }
        } else if (storedProfile.getKeepAliveMode() == KeepAliveMode.CUSTOM && !storedProfile.getKeepAlivePingTarget().equals(profile.getKeepAlivePingTarget())) {
            storedProfile.setKeepAlivePingTarget(profile.getKeepAlivePingTarget());
        }

        dataSource.save(storedProfile, storedProfile.getId());
        maskPassword(storedProfile);

        return storedProfile;
    }

    public void deleteVpnProfile(int id) {
        VpnClient client = vpnClientsById.get(id);
        // always defer deleting profile
        if (client != null) {
            if (!client.isStopped() && !client.isClosed()) {
                client.stop();
            }
            client.getProfile().setDeleted(true);
            dataSource.save(client.getProfile(), id);
        } else {
            deleteProfile(id);
        }
    }

    public OpenVpnConfiguration getProfileClientConfig(int id) throws IOException {
        return profileFiles.readParsedConfiguration(id);
    }

    public OpenVpnConfiguration setProfileClientConfig(int id, String config) throws IOException {
        OpenVpnProfile profile = dataSource.get(OpenVpnProfile.class, id);
        if (profile == null) {
            return null;
        }

        try {
            // parse supplied configuration
            OpenVpnConfiguration configuration = configurator.createConfiguration(config);

            // update keep alive target
            String remoteHost = getFirstRemoteHost(configuration);
            if (remoteHost != null && !remoteHost.equals(profile.getKeepAlivePingTarget())) {
                profile.setKeepAlivePingTarget(remoteHost);
                dataSource.save(profile, profile.getId());
            }

            // store configuration
            profileFiles.createProfileDirectory(profile.getId());
            profileFiles.writeParsedConfiguration(profile.getId(), configuration);
            writeConfigFile(profile, configuration);

            return configuration;
        } catch (OpenVpnConfigurationParser.ParseException e) {
            log.error("parsing exception", e);
            return null;
        }
    }

    public OpenVpnConfiguration setProfileClientConfigOptionFile(int id, String option, byte[] content) throws IOException {
        OpenVpnProfile profile = dataSource.get(OpenVpnProfile.class, id);
        if (profile == null) {
            return null;
        }

        OpenVpnConfiguration configuration = profileFiles.readParsedConfiguration(id);
        if (configuration == null) {
            throw new FileNotFoundException();
        }

        // store credentials in profile if present
        if ("auth-user-pass".equals(option)) {
            String[] lines = new String(content).replaceAll("\r", "").split("\n");
            if (lines.length == 2) {
                VpnLoginCredentials credentials = new VpnLoginCredentials();
                credentials.setUsername(lines[0]);
                credentials.setPassword(lines[1]);
                profile.setLoginCredentials(credentials);
                dataSource.save(profile, profile.getId());
            } else {
                log.warn("failed to extract credentials: unexpected {} lines", lines.length);
            }
        }

        fileOptionValidator.validate(option, content);
        profileFiles.writeConfigOptionFile(id, option, content);

        configuration.getInlinedContentByName().put(option, null);
        profileFiles.writeParsedConfiguration(profile.getId(), configuration);

        writeConfigFile(profile, configuration);
        return configuration;
    }

    /**
     * Get a VPN profile for a given ID
     *
     * @param id
     * @return
     */
    public VpnProfile getVpnProfileById(int id) {
        return maskPassword(dataSource.get(OpenVpnProfile.class, id));
    }

    public void routeClientThroughVpnTunnel(Device device, VpnProfile vpnProfile) {
        try {
            VpnClient client = getClientByDevice(device);
            if (client != null) {
                if (!client.getProfile().getId().equals(vpnProfile.getId())) {
                    log.info("device {} is changing used vpn from {} to {}", device.getHardwareAddress(), client.getProfile().getId(), vpnProfile.getId());
                    removeDeviceFromClient(device, client);
                } else {
                    log.info("device {} is already using vpn {}", device.getHardwareAddress(), client.getProfile().getId());
                    return;
                }
            }

            // reload profile to ensure credentials are complete
            OpenVpnProfile reloadedProfile = dataSource.get(OpenVpnProfile.class, vpnProfile.getId());
            getVpnClient(reloadedProfile).addDevices(Collections.singleton(device));
        } catch (Exception e) {
            log.error("something went wrong!", e);
        }
    }

    /**
     * Do not route device through VpnProfile (and the VPN tunnel which belongs to this profile) anymore
     * When this was the last device using this VPN profile, it will automatically get shutdown/stopped
     *
     * @param device
     */
    public void restoreNormalRoutingForClient(Device device) {
        try {
            VpnClient client = getClientByDevice(device);
            if (client == null) {
                log.info("Cannot restore normal routing for device {}: it is not using any vpn.", device);
                return;
            }

            removeDeviceFromClient(device, client);
        } catch (Exception e) {
            log.error("something went wrong!", e);
        }
    }

    public synchronized void startVpn(VpnProfile profile) {
        // reload profile to ensure credentials are complete
        OpenVpnProfile reloadedProfile = dataSource.get(OpenVpnProfile.class, profile.getId());
        getVpnClient(reloadedProfile).start();
    }

    public synchronized void stopVpn(VpnProfile profile) {
        VpnClient client = vpnClientsById.get(profile.getId());
        if (client == null) {
            log.warn("cannot stop non-running vpn {}", profile.getId());
            return;
        }

        client.stop();
    }

    public VpnStatus getStatus(VpnProfile profile) {
        return createVpnStatus(vpnClientsById.get(profile.getId()));
    }

    public VpnStatus getStatusByDevice(Device device) {
        Optional<VpnClient> client = vpnClientsById.values().stream().filter(c -> c.getDeviceIds().contains(device.getId())).findFirst();
        if (client.isPresent()) {
            return createVpnStatus(client.get());
        } else {
            return null;
        }
    }

    public Runnable getCacheCleaner() {
        return this::cleanCache;
    }

    private VpnStatus createVpnStatus(VpnClient client) {
        VpnStatus status = new VpnStatus();
        if (client == null) {
            status.setActive(false);
            status.setUp(false);
            status.setErrors(Collections.emptyList());
            status.setDevices(Collections.emptySet());
        } else {
            status.setProfileId(client.getProfile().getId());
            status.setActive(!client.isStopped());
            status.setUp(client.isUp());
            status.setErrors(client.getLog());
            status.setDevices(client.getDeviceIds());
            status.setExitStatus(client.getExitStatus());
        }
        return status;
    }

    private synchronized void removeDeviceFromClient(Device device, VpnClient client) {
        client.stopDevices(Collections.singleton(device));
    }

    private synchronized VpnClient getVpnClient(OpenVpnProfile profile) {
        VpnClient client = vpnClientsById.get(profile.getId());
        if (client == null) {
            client = openVpnClientFactory.create(profile);
            vpnClientsById.put(profile.getId(), client);
        }
        return client;
    }

    private VpnClient getClientByDevice(Device device) {
        Optional<VpnClient> client = vpnClientsById.values().stream().filter(c -> c.getDeviceIds().contains(device.getId())).findFirst();
        return client.isPresent() ? client.get() : null;
    }

    private VpnProfile maskPassword(VpnProfile profile) {
        if (profile != null && profile.getLoginCredentials() != null &&
                profile.getLoginCredentials().getPassword() != null && !profile.getLoginCredentials().getPassword().isEmpty()) {
            profile.getLoginCredentials().setPassword(passwordMask);
        }
        return profile;
    }

    private void writeConfigFile(VpnProfile profile, OpenVpnConfiguration configuration) throws FileNotFoundException {
        profileFiles.writeConfigFile(profile.getId(),
                configurator.getActiveConfiguration(configuration,
                        profileFiles.getCredentials(profile.getId()),
                        getOptionFileByOption(profile, configuration)));
    }

    private Map<String, String> getOptionFileByOption(VpnProfile profile, OpenVpnConfiguration configuration) {
        return configuration.getInlinedContentByName().keySet().stream()
                .collect(Collectors.toMap(o -> o, o -> profileFiles.getOptionFile(profile.getId(), o)));
    }

    private synchronized void cleanCache() {
        log.info("Cleaning client cache ...");
        for (Iterator<VpnClient> i = vpnClientsById.values().iterator(); i.hasNext(); ) {
            VpnClient client = i.next();
            if (!checkCacheEntry(client)) {
                // remove client from cache
                i.remove();

                // delete profile if it has been marked as deleted
                if (client.getProfile().isDeleted()) {
                    deleteProfile(client.getProfile().getId());
                }

            }
        }
        log.info("Finished cache cleaning.");
    }

    private boolean checkCacheEntry(VpnClient client) {
        int id = client.getProfile().getId();

        if (!client.isStopped() && !client.isClosed()) {
            log.debug("keeping client {}", id);
            return true;
        }

        long t = Duration.between(client.getStopInstant(), Instant.now()).getSeconds();
        if (t < stoppedClientTimeout) {
            log.debug("keeping client {} stopped since {}s", id, t);
            return true;
        }

        log.debug("closing client {} stopped since {}s", id, t);
        try {
            client.close();
            if (client.isClosed()) {
                log.debug("removing client {} from cache.", id);
                return false;
            }
            log.warn("client {} has not been closed.", id);
            return true;
        } catch (IOException e) {
            log.error("failed to close client {}", id, e);
            return true;
        }
    }

    private String getFirstRemoteHost(OpenVpnConfiguration configuration) {
        return configuration.getUserOptions().stream()
                .filter(o -> "remote".equalsIgnoreCase(o.getName()))
                .filter(o -> o instanceof SimpleOption)
                .map(o -> (SimpleOption) o)
                .filter(o -> o.getArguments().length >= 1)
                .map(o -> o.getArguments()[0])
                .findFirst()
                .orElse(null);
    }

    @SubSystemShutdown
    public void shutdown() {
        log.info("Shutting down");
        vpnClientsById.forEach((id, client) -> {
            log.info("Stopping client {}", id);
            client.stop();
        });
    }
}
