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
package org.eblocker.server.common.network.unix;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardRuntimePeerSelector;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

public class NetworkServicesUnixTest { // FIXME: there is also a NetworkServiceUnixTest testing the same class!

    private static final String APPLY_NETWORK_CONFIG_COMMAND = "apply_config";
    private static final String APPLY_FIREWALL_COMMAND = "apply_firewall";
    private static final String ENABLE_IP6_COMMAND = "enable_ip6";

    private DataSource dataSource;
    private FirewallConfigurationIp4 firewallConfiguration;
    private FirewallConfigurationIp6 firewallConfigurationIp6;
    private ScriptRunner scriptRunner;
    private NetworkServicesUnix networkServices;
    private DeviceService deviceService;
    private FeatureToggleRouter featureToggleRouter;
    private EblockerDnsServer eblockerDnsServer;
    private WireGuardRuntimePeerSelector wireGuardRuntimePeerSelector;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        firewallConfiguration = Mockito.mock(FirewallConfigurationIp4.class);
        firewallConfigurationIp6 = Mockito.mock(FirewallConfigurationIp6.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        deviceService = Mockito.mock(DeviceService.class);
        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);
        wireGuardRuntimePeerSelector =
                Mockito.mock(WireGuardRuntimePeerSelector.class);

        networkServices = new NetworkServicesUnix(
                dataSource,
                null,
                null,
                null,
                firewallConfiguration,
                firewallConfigurationIp6,
                null,
                null,
                null,
                scriptRunner,
                featureToggleRouter,
                0,
                0,
                APPLY_NETWORK_CONFIG_COMMAND,
                APPLY_FIREWALL_COMMAND,
                ENABLE_IP6_COMMAND,
                eblockerDnsServer,
                deviceService,
                wireGuardRuntimePeerSelector
        );
    }

    @Test
    public void testApplyNetworkConfigurationSuccess() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(APPLY_NETWORK_CONFIG_COMMAND)).thenReturn(0);
        networkServices.applyNetworkConfiguration(new NetworkConfiguration());
        Mockito.verify(scriptRunner).runScript(APPLY_NETWORK_CONFIG_COMMAND);
    }

    @Test(expected = EblockerException.class)
    public void testApplyNetworkConfigurationFailure() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(APPLY_NETWORK_CONFIG_COMMAND)).thenReturn(1);
        networkServices.applyNetworkConfiguration(new NetworkConfiguration());
        Mockito.verify(scriptRunner).runScript(APPLY_NETWORK_CONFIG_COMMAND);
    }

    @Test
    public void testEnableFirewallSuccess() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv4")).thenReturn(0);
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv6")).thenReturn(0);
        networkServices.enableFirewall(new HashSet<>(), new HashSet<>(), new HashSet<>(), false, false, false, false, false, false);
        ArgumentCaptor<Supplier<Boolean>> captor = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(firewallConfiguration).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyCollection(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertTrue(captor.getValue().get());
        Mockito.verify(firewallConfigurationIp6).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertTrue(captor.getValue().get());
    }

    @Test
    public void testEnableFirewallFailure() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv4")).thenReturn(1);
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv6")).thenReturn(1);
        networkServices.enableFirewall(new HashSet<>(), new HashSet<>(), new HashSet<>(), false, false, false, false, false, false);
        ArgumentCaptor<Supplier<Boolean>> captor = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(firewallConfiguration).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyCollection(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertFalse(captor.getValue().get());
        Mockito.verify(firewallConfigurationIp6).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertFalse(captor.getValue().get());
    }


    @Test
    public void testPublicEnableFirewallUsesProjectedWireGuardPeers()
            throws IOException, InterruptedException {

        WireGuardPeer allowed = new WireGuardPeer();
        allowed.setId(1);

        WireGuardPeer denied = new WireGuardPeer();
        denied.setId(2);

        List<WireGuardPeer> persisted =
                Arrays.asList(allowed, denied);

        List<WireGuardPeer> runtime =
                Collections.singletonList(allowed);

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(persisted);

        Mockito.when(
                deviceService.getDevices(true)
        ).thenReturn(Collections.emptyList());

        Mockito.when(
                wireGuardRuntimePeerSelector.select(persisted)
        ).thenReturn(runtime);

        networkServices.enableFirewall(
                false,
                false,
                false,
                true,
                false
        );

        Mockito.verify(
                wireGuardRuntimePeerSelector
        ).select(persisted);

        ArgumentCaptor<Collection> peerCaptor =
                ArgumentCaptor.forClass(Collection.class);

        Mockito.verify(firewallConfiguration).enable(
                Mockito.anySet(),
                Mockito.any(),
                peerCaptor.capture(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.any()
        );

        Assert.assertEquals(
                runtime,
                peerCaptor.getValue()
        );
    }

    @Test
    public void testUpdateIp6State() throws IOException, InterruptedException {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        Mockito.when(featureToggleRouter.shouldSendRouterAdvertisements()).thenReturn(true);
        Mockito.when(featureToggleRouter.arePrivacyExtensionsEnabled()).thenReturn(false);
        networkServices.updateIp6State();
        Mockito.verify(scriptRunner).runScript(ENABLE_IP6_COMMAND, "true", "false");
    }
}
