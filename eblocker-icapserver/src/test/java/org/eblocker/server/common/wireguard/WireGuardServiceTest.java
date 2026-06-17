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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.vpn.KeepAliveMode;
import org.eblocker.server.common.data.vpn.VpnProfile;
import org.eblocker.server.common.data.vpn.VpnStatus;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.vpn.RoutingController;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfigurationParser;
import org.eblocker.server.common.wireguard.configuration.WireGuardRuntimeConfigurationRenderer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class WireGuardServiceTest {
    private Path profileRoot;
    private DataSource dataSource;
    private ScriptRunner scriptRunner;
    private RoutingController routingController;
    private SquidConfigController squidConfigController;
    private NetworkStateMachine networkStateMachine;
    private EblockerDnsServer eblockerDnsServer;
    private WireGuardProfileFiles profileFiles;
    private WireGuardService service;

    @Before
    public void setUp() throws IOException {
        profileRoot = Files.createTempDirectory("wireguard-service-test");
        dataSource = Mockito.mock(DataSource.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        routingController = Mockito.mock(RoutingController.class);
        squidConfigController = Mockito.mock(SquidConfigController.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);
        profileFiles = new WireGuardProfileFiles(profileRoot.toString(), new ObjectMapper());
        service = new WireGuardService(
                scriptRunner,
                dataSource,
                routingController,
                squidConfigController,
                networkStateMachine,
                eblockerDnsServer,
                new WireGuardConfigurationParser(),
                new WireGuardRuntimeConfigurationRenderer(),
                profileFiles,
                "wireguard_killall",
                "wireguard_start",
                "wireguard_down",
                "wireguard_setclientroute",
                "wireguard_clearclientroute",
                "eblocker.org");
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(profileRoot);
    }

    @Test
    public void initDeletesTemporaryAndDeletedProfilesAndKillsDanglingInterfaces() throws Exception {
        WireGuardProfile temporary = new WireGuardProfile(1, "temporary");
        temporary.setTemporary(true);
        WireGuardProfile deleted = new WireGuardProfile(2, "deleted");
        deleted.setDeleted(true);
        WireGuardProfile active = new WireGuardProfile(3, "active");
        Mockito.when(dataSource.getAll(WireGuardProfile.class)).thenReturn(Arrays.asList(temporary, deleted, active));
        profileFiles.createProfileDirectory(1);
        profileFiles.createProfileDirectory(2);
        profileFiles.createProfileDirectory(3);

        service.init();

        Mockito.verify(dataSource).delete(WireGuardProfile.class, 1);
        Mockito.verify(dataSource).delete(WireGuardProfile.class, 2);
        Mockito.verify(dataSource, Mockito.never()).delete(WireGuardProfile.class, 3);
        Mockito.verify(scriptRunner).runScript("wireguard_killall", profileRoot.toString());
        Assert.assertFalse(Files.exists(profileRoot.resolve("1")));
        Assert.assertFalse(Files.exists(profileRoot.resolve("2")));
        Assert.assertTrue(Files.exists(profileRoot.resolve("3")));
    }

    @Test
    public void serviceIsStartedWithHttpsSubsystem() throws Exception {
        SubSystemService serviceAnnotation = WireGuardService.class.getAnnotation(SubSystemService.class);

        Assert.assertNotNull(serviceAnnotation);
        Assert.assertEquals(SubSystem.HTTPS_SERVER, serviceAnnotation.value());
        Assert.assertNotNull(WireGuardService.class.getMethod("init").getAnnotation(SubSystemInit.class));
    }

    @Test
    public void saveProfileCreatesNewWireGuardProfile() throws Exception {
        WireGuardProfile input = new WireGuardProfile(null, "provider");
        input.setDescription("description");
        input.setEnabled(true);
        input.setNameServersEnabled(false);
        input.setKeepAliveMode(KeepAliveMode.EBLOCKER);
        Mockito.when(dataSource.nextId(WireGuardProfile.class)).thenReturn(42);
        Mockito.when(dataSource.save(Mockito.any(WireGuardProfile.class), Mockito.eq(42))).thenAnswer(invocation -> invocation.getArgument(0));

        WireGuardProfile saved = service.saveProfile(input);

        Assert.assertEquals(Integer.valueOf(42), saved.getId());
        Assert.assertEquals("provider", saved.getName());
        Assert.assertEquals("description", saved.getDescription());
        Assert.assertTrue(saved.isEnabled());
        Assert.assertFalse(saved.isNameServersEnabled());
        Assert.assertEquals(KeepAliveMode.EBLOCKER, saved.getKeepAliveMode());
        Assert.assertEquals("eblocker.org", saved.getKeepAlivePingTarget());
        Assert.assertEquals(Integer.valueOf(1), saved.getConfigurationFileVersion());
    }

    @Test(expected = IOException.class)
    public void saveProfileRejectsUpdateOfMissingProfile() throws Exception {
        WireGuardProfile input = new WireGuardProfile(99, "missing");
        Mockito.when(dataSource.get(WireGuardProfile.class, 99)).thenReturn(null);

        service.saveProfile(input);
    }

    @Test
    public void getVpnProfilesReturnsNonDeletedProfilesOnly() {
        WireGuardProfile active = new WireGuardProfile(1, "active");
        WireGuardProfile deleted = new WireGuardProfile(2, "deleted");
        deleted.setDeleted(true);
        Mockito.when(dataSource.getAll(WireGuardProfile.class)).thenReturn(Arrays.asList(active, deleted));

        Collection<VpnProfile> profiles = service.getVpnProfiles();

        Assert.assertEquals(1, profiles.size());
        Assert.assertEquals("active", profiles.iterator().next().getName());
    }

    @Test
    public void setProfileClientConfigParsesStoresAndRendersRuntimeConfig() throws Exception {
        WireGuardProfile profile = new WireGuardProfile(7, "provider");
        Mockito.when(dataSource.get(WireGuardProfile.class, 7)).thenReturn(profile);
        Mockito.when(dataSource.save(Mockito.any(WireGuardProfile.class), Mockito.eq(7))).thenAnswer(invocation -> invocation.getArgument(0));
        String config = "[Interface]\n" +
                "PrivateKey = private=\n" +
                "Address = 10.0.0.2/32\n" +
                "DNS = 10.0.0.1\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = public=\n" +
                "Endpoint = vpn.example.net:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";

        WireGuardConfiguration parsed = service.setProfileClientConfig(7, config);

        Assert.assertNotNull(parsed);
        Assert.assertEquals(config, profileFiles.readImportedConfig(7));
        Assert.assertEquals("private=", profileFiles.readParsedConfiguration(7).getPrivateKey());
        String runtimeConfig = Files.readString(Path.of(profileFiles.getRuntimeConfig(7)), StandardCharsets.UTF_8);
        Assert.assertTrue(runtimeConfig.contains("Table = off\n"));
        Assert.assertTrue(runtimeConfig.contains("Endpoint = vpn.example.net:51820\n"));
        ArgumentCaptor<WireGuardProfile> profileCaptor = ArgumentCaptor.forClass(WireGuardProfile.class);
        Mockito.verify(dataSource).save(profileCaptor.capture(), Mockito.eq(7));
        Assert.assertEquals("vpn.example.net", profileCaptor.getValue().getKeepAlivePingTarget());
    }

    @Test
    public void setProfileClientConfigReturnsNullForInvalidConfig() throws Exception {
        WireGuardProfile profile = new WireGuardProfile(7, "provider");
        Mockito.when(dataSource.get(WireGuardProfile.class, 7)).thenReturn(profile);

        WireGuardConfiguration parsed = service.setProfileClientConfig(7, "[Interface]\nAddress = 10.0.0.2/32\n");

        Assert.assertNull(parsed);
        Assert.assertFalse(Files.exists(profileRoot.resolve("7")));
    }

    @Test
    public void startAndStopRunWireGuardScripts() throws Exception {
        WireGuardProfile profile = new WireGuardProfile(7, "provider");
        Mockito.when(dataSource.get(WireGuardProfile.class, 7)).thenReturn(profile);

        service.startVpn(profile);
        service.stopVpn(profile);

        Mockito.verify(scriptRunner).runScript("wireguard_start", "7", profileFiles.getRuntimeConfig(7), profileFiles.getLogFile(7));
        Mockito.verify(scriptRunner).runScript("wireguard_down", "7", profileFiles.getRuntimeConfig(7), profileFiles.getLogFile(7));
    }

    @Test
    public void routeClientStartsWireGuardConfiguresPolicyRoutingDnsAndAcls() throws Exception {
        WireGuardProfile profile = new WireGuardProfile(7, "provider");
        profile.setNameServersEnabled(true);
        Device device = createDevice("device:1");
        Mockito.when(routingController.createRoute()).thenReturn(17);
        storeConfig(7, "[Interface]\n" +
                "PrivateKey = private=\n" +
                "Address = 10.0.0.2/32\n" +
                "DNS = 10.0.0.1\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = public=\n" +
                "Endpoint = vpn.example.net:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n");

        service.routeClientThroughVpnTunnel(device, profile);

        Mockito.verify(scriptRunner).runScript("wireguard_start", "7", profileFiles.getRuntimeConfig(7), profileFiles.getLogFile(7));
        Mockito.verify(scriptRunner).runScript("wireguard_setclientroute", "17", "wg7");
        Mockito.verify(eblockerDnsServer).addVpnResolver(7, Collections.singletonList("10.0.0.1"), "10.0.0.2");
        Mockito.verify(eblockerDnsServer).useVpnResolver(device, 7);
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(7, Collections.singleton(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();

        VpnStatus status = service.getStatus(profile);
        Assert.assertEquals(7, status.getProfileId());
        Assert.assertTrue(status.isActive());
        Assert.assertTrue(status.isUp());
        Assert.assertEquals(Collections.singleton("device:1"), status.getDevices());
        VpnStatus deviceStatus = service.getStatusByDevice(device);
        Assert.assertNotNull(deviceStatus);
        Assert.assertEquals(status.getProfileId(), deviceStatus.getProfileId());
        Assert.assertEquals(status.getDevices(), deviceStatus.getDevices());
    }

    @Test
    public void restoreNormalRoutingStopsLastWireGuardClientAndClearsState() throws Exception {
        WireGuardProfile profile = new WireGuardProfile(7, "provider");
        profile.setNameServersEnabled(true);
        Device device = createDevice("device:1");
        Mockito.when(routingController.createRoute()).thenReturn(17);
        storeConfig(7, "[Interface]\n" +
                "PrivateKey = private=\n" +
                "Address = 10.0.0.2/32\n" +
                "DNS = 10.0.0.1\n" +
                "\n" +
                "[Peer]\n" +
                "PublicKey = public=\n" +
                "Endpoint = vpn.example.net:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n");
        service.routeClientThroughVpnTunnel(device, profile);

        service.restoreNormalRoutingForClient(device);

        Mockito.verify(eblockerDnsServer).useDefaultResolver(device);
        Mockito.verify(scriptRunner).runScript("wireguard_clearclientroute", "17");
        Mockito.verify(scriptRunner).runScript("wireguard_down", "7", profileFiles.getRuntimeConfig(7), profileFiles.getLogFile(7));
        Mockito.verify(eblockerDnsServer).removeVpnResolver(7);
        Mockito.verify(routingController).deleteRoute(17);
        Mockito.verify(squidConfigController).updateVpnDevicesAcl(7, Collections.emptySet());
        Mockito.verify(networkStateMachine, Mockito.times(2)).deviceStateChanged();

        VpnStatus status = service.getStatus(profile);
        Assert.assertFalse(status.isActive());
        Assert.assertFalse(status.isUp());
        Assert.assertTrue(status.getDevices().isEmpty());
        Assert.assertNull(service.getStatusByDevice(device));
    }

    private void storeConfig(int id, String config) throws Exception {
        WireGuardConfiguration configuration = new WireGuardConfigurationParser().parse(config);
        profileFiles.createProfileDirectory(id);
        profileFiles.writeParsedConfiguration(id, configuration);
        profileFiles.writeRuntimeConfig(id, new WireGuardRuntimeConfigurationRenderer().render(configuration));
    }

    private Device createDevice(String id) {
        Device device = new Device();
        device.setId(id);
        return device;
    }

}
