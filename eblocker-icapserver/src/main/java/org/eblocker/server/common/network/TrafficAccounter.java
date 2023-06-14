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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TrafficAccount;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = false)
public class TrafficAccounter {
    private static final Logger logger = LoggerFactory.getLogger(TrafficAccounter.class);

    private static final Pattern ACCOUNT_IN_PATTERN = Pattern.compile("\\[(\\d+):(\\d+)\\] -A ACCOUNT-IN -s (\\d+\\.\\d+\\.\\d+\\.\\d+)/32 -i eth0 -j RETURN");
    private static final Pattern ACCOUNT_OUT_PATTERN = Pattern.compile("\\[(\\d+):(\\d+)\\] -A ACCOUNT-OUT -d (\\d+\\.\\d+\\.\\d+\\.\\d+)/32 -o eth0 -j RETURN");

    private final String dumpIpTablesRulesScript;
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final ScriptRunner scriptRunner;

    private File ipTablesDumpFile;
    private Map<String, TrafficAccount> totalsByMac = new HashMap<>();
    private Map<String, TrafficAccount> lastUpdateByMac = new HashMap<>();

    @Inject
    public TrafficAccounter(@Named("network.statistic.dumpIptablesCommand") String dumpIpTablesRulesScript,
                            DataSource dataSource, DeviceService deviceService, ScriptRunner scriptRunner) throws IOException {
        this.dumpIpTablesRulesScript = dumpIpTablesRulesScript;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.scriptRunner = scriptRunner;
    }

    @SubSystemInit
    public void init() {
        initIpTablesDumpFile();
        initTotalsCounters();
    }

    public TrafficAccount getTrafficAccount(Device device) {
        TrafficAccount account = totalsByMac.get(device.getHardwareAddress(false));
        if (account != null) {
            return new TrafficAccount(account);
        }
        return null;
    }

    private void initIpTablesDumpFile() {
        try {
            ipTablesDumpFile = Files.createTempFile(Paths.get("/tmp"), "iptables-rules", null).toFile();
            ipTablesDumpFile.deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to init dump file", e);
        }
    }

    private void initTotalsCounters() {
        totalsByMac = dataSource.getAll(TrafficAccount.class).stream().collect(Collectors.toMap(TrafficAccount::getMac, Function.identity()));
    }

    public synchronized void update() {
        try {
            List<String> dump = getIpTableRulesDump();
            List<TrafficAccount> counters = extractCounters(dump);
            updateTotals(counters);
        } catch (IpTablesException e) {
            logger.error("failed to update network activity", e);
        }
    }

    private List<String> getIpTableRulesDump() throws IpTablesException {
        try {
            int exitCode = scriptRunner.runScript(dumpIpTablesRulesScript, ipTablesDumpFile.getAbsolutePath());
            if (exitCode != 0) {
                throw new IpTablesException("ip tables rules dump script failed with exit code " + exitCode);
            }
            return Files.readAllLines(ipTablesDumpFile.toPath());
        } catch (IOException e) {
            throw new IpTablesException("failed to get ip tables rules dump", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    private List<TrafficAccount> extractCounters(List<String> dump) {
        return dump.stream()
                .map(this::extractCounter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private TrafficAccount extractCounter(String line) {
        TrafficAccount counter = extractInCounter(line);
        if (counter != null) {
            return counter;
        }

        return extractOutCounter(line);
    }

    private TrafficAccount extractInCounter(String line) {
        Counter counter = extractCounter(ACCOUNT_IN_PATTERN, line);
        if (counter != null) {
            TrafficAccount account = new TrafficAccount();
            account.setMac(counter.device.getHardwareAddress(false));
            account.setPacketsIn(counter.packets);
            account.setBytesIn(counter.bytes);
            return account;
        }

        return null;
    }

    private TrafficAccount extractOutCounter(String line) {
        Counter counter = extractCounter(ACCOUNT_OUT_PATTERN, line);
        if (counter != null) {
            TrafficAccount account = new TrafficAccount();
            account.setMac(counter.device.getHardwareAddress(false));
            account.setPacketsOut(counter.packets);
            account.setBytesOut(counter.bytes);
            return account;
        }

        return null;
    }

    private Counter extractCounter(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            IpAddress ip = IpAddress.parse(matcher.group(3));
            Device device = deviceService.getDeviceByIp(ip);
            if (device == null) {
                logger.warn("no device known for ip {}", ip);
                return null;
            }
            return new Counter(device, matcher.group(1), matcher.group(2));
        }
        return null;
    }

    private void updateTotals(List<TrafficAccount> counters) {
        // update totals
        Map<String, TrafficAccount> countersByMac = counters.stream()
                .collect(Collectors.groupingBy(TrafficAccount::getMac, counterCollector()));
        countersByMac.values().forEach(this::updateCounter);

        // remove any unreferenced lastUpdate counter
        Iterator i = lastUpdateByMac.keySet().iterator();
        while (i.hasNext()) {
            if (!countersByMac.containsKey(i.next())) {
                i.remove();
            }
        }
    }

    private void updateCounter(TrafficAccount counter) {
        TrafficAccount lastUpdate = lastUpdateByMac.get(counter.getMac());
        lastUpdateByMac.put(counter.getMac(), counter);

        if (lastUpdate == null) {
            lastUpdate = new TrafficAccount();
        }

        TrafficAccount delta = TrafficAccount.sub(counter, lastUpdate);

        if (delta.getBytesIn() != 0 || delta.getBytesOut() != 0) {
            TrafficAccount total = totalsByMac.get(counter.getMac());
            if (total == null) {
                total = new TrafficAccount();
                total.setMac(counter.getMac());
                // TODO: if datasource would accept long as id mac address could be used directly as key
                total.setId(dataSource.nextId(TrafficAccount.class));
                totalsByMac.put(total.getMac(), total);
            }

            total.add(delta);
            total.setLastActivity(new Date());
            dataSource.save(total, total.getId());
        }
    }

    private class IpTablesException extends Exception {
        IpTablesException(String message) {
            super(message);
        }

        IpTablesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static Collector<TrafficAccount, TrafficAccount, TrafficAccount> counterCollector() {
        return new Collector<TrafficAccount, TrafficAccount, TrafficAccount>() {
            @Override
            public Supplier<TrafficAccount> supplier() {
                return TrafficAccount::new;
            }

            @Override
            public BiConsumer<TrafficAccount, TrafficAccount> accumulator() {
                return (a, b) -> {
                    a.setMac(b.getMac());
                    a.add(b);
                };
            }

            @Override
            public BinaryOperator<TrafficAccount> combiner() {
                return (a, b) -> TrafficAccount.add(a, b);
            }

            @Override
            public Function<TrafficAccount, TrafficAccount> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.CONCURRENT,
                        Collector.Characteristics.UNORDERED,
                        Collector.Characteristics.IDENTITY_FINISH));
            }
        };
    }

    private class Counter {
        Device device;
        long bytes;
        long packets;

        Counter(Device device, String packets, String bytes) {
            this.device = device;
            this.packets = Long.parseLong(packets);
            this.bytes = Long.parseLong(bytes);
        }
    }

}
