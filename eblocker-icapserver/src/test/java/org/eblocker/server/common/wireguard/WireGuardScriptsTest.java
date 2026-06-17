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

import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WireGuardScriptsTest {
    private Path tempDir;
    private Path fakeBin;
    private Path commandLog;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wireguard-scripts-test");
        fakeBin = tempDir.resolve("bin");
        commandLog = tempDir.resolve("commands.log");
        Files.createDirectory(fakeBin);
        writeFakeCommand("redis-cli", "printf 'redis-cli %s\\n' \"$*\" >> \"$COMMAND_LOG\"\n");
        writeFakeCommand("wg-quick", "printf 'wg-quick %s\\n' \"$*\" >> \"$COMMAND_LOG\"\nprintf 'wg-quick %s\\n' \"$*\"\n");
        writeFakeCommand("ip", "printf 'ip %s\\n' \"$*\" >> \"$COMMAND_LOG\"\ncase \"$*\" in\n  'route show default') printf 'default via 192.168.138.1 dev eth0 proto dhcp src 192.168.138.105 metric 100\\n' ;;\n  '-4 route show dev eblocker-mobile proto kernel scope link') printf '10.8.0.0/24 proto kernel scope link src 10.8.0.1\\n' ;;\n  '-6 route show dev eblocker-mobile proto kernel scope link') printf 'fd42:eb10:8::/64 proto kernel metric 256 pref medium\\n' ;;\n  '-6 route show dev eblocker-mobile') printf 'fd42:eb10:8::/64 proto kernel metric 256 pref medium\\n' ;;\n  '-4 address show dev eblocker-mobile') printf '3: eblocker-mobile: <POINTOPOINT> mtu 1420\\n    inet 10.8.0.1/24 scope global eblocker-mobile\\n' ;;\n  '-6 address show dev eblocker-mobile') printf '3: eblocker-mobile: <POINTOPOINT> mtu 1420\\n    inet6 fd42:eb10:8::1/64 scope global\\n' ;;\nesac\n");
        writeFakeCommand("iptables", "printf 'iptables %s\\n' \"$*\" >> \"$COMMAND_LOG\"\ncase \" $* \" in *' -C '*|*' -D '*) exit 1 ;; esac\n");
        writeFakeCommand("ip6tables", "printf 'ip6tables %s\\n' \"$*\" >> \"$COMMAND_LOG\"\ncase \" $* \" in *' -C '*|*' -D '*) exit 1 ;; esac\n");
        writeFakeCommand("wg", "printf 'wg %s\\n' \"$*\" >> \"$COMMAND_LOG\"\ncase \"$1\" in\n  genkey) printf 'private-key\\n' ;;\n  genpsk) printf 'preshared-key\\n' ;;\n  pubkey) read PRIVATE_KEY; printf 'public-for-%s\\n' \"$PRIVATE_KEY\" ;;\nesac\n");
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void wireGuardStartStartsConfigAndPublishesInterfaceUp() throws Exception {
        Path config = tempDir.resolve("wg42.conf");
        Path log = tempDir.resolve("wg42.log");
        Files.writeString(config, "[Interface]\nPrivateKey = private=\n", StandardCharsets.UTF_8);

        ProcessResult result = runScript("wireguard_start", "42", config.toString(), log.toString());

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "wg-quick up " + config,
                "redis-cli PUBLISH vpn_profile_status:42:in up wg42"), readCommandLog());
        Assert.assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("wg-quick up " + config));
    }

    @Test
    public void wireGuardStartFailsWhenConfigIsMissing() throws Exception {
        Path log = tempDir.resolve("missing.log");

        ProcessResult result = runScript("wireguard_start", "42", tempDir.resolve("missing.conf").toString(), log.toString());

        Assert.assertEquals(1, result.exitCode);
        Assert.assertTrue(result.stderr.contains("The config file can not be found"));
    }

    @Test
    public void wireGuardDownStopsConfigAndPublishesInterfaceDown() throws Exception {
        Path config = tempDir.resolve("wg42.conf");
        Path log = tempDir.resolve("wg42-down.log");
        Files.writeString(config, "[Interface]\nPrivateKey = private=\n", StandardCharsets.UTF_8);

        ProcessResult result = runScript("wireguard_down", "42", config.toString(), log.toString());

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "wg-quick down " + config,
                "redis-cli PUBLISH vpn_profile_status:42:in down wg42"), readCommandLog());
        Assert.assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("wg-quick down " + config));
    }

    @Test
    public void wireGuardSetClientRouteAddsPolicyDefaultRoute() throws Exception {
        ProcessResult result = runScript("wireguard_setclientroute", "42", "wg42");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "ip route show table wireguard42 default",
                "ip route add table wireguard42 default dev wg42",
                "ip route flush cache"), readCommandLog());
    }

    @Test
    public void wireGuardSetClientRouteIp6AddsPolicyDefaultRoute() throws Exception {
        ProcessResult result = runScript("wireguard_setclientroute_ip6", "42", "wg42");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "ip -6 route show table wireguard42 default",
                "ip -6 route add table wireguard42 default dev wg42",
                "ip -6 route flush cache"), readCommandLog());
    }

    @Test
    public void wireGuardClearClientRouteDeletesPolicyDefaultRoute() throws Exception {
        ProcessResult result = runScript("wireguard_clearclientroute", "42");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "ip route show table wireguard42 default",
                "ip route del table wireguard42 default",
                "ip -6 route show table wireguard42 default",
                "ip -6 route del table wireguard42 default",
                "ip route flush cache",
                "ip -6 route flush cache"), readCommandLog());
    }

    @Test
    public void wireGuardKillAllStopsAllRuntimeConfigsBelowProfilePath() throws Exception {
        Path profileOne = Files.createDirectories(tempDir.resolve("profiles/1"));
        Path profileTwo = Files.createDirectories(tempDir.resolve("profiles/2"));
        Path configOne = profileOne.resolve("wg1.conf");
        Path configTwo = profileTwo.resolve("wg2.conf");
        Files.writeString(configOne, "[Interface]\nPrivateKey = one=\n", StandardCharsets.UTF_8);
        Files.writeString(configTwo, "[Interface]\nPrivateKey = two=\n", StandardCharsets.UTF_8);

        ProcessResult result = runScript("wireguard_killall", tempDir.resolve("profiles").toString());

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals(Arrays.asList(
                "wg-quick down " + configOne,
                "wg-quick down " + configTwo), readCommandLog());
    }

    @Test
    public void wireGuardGenKeyPrintsNewPrivateKey() throws Exception {
        ProcessResult result = runScript("wireguard_genkey");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals("private-key\n", result.stdout);
        Assert.assertEquals(Arrays.asList("wg genkey"), readCommandLog());
    }

    @Test
    public void wireGuardPubKeyDerivesPublicKeyFromPrivateKeyArgument() throws Exception {
        ProcessResult result = runScript("wireguard_pubkey", "private-key");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals("public-for-private-key\n", result.stdout);
        Assert.assertEquals(Arrays.asList("wg pubkey"), readCommandLog());
    }

    @Test
    public void wireGuardGenPskPrintsNewPresharedKey() throws Exception {
        ProcessResult result = runScript("wireguard_genpsk");

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        Assert.assertEquals("preshared-key\n", result.stdout);
        Assert.assertEquals(Arrays.asList("wg genpsk"), readCommandLog());
    }

    @Test
    public void wireGuardMobileStartConfiguresIpv4AndIpv6ForwardingAndDns() throws Exception {
        Path config = tempDir.resolve("eblocker-mobile.conf");
        Files.writeString(config, "[Interface]\nPrivateKey = private=\n", StandardCharsets.UTF_8);

        ProcessResult result = runScript("wireguard_mobile_start", config.toString());

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        List<String> commands = readCommandLog();
        Assert.assertTrue(commands.contains("wg-quick up " + config));
        Assert.assertTrue(commands.contains("iptables -A FORWARD -i eblocker-mobile -o eth0 -j ACCEPT"));
        Assert.assertTrue(commands.contains("iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o eth0 -j MASQUERADE"));
        Assert.assertTrue(commands.contains("iptables -t nat -A PREROUTING -i eblocker-mobile -p udp --dport 53 -j DNAT --to-destination 10.8.0.1:5300"));
        Assert.assertTrue(commands.contains("ip6tables -A FORWARD -i eblocker-mobile -o eth0 -j ACCEPT"));
        Assert.assertTrue(commands.contains("ip6tables -t nat -A POSTROUTING -s fd42:eb10:8::/64 -o eth0 -j MASQUERADE"));
        Assert.assertTrue(commands.contains("ip6tables -t nat -A PREROUTING -i eblocker-mobile -p udp --dport 53 -j DNAT --to-destination [fd42:eb10:8::1]:5300"));
    }

    @Test
    public void wireGuardMobileDownRemovesIpv4AndIpv6ForwardingAndDnsBeforeInterfaceDown() throws Exception {
        Path config = tempDir.resolve("eblocker-mobile.conf");
        Files.writeString(config, "[Interface]\nPrivateKey = private=\n", StandardCharsets.UTF_8);

        ProcessResult result = runScript("wireguard_mobile_down", config.toString());

        Assert.assertEquals(result.stderr, 0, result.exitCode);
        List<String> commands = readCommandLog();
        Assert.assertTrue(commands.contains("iptables -t nat -D POSTROUTING -s 10.8.0.0/24 -o eth0 -j MASQUERADE"));
        Assert.assertTrue(commands.contains("ip6tables -t nat -D POSTROUTING -s fd42:eb10:8::/64 -o eth0 -j MASQUERADE"));
        Assert.assertTrue(commands.contains("ip6tables -t nat -D PREROUTING -i eblocker-mobile -p udp --dport 53 -j DNAT --to-destination [fd42:eb10:8::1]:5300"));
        Assert.assertEquals("wg-quick down " + config, commands.get(commands.size() - 1));
    }

    private ProcessResult runScript(String scriptName, String... arguments) throws IOException, InterruptedException {
        Path script = Paths.get("src/main/package/scripts").resolve(scriptName);
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(buildCommand(script, arguments));
        Map<String, String> environment = builder.environment();
        environment.put("PATH", fakeBin + ":" + environment.get("PATH"));
        environment.put("COMMAND_LOG", commandLog.toString());
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, stdout, stderr);
    }

    private List<String> buildCommand(Path script, String... arguments) {
        List<String> command = new java.util.ArrayList<>();
        command.add("bash");
        command.add(script.toString());
        command.addAll(Arrays.asList(arguments));
        return command;
    }

    private List<String> readCommandLog() throws IOException {
        if (!Files.exists(commandLog)) {
            return List.of();
        }
        return Files.readAllLines(commandLog, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
    }

    private void writeFakeCommand(String name, String body) throws IOException {
        Path command = fakeBin.resolve(name);
        Files.writeString(command, "#!/bin/bash\n" + body, StandardCharsets.UTF_8);
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
        Files.setPosixFilePermissions(command, permissions);
    }

    private static class ProcessResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
