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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class DnsService {
    private final NetworkServices networkServices;
    private final NetworkStateMachine networkStateMachine;
    private final EblockerDnsServer dnsServer;
    private final DnsStatisticsService dnsStatisticsService;

    @Inject
    public DnsService(NetworkServices networkServices, NetworkStateMachine networkStateMachine,
                      EblockerDnsServer dnsServer, DnsStatisticsService dnsStatisticsService) {
        this.networkServices = networkServices;
        this.networkStateMachine = networkStateMachine;
        this.dnsServer = dnsServer;
        this.dnsStatisticsService = dnsStatisticsService;
    }

    public boolean setStatus(boolean status) {
        NetworkConfiguration configuration = networkServices.getCurrentNetworkConfiguration();
        configuration.setDnsServer(status);
        networkStateMachine.updateConfiguration(configuration);
        return dnsServer.isEnabled();
    }

    public DnsResolvers getDnsResolvers() {
        return dnsServer.getDnsResolvers();
    }

    public DnsResolvers setDnsResolvers(DnsResolvers dnsResolvers) {
        return dnsServer.setDnsResolvers(dnsResolvers);
    }

    public List<LocalDnsRecord> getLocalDnsRecords() {
        return dnsServer.getLocalDnsRecords().stream().filter(r -> !r.isHidden()).collect(Collectors.toList());
    }

    public List<LocalDnsRecord> setLocalDnsRecords(List<LocalDnsRecord> localDnsRecords) {
        return dnsServer.setLocalDnsRecords(localDnsRecords).stream().filter(r -> !r.isHidden()).collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return dnsServer.isEnabled();
    }

    public void flushCache() {
        dnsServer.flushCache();
    }

    public List<NameServerStats> testNameServers(List<String> nameServers) {
        return nameServers.stream()
                .map(nameServer -> dnsStatisticsService.testNameServer(nameServer,
                        Arrays.asList("eblocker.org", "eblocker.org", "eblocker.org", "eblocker.org", "eblocker.org")))
                .collect(Collectors.toList());
    }

    public Object getResolverStats(String resolver, int hours, String lengthValue) {
        if (lengthValue != null) {
            int length = Integer.parseInt(lengthValue);
            ZonedDateTime start = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
            int nextQuarters = start.getMinute() % length + 1;
            start = start.plusMinutes(nextQuarters * length).minusHours(hours);
            return dnsStatisticsService.getResolverStatistics(resolver, start.toInstant(), length, ChronoUnit.MINUTES);
        } else {
            return dnsStatisticsService.getResolverStatistics(resolver,
                    ZonedDateTime.now().minusHours(hours).toInstant());
        }
    }
}
