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
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashSet;
import java.util.function.Supplier;

public class NetworkServicesUnixTest {

    private static final String APPLY_NETWORK_CONFIG_COMMAND = "apply_config";
    private static final String APPLY_FIREWALL_COMMAND = "apply_firewall";
    private static final String ENABLE_IP6_COMMAND = "enable_ip6";

    private FirewallConfigurationIp4 firewallConfiguration;
    private FirewallConfigurationIp6 firewallConfigurationIp6;
    private ScriptRunner scriptRunner;
    private NetworkServicesUnix networkServices;

    @Before
    public void setUp() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        firewallConfiguration = Mockito.mock(FirewallConfigurationIp4.class);
        firewallConfigurationIp6 = Mockito.mock(FirewallConfigurationIp6.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        networkServices = new NetworkServicesUnix(dataSource, null, null, null, firewallConfiguration, firewallConfigurationIp6, null, null, null, scriptRunner, 0, 0, APPLY_NETWORK_CONFIG_COMMAND, APPLY_FIREWALL_COMMAND, ENABLE_IP6_COMMAND, null);
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
        networkServices.enableFirewall(new HashSet<>(), new HashSet<>(), false, false, false, false, false);
        ArgumentCaptor<Supplier<Boolean>> captor = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(firewallConfiguration).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertTrue(captor.getValue().get());
        Mockito.verify(firewallConfigurationIp6).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertTrue(captor.getValue().get());
    }

    @Test
    public void testEnableFirewallFailure() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv4")).thenReturn(1);
        Mockito.when(scriptRunner.runScript(APPLY_FIREWALL_COMMAND, "IPv6")).thenReturn(1);
        networkServices.enableFirewall(new HashSet<>(), new HashSet<>(), false, false, false, false, false);
        ArgumentCaptor<Supplier<Boolean>> captor = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(firewallConfiguration).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertFalse(captor.getValue().get());
        Mockito.verify(firewallConfigurationIp6).enable(Mockito.anySet(), Mockito.anySet(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), captor.capture());
        Assert.assertFalse(captor.getValue().get());
    }

}
