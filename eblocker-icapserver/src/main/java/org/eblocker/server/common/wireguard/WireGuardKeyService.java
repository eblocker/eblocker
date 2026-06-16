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
import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.system.CommandRunner;

import java.io.IOException;

@Singleton
public class WireGuardKeyService {
    private final CommandRunner commandRunner;
    private final String generatePrivateKeyCommand;
    private final String generatePublicKeyCommand;
    private final String generatePresharedKeyCommand;

    @Inject
    public WireGuardKeyService(CommandRunner commandRunner,
                               @Named("wireguard.generate.private.key.command") String generatePrivateKeyCommand,
                               @Named("wireguard.generate.public.key.command") String generatePublicKeyCommand,
                               @Named("wireguard.generate.preshared.key.command") String generatePresharedKeyCommand) {
        this.commandRunner = commandRunner;
        this.generatePrivateKeyCommand = generatePrivateKeyCommand;
        this.generatePublicKeyCommand = generatePublicKeyCommand;
        this.generatePresharedKeyCommand = generatePresharedKeyCommand;
    }

    public WireGuardKeyPair generateKeyPair() throws IOException, InterruptedException {
        String privateKey = commandRunner.runCommandWithOutput(generatePrivateKeyCommand).trim();
        String publicKey = commandRunner.runCommandWithOutput(generatePublicKeyCommand, privateKey).trim();
        return new WireGuardKeyPair(privateKey, publicKey);
    }

    public String generatePresharedKey() throws IOException, InterruptedException {
        return commandRunner.runCommandWithOutput(generatePresharedKeyCommand).trim();
    }
}
