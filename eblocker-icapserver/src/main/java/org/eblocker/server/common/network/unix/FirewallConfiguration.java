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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.unix.firewall.Chain;
import org.eblocker.server.common.network.unix.firewall.IpAddressFilter;
import org.eblocker.server.common.network.unix.firewall.Table;
import org.eblocker.server.common.network.unix.firewall.TableGenerator;
import org.eblocker.server.common.util.Levenshtein;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writes configuration files for "iptables-restore".
 */
public class FirewallConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FirewallConfiguration.class);

    private final TableGenerator tableGenerator;
    private final NetworkServices networkServices;
    private final ParentalControlAccessRestrictionsService restrictionsService;
    private final Environment environment;

    private final Path configFullPath;
    private final Path configDeltaPath;

    private final Levenshtein<String> levenshtein;

    private List<Table> activeTables;

    @Inject
    public FirewallConfiguration(@Named("network.unix.firewall.config.full.path") String configFullPath,
                                 @Named("network.unix.firewall.config.delta.path") String configDeltaPath,
                                 TableGenerator tableGenerator,
                                 NetworkServices networkServices,
                                 ParentalControlAccessRestrictionsService restrictionsService,
                                 Environment environment) {

        this.configFullPath = Paths.get(configFullPath);
        this.configDeltaPath = Paths.get(configDeltaPath);
        this.tableGenerator = tableGenerator;
        this.networkServices = networkServices;
        this.restrictionsService = restrictionsService;
        this.environment = environment;
        this.levenshtein = new Levenshtein.Builder<String>().substitutionCost(c -> Integer.MAX_VALUE).build();
    }


    public synchronized void enable(Set<Device> allDevices, Collection<OpenVpnClientState> anonVpnClients,
                                    boolean masquerade, boolean enableSSL, boolean enableEblockerDns,
                                    boolean enableOpenVpnServer, boolean enableMalwareSet,
                                    Supplier<Boolean> applyFirewallRules) throws IOException {

        NetworkConfiguration netConfig = networkServices.getCurrentNetworkConfiguration();

        // Set IP/network addresses
        tableGenerator.setOwnIpAddress(netConfig.getIpAddress());
        tableGenerator.setNetworkMask(netConfig.getNetworkMask());
        tableGenerator.setGatewayIpAddress(netConfig.getGateway());
        tableGenerator.setMobileVpnIpAddress(netConfig.getVpnIpAddress());

        // Set flags
        tableGenerator.setMasqueradeEnabled(masquerade);
        tableGenerator.setSslEnabled(enableSSL);
        tableGenerator.setDnsEnabled(enableEblockerDns);
        tableGenerator.setMobileVpnServerEnabled(enableOpenVpnServer);
        tableGenerator.setMalwareSetEnabled(enableMalwareSet);
        tableGenerator.setServerEnvironment(environment.isServer());

        // ensure stable order to prevent deltas due to rule order changes
        Set<Device> devicesByMac = new TreeSet<>(Comparator.comparing(Device::getHardwareAddress));
        devicesByMac.addAll(allDevices);
        Set<OpenVpnClientState> anonVpnClientsById = new TreeSet<>(Comparator.comparing(OpenVpnClientState::getId));
        anonVpnClientsById.addAll(anonVpnClients);

        IpAddressFilter ipAddressFilter = new IpAddressFilter(devicesByMac, IpAddress::isIpv4, restrictionsService);

        List<Table> newTables = List.of(
                tableGenerator.generateNatTable(ipAddressFilter, anonVpnClientsById),
                tableGenerator.generateFilterTable(ipAddressFilter, anonVpnClientsById),
                tableGenerator.generateMangleTable(ipAddressFilter, anonVpnClientsById));

        // write delta config
        String deltaConfig = null;
        if (activeTables != null) {
            deltaConfig = createTablesDiff(activeTables, newTables);
            Files.write(configDeltaPath, deltaConfig.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } else {
            Files.deleteIfExists(configDeltaPath);
        }

        // write full config
        String fullConfig = createTablesDiff(Collections.emptyList(), newTables);
        Files.write(configFullPath, fullConfig.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        if (applyFirewallRules.get()) {
            activeTables = newTables;
        } else {
            LOG.error("applying firewall rules failed");
            LOG.error("delta rules:\n{}", deltaConfig);
            LOG.error("full rules:\n{}", fullConfig);
        }
    }

    /**
     * Writes all the table information to the file (final step)
     */
    private String createTablesDiff(List<Table> currentTables, List<Table> newTables) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        for (Table newTable : newTables) {
            String tableName = newTable.getName();
            writer.format("*%s\n", tableName);
            Table currentTable = currentTables.stream()
                    .filter(t -> tableName.equals(t.getName()))
                    .findAny()
                    .orElse(new Table(tableName));
            writeTableDiff(writer, currentTable, newTable);
            writer.println("COMMIT");
        }
        writer.flush();
        return stringWriter.toString();
    }

    private void writeTableDiff(PrintWriter writer, Table currentTable, Table newTable) {
        Supplier<TreeSet<Chain>> factory = () -> new TreeSet<>(Comparator.comparing(Chain::getName));
        TreeSet<Chain> currentChains = currentTable.getChains().stream().collect(Collectors.toCollection(factory));
        TreeSet<Chain> newChains = newTable.getChains().stream().collect(Collectors.toCollection(factory));

        Sets.SetView<Chain> addedChains = Sets.difference(newChains, currentChains);
        addedChains.forEach(chain -> writer.format(":%s %s\n", chain.getName(), chain.getPolicy()));

        Sets.SetView<Chain> removedChains = Sets.difference(currentChains, newChains);
        removedChains.forEach(chain -> writer.format("-D %s\n", chain.getName()));

        Map<String, Chain> currentChainsByName = currentTable.getChains().stream().collect(Collectors.toMap(Chain::getName, Function.identity()));
        newChains.forEach(chain -> writeChainDiff(writer, currentChainsByName.get(chain.getName()), chain));
    }

    private void writeChainDiff(PrintWriter writer, Chain currentChain, Chain newChain) {
        if (currentChain == null) {
            newChain.getRulesAsStrings().forEach(rule -> writer.format("-A %s %s\n", newChain.getName(), rule));
            return;
        }

        Levenshtein.Distance distance = levenshtein.distance(currentChain.getRulesAsStrings(), newChain.getRulesAsStrings());
        int i = 1;
        for (Levenshtein.DistanceMatrixEntry e : distance.getEditSequence()) {
            switch (e.getOperation()) {
                case NO_OPERATION:
                    ++i;
                    break;
                case INSERT:
                    writer.format("-I %s %d %s\n", newChain.getName(), i, newChain.getRulesAsStrings().get(e.getY() - 1));
                    ++i;
                    break;
                case DELETE:
                    writer.format("-D %s %d\n", newChain.getName(), i);
                    break;
                case SUBSTITUTE:
                    writer.format("-R %s %d %s\n", newChain.getName(), i, newChain.getRulesAsStrings().get(e.getY() - 1));
                    ++i;
                    break;
            }
        }
    }
}
