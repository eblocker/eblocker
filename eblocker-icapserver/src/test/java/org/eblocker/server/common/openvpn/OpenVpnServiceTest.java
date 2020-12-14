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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.KeepAliveMode;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnLoginCredentials;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationParser;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationVersion0;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurator;
import org.eblocker.server.common.openvpn.configuration.OpenVpnFileOptionValidator;
import org.eblocker.server.common.openvpn.configuration.Option;
import org.eblocker.server.common.openvpn.configuration.SimpleOption;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenVpnServiceTest {
    private static final String KILL_ALL_INSTANCES_SCRIPT = "KILL_ALL_INSTANCES_SCRIPT";
    private static final String PASSWORD_MASK = "****";
    private static final int STOPPED_CLIENT_TIMEOUT = 60;
    private static final String KEEP_ALIVE_TARGET = "eblocker.com";

    private OpenVpnProfileFiles profileFiles;
    private OpenVpnFileOptionValidator fileOptionValidator;
    private ScriptRunner scriptRunner;
    private DataSource dataSource;
    private OpenVpnClientFactory clientFactory;
    private OpenVpnConfigurator configurator;

    private Map<Integer, VpnClient> clientsById;
    private Map<Integer, OpenVpnProfile> profilesById;

    @Before
    public void setup() throws IOException, OpenVpnConfigurationParser.ParseException {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        dataSource = Mockito.mock(DataSource.class);

        configurator = Mockito.mock(OpenVpnConfigurator.class);
        Mockito.when(configurator.createConfiguration(Mockito.anyString())).thenReturn(new OpenVpnConfiguration());

        fileOptionValidator = Mockito.mock(OpenVpnFileOptionValidator.class);

        clientsById = new HashMap<>();
        clientFactory = vpnProfile -> {
            OpenVpnClient client = createMockOpenVpnClient(vpnProfile);
            clientsById.put(vpnProfile.getId(), client);
            return client;
        };

        profilesById = new HashMap<>();
        profilesById.put(0, createMockProfile(0, "unit-test-profile-0", "username", "password"));
        profilesById.put(1, createMockProfile(1, "unit-test-profile-1", "username", "password"));
        profilesById.put(2, createMockProfile(2, "unit-test-profile-2", "username", "password"));
        Mockito.when(dataSource.get(Mockito.eq(OpenVpnProfile.class), Mockito.anyInt())).then(i -> copyProfile(profilesById.get(i.getArguments()[1])));
        Mockito.when(dataSource.save(Mockito.any(OpenVpnProfile.class), Mockito.anyInt())).then(i -> {
            profilesById.put(i.getArgument(1), copyProfile(i.getArgument(0)));
            return i.getArgument(0);
        });
        Mockito.when(dataSource.getAll(OpenVpnProfile.class)).then(x -> new ArrayList<>(profilesById.values()));

        profileFiles = Mockito.mock(OpenVpnProfileFiles.class);
        Mockito.when(profileFiles.hasParsedConfiguration(0)).thenReturn(true);
        Mockito.when(profileFiles.hasParsedConfiguration(1)).thenReturn(true);
        Mockito.when(profileFiles.hasParsedConfiguration(2)).thenReturn(false);
        Mockito.when(profileFiles.readParsedConfiguration(0)).thenReturn(new OpenVpnConfiguration());
        Mockito.when(profileFiles.getOptionFile(Mockito.anyInt(), Mockito.anyString())).then(im -> "option." + im.getArgument(1));
    }

    @Test
    public void testInit() throws Exception {
        OpenVpnService service = createService();
        service.init();

        Mockito.verify(scriptRunner).runScript(KILL_ALL_INSTANCES_SCRIPT);

        // Assert.assertEquals fails, why?
        Assert.assertTrue(service.getVpnProfiles().containsAll(profilesById.values()) && profilesById.values().containsAll(service.getVpnProfiles()));
        Assert.assertTrue(service.getVpnClients().containsAll(clientsById.values()) && clientsById.values().containsAll(service.getVpnClients()));

        Assert.assertEquals(service.getVpnProfileById(0), profilesById.get(0));
        Assert.assertEquals(service.getVpnProfileById(1), profilesById.get(1));
        Assert.assertEquals(service.getVpnProfileById(2), profilesById.get(2));
    }

    @Test
    public void testInitWithProfileConfigurationUpgradeV1() throws IOException {
        // setup mock profile with old configuration
        profilesById.put(3, createMockProfile(3, "unit-test-profile-3", "username", "password"));
        profilesById.get(3).setConfigurationFileVersion(null);

        List<Option> options = Arrays.asList(
                new SimpleOption(1, "blacklisted-option"),
                new SimpleOption(2, "ignored-option"),
                new SimpleOption(3, "effective-option"),
                new SimpleOption(4, "remote", new String[]{ "vpn.eblocker.com" })
        );

        OpenVpnConfigurationVersion0 oldConfiguration = new OpenVpnConfigurationVersion0();
        oldConfiguration.setBlacklistedOptions(options.subList(0, 1));
        oldConfiguration.setIgnoredOptions(options.subList(1, 2));
        oldConfiguration.setEffectiveOptions(options.subList(2, 4));
        Mockito.when(profileFiles.readParsedConfigurationVersion0(3)).thenReturn(oldConfiguration);
        Mockito.when(profileFiles.hasParsedConfiguration(3)).thenReturn(true);

        // quite hacky way to store configuration to be able to do the next upgrade iteration
        // probably migrations should be moved to single class for easier testing ...
        Holder<OpenVpnConfiguration> newConfigurationHolder = new Holder<>();
        Mockito.doAnswer(im -> {
            newConfigurationHolder.value = im.getArgument(1);
            return null;
        }).when(profileFiles).writeParsedConfiguration(Mockito.eq(3), Mockito.any(OpenVpnConfiguration.class));
        Mockito.when(profileFiles.readParsedConfiguration(3)).thenAnswer(im -> newConfigurationHolder.value);

        // init service
        OpenVpnService service = createService();
        service.init();

        // check profile configuration file version has been updated
        Assert.assertEquals(Integer.valueOf(3), profilesById.get(3).getConfigurationFileVersion());

        // check generated configuration is correct
        Assert.assertEquals(options, newConfigurationHolder.value.getUserOptions());
    }

    @Test
    public void testInitWithProfileConfigurationUpgradeV2() throws IOException {
        // setup mock profile with old configuration
        profilesById.put(3, createMockProfile(3, "unit-test-profile-3", "username", "password"));
        profilesById.get(3).setConfigurationFileVersion(1);

        List<Option> options = Arrays.asList(
                new SimpleOption(1, "external-file-option"),
                new SimpleOption(2, "another-file-option"),
                new SimpleOption(4, "remote", new String[]{ "vpn.eblocker.com" })
        );

        Map<String, String> inlineContent = new HashMap<>();
        inlineContent.put("external-file-option", "external-file-option-content");
        inlineContent.put("another-file-option", "another-file-option-content");

        OpenVpnConfiguration oldConfiguration = new OpenVpnConfiguration();
        oldConfiguration.setUserOptions(options);
        oldConfiguration.setInlinedContentByName(inlineContent);
        Mockito.when(profileFiles.readParsedConfiguration(3)).thenReturn(oldConfiguration);
        Mockito.when(profileFiles.hasParsedConfiguration(3)).thenReturn(true);

        // init service
        OpenVpnService service = createService();
        service.init();

        // check profile configuration file version has been updated
        Assert.assertEquals(Integer.valueOf(3), profilesById.get(3).getConfigurationFileVersion());

        // check previously inlined content has been written to disk
        Mockito.verify(profileFiles).writeConfigOptionFile(3, "external-file-option", "external-file-option-content".getBytes());
        Mockito.verify(profileFiles).writeConfigOptionFile(3, "another-file-option", "another-file-option-content".getBytes());

        // check configuration has been updated
        ArgumentCaptor<OpenVpnConfiguration> captor = ArgumentCaptor.forClass(OpenVpnConfiguration.class);
        Mockito.verify(profileFiles).writeParsedConfiguration(Mockito.eq(3), captor.capture());
        OpenVpnConfiguration newConfiguration = captor.getValue();
        Assert.assertEquals(2, newConfiguration.getInlinedContentByName().size());
        Assert.assertTrue(newConfiguration.getInlinedContentByName().containsKey("external-file-option"));
        Assert.assertNull(newConfiguration.getInlinedContentByName().get("external-file-option"));
        Assert.assertTrue(newConfiguration.getInlinedContentByName().containsKey("another-file-option"));
        Assert.assertNull(newConfiguration.getInlinedContentByName().get("another-file-option"));

        // check openvpn config file has been updated
        Mockito.verify(configurator).getActiveConfiguration(Mockito.eq(newConfiguration), Mockito.any(), Mockito.any());
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(3), Mockito.anyList());
    }

    @Test
    public void testInitWithProfileConfigurationUpgradeV3() {
        // setup mock profile with old configuration
        profilesById.put(3, createMockProfile(3, "unit-test-profile-3", "username", "password"));
        profilesById.get(3).setConfigurationFileVersion(2);
        Mockito.when(profileFiles.hasParsedConfiguration(3)).thenReturn(true);

        // init service
        OpenVpnService service = createService();
        service.init();

        // check version has been set
        Assert.assertEquals(Integer.valueOf(3), profilesById.get(3).getConfigurationFileVersion());
    }

    @Test
    public void testInitWithActiveClients() {
        // setup three devices and vpn states: one using vpn 0, one using a not existing one and a device using no vpn
        Map<String, Device> devicesById = new HashMap<>();
        devicesById.put("0", createDevice(0));
        devicesById.put("1", createDevice(1));
        devicesById.put("2", createDevice(2));

        OpenVpnClientState[] states = {
                createOpenVpnClientState(0, Collections.singleton(devicesById.get("0"))),
                createOpenVpnClientState(100, Collections.singleton(devicesById.get("1"))),
                createOpenVpnClientState(2, Collections.emptySet())
        };

        Mockito.when(dataSource.getDevice(Mockito.anyString())).thenAnswer(invocationOnMock -> devicesById.get(invocationOnMock.getArgument(0)));
        Mockito.when(dataSource.getDevices()).thenReturn(new HashSet<>(devicesById.values()));
        Mockito.when(dataSource.getAll(OpenVpnClientState.class)).thenReturn(Arrays.asList(states));

        // initialize service
        OpenVpnService service = createService();
        service.init();

        // check no clients have been started and client states have been cleaned up
        Assert.assertNull(clientsById.get(0));
        Assert.assertNull(clientsById.get(1));
        Assert.assertNull(clientsById.get(2));
        Mockito.verify(dataSource).deleteAll(OpenVpnClientState.class);
    }

    @Test
    public void testInitProfileCleanUp() throws Exception {
        // add temporary profile
        OpenVpnProfile temporaryProfile = createMockProfile(3, "unit-test-profile-3-temporary", "username", "password");
        temporaryProfile.setTemporary(true);
        profilesById.put(3, temporaryProfile);

        // add deleted profile
        OpenVpnProfile deletedProfile = createMockProfile(4, "unit-test-profile-4-deleted", "username", "password");
        deletedProfile.setDeleted(true);
        profilesById.put(4, deletedProfile);

        // re-init mock to include new profiles
        Mockito.when(dataSource.getAll(OpenVpnProfile.class)).thenReturn(new ArrayList<>(profilesById.values()));

        // initialize service
        OpenVpnService service = createService();
        service.init();

        // check if temporary and deleted profiles have been deleted
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 3);
        Mockito.verify(profileFiles).removeProfileDirectory(3);
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 4);
        Mockito.verify(profileFiles).removeProfileDirectory(4);
    }

    private Device createDevice(int id) {
        Device device = new Device();
        device.setId(String.valueOf(id));
        device.setEnabled(true);
        return device;
    }

    private OpenVpnClientState createOpenVpnClientState(int id, Set<Device> devices) {
        OpenVpnClientState state = new OpenVpnClientState();
        state.setId(id);
        state.setDevices(devices.stream().map(Device::getId).collect(Collectors.toSet()));
        return state;
    }

    @Test
    public void testRouteClientThroughVpnTunnel() throws Exception {
        OpenVpnService service = createService();
        service.init();

        Device device = createDevice(0);

        // normal request
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));
        Assert.assertNotNull(clientsById.get(0));
        Assert.assertTrue(clientsById.get(0).getDeviceIds().contains(device.getId()));

        // vpn switch
        service.routeClientThroughVpnTunnel(device, profilesById.get(1));
        Assert.assertFalse(clientsById.get(0).getDeviceIds().contains(device.getId()));
        Assert.assertNotNull(clientsById.get(1));
        Assert.assertTrue(clientsById.get(1).getDeviceIds().contains(device.getId()));

        // removal
        service.restoreNormalRoutingForClient(device);
        Assert.assertFalse(clientsById.get(1).getDeviceIds().contains(device.getId()));
    }

    @Test
    public void testClientLifecycle() throws Exception {
        OpenVpnService service = createService();
        service.init();
        Assert.assertEquals(0, service.getVpnClients().size());

        // add single device
        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));
        // check vpn client is created
        Assert.assertEquals(1, service.getVpnClients().size());

        // store client for later check if correctly released
        VpnClient client = clientsById.get(0);

        // remove single device
        service.restoreNormalRoutingForClient(device);

        // check vpn client is still kept in cache
        Assert.assertEquals(1, service.getVpnClients().size());

        // mock client state
        Mockito.when(client.isStopped()).thenReturn(true);
        Mockito.when(client.isClosed()).thenReturn(true);
        Mockito.when(client.getStopInstant()).thenReturn(Instant.now());

        // run fist cache clean-up, it is expected to keep client in cache as its timeout has not been reached yet
        service.getCacheCleaner().run();
        Assert.assertEquals(1, service.getVpnClients().size());

        // run second cache clean-up, this one is expected to remove the client
        Mockito.when(client.getStopInstant()).thenReturn(Instant.now().minusSeconds(STOPPED_CLIENT_TIMEOUT));
        service.getCacheCleaner().run();
        Assert.assertEquals(0, service.getVpnClients().size());

        // ensure client resources has been released
        Mockito.verify(client).close();
    }

    @Test
    public void testSaveNewProfile() throws IOException {
        OpenVpnService service = createService();
        service.init();

        OpenVpnProfile profile = createMockProfile(null, "name", "username", "password");
        profile = service.saveProfile(profile);

        Mockito.verify(dataSource).save(Mockito.any(OpenVpnProfile.class), Mockito.anyInt());
        Assert.assertNotNull(profile.getId());
        Assert.assertEquals(PASSWORD_MASK, profile.getLoginCredentials().getPassword());
    }

    @Test
    public void testSaveExistingProfile() throws IOException {
        OpenVpnService service = createService();
        service.init();

        OpenVpnProfile profile = (OpenVpnProfile) service.getVpnProfileById(0);
        Assert.assertNotNull(profile);
        Assert.assertEquals(PASSWORD_MASK, profile.getLoginCredentials().getPassword());

        profile = service.saveProfile(profile);

        // check returned profile
        Assert.assertEquals(Integer.valueOf(0), profile.getId());
        Assert.assertEquals(PASSWORD_MASK, profile.getLoginCredentials().getPassword());

        // check saved profile
        Mockito.verify(dataSource).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals("password", profilesById.get(0).getLoginCredentials().getPassword());
    }

    @Test
    public void testPasswordChange() throws IOException {
        OpenVpnService service = createService();
        service.init();

        OpenVpnProfile profile = profilesById.get(0);
        profile.getLoginCredentials().setPassword("p455w0rd");
        profile = service.saveProfile(profile);

        // check returned profile
        Assert.assertEquals(Integer.valueOf(0), profile.getId());
        Assert.assertEquals(PASSWORD_MASK, profile.getLoginCredentials().getPassword());

        // check saved profile
        Mockito.verify(dataSource).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals("p455w0rd", profilesById.get(0).getLoginCredentials().getPassword());
    }

    @Test
    public void testKeepAliveSettings() throws IOException {
        List<Option> options = Arrays.asList(
                new SimpleOption(1, "remote", new String[]{ "vpn.eblocker.com" })
        );

        OpenVpnConfiguration oldConfiguration = new OpenVpnConfiguration();
        oldConfiguration.setUserOptions(options);
        Mockito.when(profileFiles.readParsedConfiguration(0)).thenReturn(oldConfiguration);
        Mockito.when(profileFiles.hasParsedConfiguration(0)).thenReturn(true);

        OpenVpnService service = createService();
        service.init();

        OpenVpnProfile profile = profilesById.get(0);
        profile.setKeepAliveMode(KeepAliveMode.CUSTOM);
        profile.setKeepAlivePingTarget("test.com");
        service.saveProfile(profile);

        // check saved profile
        Mockito.verify(dataSource).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals(KeepAliveMode.CUSTOM, profilesById.get(0).getKeepAliveMode());
        Assert.assertEquals("test.com", profilesById.get(0).getKeepAlivePingTarget());

        // change custom keep alive target
        profile.setKeepAlivePingTarget("test2.com");
        service.saveProfile(profile);

        Mockito.verify(dataSource, Mockito.times(2)).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals(KeepAliveMode.CUSTOM, profilesById.get(0).getKeepAliveMode());
        Assert.assertEquals("test2.com", profilesById.get(0).getKeepAlivePingTarget());

        // change keep alive target to eblocker
        profile.setKeepAliveMode(KeepAliveMode.EBLOCKER);
        service.saveProfile(profile);

        Mockito.verify(dataSource, Mockito.times(3)).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals(KeepAliveMode.EBLOCKER, profilesById.get(0).getKeepAliveMode());
        Assert.assertEquals(KEEP_ALIVE_TARGET, profilesById.get(0).getKeepAlivePingTarget());

        // change keep alive target to openvpn config remote
        profile.setKeepAliveMode(KeepAliveMode.OPENVPN_REMOTE);
        service.saveProfile(profile);

        Mockito.verify(dataSource, Mockito.times(4)).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals(KeepAliveMode.OPENVPN_REMOTE, profilesById.get(0).getKeepAliveMode());
        Assert.assertEquals("vpn.eblocker.com", profilesById.get(0).getKeepAlivePingTarget());

        // disable keep alive mode
        profile.setKeepAliveMode(KeepAliveMode.DISABLED);
        service.saveProfile(profile);

        Mockito.verify(dataSource, Mockito.times(5)).save(Mockito.eq(profile), Mockito.eq(0));
        Assert.assertEquals(KeepAliveMode.DISABLED, profilesById.get(0).getKeepAliveMode());
    }

    @Test
    public void testRetrieveProfile() throws IOException {
        Mockito.when(dataSource.get(OpenVpnProfile.class, 3)).thenReturn(createMockProfile(3, "unit-test-profile-3", "username", "password"));
        OpenVpnService service = createService();
        service.init();

        // check non-existing retrieval
        Assert.assertNull(service.getVpnProfileById(100));

        // check exiting profile is returned
        Assert.assertNotNull(service.getVpnProfileById(3));
        Assert.assertEquals(PASSWORD_MASK, service.getVpnProfileById(3).getLoginCredentials().getPassword());
    }

    @Test
    public void testSetProfileClientConfigWithDirectory() throws IOException, OpenVpnConfigurationParser.ParseException {
        OpenVpnService service = createService();
        service.init();

        service.setProfileClientConfig(0, "config");
        Mockito.verify(configurator).createConfiguration("config");
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(0), Mockito.any());
    }

    @Test
    public void testSetProfileClientConfigWithoutDirectory() throws IOException, OpenVpnConfigurationParser.ParseException {
        OpenVpnService service = createService();
        service.init();

        service.setProfileClientConfig(2, "config");
        Mockito.verify(configurator).createConfiguration("config");
        Mockito.verify(profileFiles).createProfileDirectory(2);
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(2), Mockito.any());
    }

    @Test
    public void testSetProfileClientConfigKeepAliveTarget() throws IOException, OpenVpnConfigurationParser.ParseException {
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Collections.singletonList(new SimpleOption(1, "remote", new String[]{ "vpn.eblocker.com" })));
        Mockito.when(configurator.createConfiguration("remote vpn.eblocker.com")).thenReturn(configuration);

        OpenVpnService service = createService();
        service.init();

        service.setProfileClientConfig(0, "remote vpn.eblocker.com");
        Mockito.verify(configurator).createConfiguration("remote vpn.eblocker.com");
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(0), Mockito.any());
        Assert.assertEquals(KeepAliveMode.DISABLED, profilesById.get(0).getKeepAliveMode());
        Assert.assertEquals("vpn.eblocker.com", profilesById.get(0).getKeepAlivePingTarget());
    }

    @Test
    public void testSetProfileClientConfigOption() throws IOException {
        OpenVpnService service = createService();
        service.init();

        byte[] content = "content".getBytes();
        service.setProfileClientConfigOptionFile(0, "option", content);

        Mockito.verify(fileOptionValidator).validate("option", content);
        Mockito.verify(profileFiles).writeConfigOptionFile(0, "option", content);
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(0), Mockito.any());
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void testSetProfileClientConfigOptionInvalid() throws IOException {
        OpenVpnService service = createService();
        service.init();

        byte[] content = "content".getBytes();

        Mockito.doThrow(OpenVpnFileOptionValidator.ValidationException.class).when(fileOptionValidator).validate("option", content);
        service.setProfileClientConfigOptionFile(0, "option", content);
    }

    @Test
    public void testSetProfileClientConfigOptionCredentialExtraction() throws IOException {
        OpenVpnService service = createService();
        service.init();

        // setup mock profile
        service.setProfileClientConfigOptionFile(0, "auth-user-pass", "config-username\nconfig-password\n".getBytes());
        Mockito.verify(profileFiles).writeConfigFile(Mockito.eq(0), Mockito.any());

        // check password has been set
        Assert.assertEquals("config-username", profilesById.get(0).getLoginCredentials().getUsername());
        Assert.assertEquals("config-password", profilesById.get(0).getLoginCredentials().getPassword());
    }

    @Test
    public void testDeleteProfileWithDirectory() throws IOException {
        OpenVpnService service = createService();
        service.init();

        service.deleteVpnProfile(0);
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 0);
        Mockito.verify(profileFiles).removeProfileDirectory(0);
    }

    @Test
    public void testDeleteProfileWithoutDirectory() throws IOException {
        OpenVpnService service = createService();
        service.init();

        service.deleteVpnProfile(0);
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 0);
        Mockito.verify(profileFiles).removeProfileDirectory(0);
    }

    @Test
    public void testDeleteProfileWithRunningClient() throws IOException {
        OpenVpnService service = createService();
        service.init();

        // add single device
        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));

        // check vpn client has been created
        Assert.assertEquals(1, service.getVpnClients().size());

        // store client for later check if correctly released
        VpnClient client = clientsById.get(0);

        // delete profile
        service.deleteVpnProfile(0);

        // check profile deletion is deferred and client shutdown
        Assert.assertTrue(client.getProfile().isDeleted());
        Mockito.verify(client).stop();
        Mockito.verify(dataSource, Mockito.times(0)).delete(OpenVpnProfile.class, 0);

        // run cache clean-up expecting profile to be deleted
        Mockito.when(client.isStopped()).thenReturn(true);
        Mockito.when(client.isClosed()).thenReturn(true);
        Mockito.when(client.getStopInstant()).thenReturn(Instant.now().minusSeconds(STOPPED_CLIENT_TIMEOUT));
        service.getCacheCleaner().run();
        Assert.assertEquals(0, service.getVpnClients().size());
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 0);
        Mockito.verify(profileFiles).removeProfileDirectory(0);
    }

    @Test
    public void testDeleteProfileWithStoppedClient() throws IOException {
        OpenVpnService service = createService();
        service.init();

        // add single device
        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));

        // check vpn client has been created
        Assert.assertEquals(1, service.getVpnClients().size());

        // store client for later check if correctly released
        VpnClient client = clientsById.get(0);
        Mockito.when(client.isStopped()).thenReturn(true);

        // delete profile
        service.deleteVpnProfile(0);

        // check profile deletion is deferred
        Assert.assertTrue(client.getProfile().isDeleted());
        Mockito.verify(client, Mockito.never()).stop();
        Mockito.verify(dataSource, Mockito.never()).delete(OpenVpnProfile.class, 0);

        // run cache clean-up expecting profile to be deleted
        Mockito.when(client.isStopped()).thenReturn(true);
        Mockito.when(client.isClosed()).thenReturn(true);
        Mockito.when(client.getStopInstant()).thenReturn(Instant.now().minusSeconds(STOPPED_CLIENT_TIMEOUT));
        service.getCacheCleaner().run();
        Assert.assertEquals(0, service.getVpnClients().size());
        Mockito.verify(dataSource).delete(OpenVpnProfile.class, 0);
        Mockito.verify(profileFiles).removeProfileDirectory(0);
    }

    @Test
    public void testGetStatusClientRunningAndConnected() {
        OpenVpnService service = createService();
        service.init();

        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));

        VpnClient client = clientsById.get(0);
        Mockito.when(client.isStopped()).thenReturn(false);
        Mockito.when(client.isUp()).thenReturn(true);
        Mockito.when(client.getExitStatus()).thenReturn(null);

        VpnStatus status = service.getStatus(profilesById.get(0));
        Assert.assertTrue(status.isActive());
        Assert.assertTrue(status.isUp());
        Assert.assertEquals(Collections.singleton(device.getId()), status.getDevices());
        Assert.assertTrue(status.getErrors().isEmpty());
        Assert.assertNull(status.getExitStatus());
    }

    @Test
    public void testGetStatusClientRunningAndNotConnected() {
        OpenVpnService service = createService();
        service.init();

        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));

        VpnClient client = clientsById.get(0);
        Mockito.when(client.isStopped()).thenReturn(false);
        Mockito.when(client.isUp()).thenReturn(false);
        Mockito.when(client.getExitStatus()).thenReturn(null);

        VpnStatus status = service.getStatus(profilesById.get(0));
        Assert.assertTrue(status.isActive());
        Assert.assertFalse(status.isUp());
        Assert.assertEquals(Collections.singleton(device.getId()), status.getDevices());
        Assert.assertTrue(status.getErrors().isEmpty());
        Assert.assertNull(status.getExitStatus());
    }

    @Test
    public void testGetStatusClientCachedAndStopped() {
        OpenVpnService service = createService();
        service.init();

        Device device = createDevice(0);
        service.routeClientThroughVpnTunnel(device, profilesById.get(0));

        VpnClient client = clientsById.get(0);
        Mockito.when(client.isStopped()).thenReturn(true);
        Mockito.when(client.isUp()).thenReturn(false);
        Mockito.when(client.getExitStatus()).thenReturn(1);
        Mockito.when(client.getLog()).thenReturn(Collections.singletonList("error"));

        VpnStatus status = service.getStatus(profilesById.get(0));
        Assert.assertFalse(status.isActive());
        Assert.assertFalse(status.isUp());
        Assert.assertEquals(Collections.singleton(device.getId()), status.getDevices());
        Assert.assertEquals(Collections.singletonList("error"), status.getErrors());
        Assert.assertEquals(Integer.valueOf(1), status.getExitStatus());
    }

    @Test
    public void testGetStatusClientNotCached() {
        OpenVpnService service = createService();
        service.init();

        VpnStatus status = service.getStatus(profilesById.get(0));
        Assert.assertFalse(status.isActive());
        Assert.assertFalse(status.isUp());
        Assert.assertTrue(status.getDevices().isEmpty());
        Assert.assertTrue(status.getErrors().isEmpty());
        Assert.assertNull(status.getExitStatus());
    }

    private OpenVpnService createService() {
        return new OpenVpnService(scriptRunner, dataSource, clientFactory, configurator, fileOptionValidator, KEEP_ALIVE_TARGET, KILL_ALL_INSTANCES_SCRIPT, profileFiles, PASSWORD_MASK, STOPPED_CLIENT_TIMEOUT);
    }

    private OpenVpnClient createMockOpenVpnClient(VpnProfile profile) {
        Set<String> deviceIds = new HashSet<>();
        OpenVpnClient client = Mockito.mock(OpenVpnClient.class);
        Mockito.when(client.getProfile()).then(i -> profile);
        Mockito.when(client.getDeviceIds()).then(i -> deviceIds);
        Mockito.doAnswer(
                i -> deviceIds.addAll(
                        ((Collection<Device>) i.getArguments()[0]).stream()
                                .map(Device::getId)
                                .collect(Collectors.toList())))
                .when(client).addDevices(Mockito.any());
        Mockito.doAnswer(
                i -> deviceIds.removeAll(
                        ((Collection<Device>) i.getArguments()[0]).stream()
                                .map(Device::getId)
                                .collect(Collectors.toList())))
                .when(client).stopDevices(Mockito.any());
        return client;
    }

    private OpenVpnProfile createMockProfile(Integer id, String name, String username, String password) {
        OpenVpnProfile profile = new OpenVpnProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setConfigurationFileVersion(3);
        if (username != null) {
            VpnLoginCredentials credentials = new VpnLoginCredentials();
            credentials.setUsername(username);
            credentials.setPassword(password);
            profile.setLoginCredentials(credentials);
        }
        return profile;
    }

    private OpenVpnProfile copyProfile(OpenVpnProfile source) {
        if (source == null) {
            return null;
        }

        OpenVpnProfile copy = new OpenVpnProfile();
        copy.setId(source.getId());
        copy.setConfigurationFileVersion(source.getConfigurationFileVersion());
        copy.setDeleted(source.isDeleted());
        copy.setDescription(source.getDescription());
        copy.setEnabled(source.isEnabled());
        copy.setKeepAliveMode(source.getKeepAliveMode());
        copy.setKeepAlivePingTarget(source.getKeepAlivePingTarget());
        copy.setName(source.getName());
        copy.setTemporary(source.isTemporary());
        if (source.getLoginCredentials() != null) {
            copy.setLoginCredentials(new VpnLoginCredentials());
            copy.getLoginCredentials().setUsername(source.getLoginCredentials().getUsername());
            copy.getLoginCredentials().setPassword(source.getLoginCredentials().getPassword());
        }
        return copy;
    }
}
