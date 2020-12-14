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

import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkInterfaceAliasesTest {

    private final List<String> INTERFACE_NAME_LOOKUP = Arrays.asList("eth1", "eth0", "en8", "en7", "en4", "en2", "en1", "en0", "enp0s25");
    private final int ALIASES_COUNT = 16;
    private final int ALIAS_MIN = 20;
    private final int ALIAS_MAX = ALIAS_MIN + ALIASES_COUNT - 1;
    private final String SCRIPT_ALIAS_ADD = "SCRIPT_ALIAS_ADD";
    private final String SCRIPT_ALIAS_REMOVE = "SCRIPT_ALIAS_REMOVE";

    private NetworkInterfaceAliases networkInterfaceAliases;
    private ScriptRunner scriptRunner;

    private String networkInterfaceName;

    @Before
    public void setup() throws SocketException {
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (INTERFACE_NAME_LOOKUP.contains(networkInterface.getName())) {
                networkInterfaceName = networkInterface.getName();
            }
        }
        Assert.assertNotNull("Cannot find any network interface from this list: " + INTERFACE_NAME_LOOKUP.stream().collect(Collectors.joining(", ")), networkInterfaceName);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        networkInterfaceAliases = new NetworkInterfaceAliases(networkInterfaceName, ALIAS_MIN, ALIAS_MAX, SCRIPT_ALIAS_ADD, SCRIPT_ALIAS_REMOVE, scriptRunner);
    }

    @Test
    public void testSingleAddRemove() throws IOException, InterruptedException {
        String ip = "10.10.10.10";
        String netmask = "255.255.255.255";

        String alias = networkInterfaceAliases.add(ip, netmask);
        Mockito.verify(scriptRunner).runScript(SCRIPT_ALIAS_ADD, alias, ip, netmask);
        Assert.assertEquals(networkInterfaceName + ":" + ALIAS_MIN, alias);
        Assert.assertTrue(networkInterfaceAliases.getAliasedIps().contains(ip));

        networkInterfaceAliases.remove(alias);
        Mockito.verify(scriptRunner).runScript(SCRIPT_ALIAS_REMOVE, alias);
        Assert.assertFalse(networkInterfaceAliases.getAliasedIps().contains(ip));
    }

    @Test
    public void testNonExistingRemoval() throws IOException, InterruptedException {
        String alias = networkInterfaceName + ":" + 77;
        networkInterfaceAliases.remove(alias);
        Mockito.verify(scriptRunner, Mockito.never()).runScript(Mockito.eq(SCRIPT_ALIAS_REMOVE), Mockito.any(String.class));
    }

    @Test
    public void testRecycling() {
        Deque<String> aliases = new ArrayDeque<>(1 + ALIAS_MAX - ALIAS_MIN);
        for (int i = 0; i < ALIASES_COUNT * 16; ++i) {
            if (aliases.size() == ALIASES_COUNT) {
                networkInterfaceAliases.remove(aliases.poll());
            }
            String ip = "10.10.10." + i % 256;
            String alias = networkInterfaceAliases.add(ip, "255.255.255.255");
            Assert.assertNotNull(alias);
            aliases.add(alias);
        }
    }

    @Test
    public void testExhaustion() {
        for (int i = 0; i < ALIASES_COUNT; ++i) {
            networkInterfaceAliases.add("10.10.10." + i, "255.255.255.255");
        }
        Assert.assertNull(networkInterfaceAliases.add("10.10.11.10", "255.255.255.255"));
    }

}
