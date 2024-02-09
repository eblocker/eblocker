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

import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsRecordType;
import org.eblocker.server.common.data.dns.DnsResponse;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.eblocker.server.common.util.Ip4Utils;
import org.eblocker.server.common.util.Ip6Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class checks for possible gateway names and configures them as local names in the eblocker dns server
 */
@Singleton
public class DnsGatewayNames {
    private static final Logger log = LoggerFactory.getLogger(DnsGatewayNames.class);

    private final List<String> gatewayNames;
    private final DataSource dataSource;
    private final DnsResolver dnsResolver;
    private final EblockerDnsServer dnsServer;

    @Inject
    public DnsGatewayNames(@Named("dns.server.gateway.local.names") String gatewayNames,
                           DataSource dataSource,
                           DnsResolver dnsResolver,
                           EblockerDnsServer dnsServer) {
        this.gatewayNames = Splitter.on(',').trimResults().splitToList(gatewayNames);
        this.dataSource = dataSource;
        this.dnsResolver = dnsResolver;
        this.dnsServer = dnsServer;
    }

    public void check() {
        String gateway = dataSource.getGateway();
        String resolvedGateway = dataSource.getResolvedDnsGateway();
        if (Objects.equals(gateway, resolvedGateway)) {
            log.debug("gateway has not changed");
            return;
        }
        log.info("resolving {} possible names for gateway", gatewayNames.size());

        List<DnsResponse> responses = resolveGatewayNames(gateway);
        if (responses == null) {
            log.info("resolving names failed");
            return;
        }
        log.info("found {} gateway records", responses.size());

        addLocalDnsRecords(responses);
        dataSource.setResolvedDnsGateway(gateway);
    }

    private List<DnsResponse> resolveGatewayNames(String gateway) {
        List<DnsQuery> queries = gatewayNames.stream()
                .flatMap(name -> Stream.of(
                        new DnsQuery(DnsRecordType.A, name),
                        new DnsQuery(DnsRecordType.AAAA, name)))
                .collect(Collectors.toList());
        List<DnsResponse> results = dnsResolver.resolve(gateway, queries);
        if (!validateResults(results)) {
            return null; // NOSONAR: An empty list is a valid result iff there are no queries
        }
        return results;
    }

    private boolean validateResults(List<DnsResponse> resolvedNames) {
        if (resolvedNames == null) {
            log.info("resolving gateway names failed");
            return false;
        }

        if (resolvedNames.size() != gatewayNames.size() * 2) {
            log.error("expected {} resolved names but got {}", gatewayNames.size() * 2, resolvedNames.size());
            return false;
        }

        if (resolvedNames.stream().anyMatch(r -> r.getStatus() == null)) {
            log.info("unanswered dns questions");
            return false;
        }

        return true;
    }

    private void addLocalDnsRecords(List<DnsResponse> responses) {
        if (responses.isEmpty()) {
            return;
        }

        Map<String, LocalDnsRecord> localDnsRecordsByName = dnsServer.getLocalDnsRecords().stream()
                .collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        for (int i = 0; i < gatewayNames.size(); ++i) {
            updateLocalRecords(localDnsRecordsByName, gatewayNames.get(i), responses.subList(i * 2, (i + 1) * 2));
        }
        dnsServer.setLocalDnsRecords(new ArrayList<>(localDnsRecordsByName.values()));
    }

    private void updateLocalRecords(Map<String, LocalDnsRecord> localDnsRecordsByName, String name, List<DnsResponse> responses) {
        Ip4Address ip4 = null;
        Ip6Address ip6 = null;
        for (DnsResponse response : responses) {
            if (response.getStatus() == 0 && response.getIpAddress() != null) {
                if (response.getRecordType() == DnsRecordType.A) {
                    Ip4Address address = (Ip4Address) response.getIpAddress();
                    if (Ip4Utils.isPrivate(address) || Ip4Utils.isLinkLocal(address)) {
                        ip4 = address;
                    }
                } else if (response.getRecordType() == DnsRecordType.AAAA) {
                    Ip6Address address = (Ip6Address) response.getIpAddress();
                    if (Ip6Utils.isLinkLocal(address) || Ip6Utils.isUniqueLocal(address)) {
                        ip6 = address;
                    }
                }
            }
        }
        if (ip4 == null && ip6 == null) {
            localDnsRecordsByName.remove(name);
        } else {
            localDnsRecordsByName.put(name, new LocalDnsRecord(name, false, false, ip4, ip6, ip4, ip6));
        }
    }
}
