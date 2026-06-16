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

import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.system.CommandRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class WireGuardKeyServiceTest {
    private CommandRunner commandRunner;
    private WireGuardKeyService service;

    @Before
    public void setUp() {
        commandRunner = Mockito.mock(CommandRunner.class);
        service = new WireGuardKeyService(commandRunner, "wireguard_genkey", "wireguard_pubkey", "wireguard_genpsk");
    }

    @Test
    public void generatesKeyPairViaConfiguredCommands() throws Exception {
        Mockito.when(commandRunner.runCommandWithOutput("wireguard_genkey")).thenReturn(" private-key\n");
        Mockito.when(commandRunner.runCommandWithOutput("wireguard_pubkey", "private-key")).thenReturn(" public-key\n");

        WireGuardKeyPair keyPair = service.generateKeyPair();

        Assert.assertEquals("private-key", keyPair.getPrivateKey());
        Assert.assertEquals("public-key", keyPair.getPublicKey());
    }

    @Test
    public void generatesPresharedKeyViaConfiguredCommand() throws Exception {
        Mockito.when(commandRunner.runCommandWithOutput("wireguard_genpsk")).thenReturn(" preshared-key\n");

        Assert.assertEquals("preshared-key", service.generatePresharedKey());
    }
}
