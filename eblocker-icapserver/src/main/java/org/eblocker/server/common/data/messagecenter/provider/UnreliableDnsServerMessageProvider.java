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
package org.eblocker.server.common.data.messagecenter.provider;

import org.eblocker.server.common.data.dns.DnsRating;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.data.dns.ResolverStats;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.http.service.DnsStatisticsService;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class UnreliableDnsServerMessageProvider extends AbstractMessageProvider {

    private static final Logger log = LoggerFactory.getLogger(UnreliableDnsServerMessageProvider.class);

    private final long interval;
    private final Clock clock;
    private final DnsStatisticsService dnsStatisticsService;
    private final EblockerDnsServer dnsServer;
    private Instant lastUpdate;

    @Inject
    public UnreliableDnsServerMessageProvider(@Named("dns.warning.interval") long interval,
                                              Clock clock,
                                              DnsStatisticsService dnsStatisticsService,
                                              EblockerDnsServer dnsServer) {
        this.interval = interval;
        this.clock = clock;
        this.dnsStatisticsService = dnsStatisticsService;
        this.dnsServer = dnsServer;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        if (dnsServer.isEnabled() && (lastUpdate == null || Duration.between(lastUpdate, Instant.now(clock)).getSeconds() >= interval)) {
            lastUpdate = Instant.now(clock);
            List<String> unreliableNameServers = findUnreliableNameServers();
            if (unreliableNameServers.isEmpty()) {
                messageContainers.remove(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
            } else {
                MessageContainer container = messageContainers.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER);
                if (container == null || !container.getMessage().getContext().get("nameServers").equals(nameServerString(unreliableNameServers))) {
                    container = createMessage(unreliableNameServers);
                    messageContainers.put(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId(), container);
                }
            }
        }
    }

    private List<String> findUnreliableNameServers() {
        DnsResolvers resolvers = dnsServer.getDnsResolvers();
        String resolver = resolvers.getDefaultResolver();
        List<String> nameServers;
        switch(resolver) {
            case "tor":
                nameServers = Collections.singletonList("127.0.0.1");
                break;
            case "dhcp":
                nameServers = resolvers.getDhcpNameServers();
                break;
            case "custom":
                nameServers = resolvers.getCustomNameServers();
                break;
            default:
                log.error("unknown resolver: {}", resolver);
                return Collections.emptyList();
        }

        ResolverStats stats = dnsStatisticsService.getResolverStatistics(resolver, Instant.now().minus(1, ChronoUnit.HOURS));
        return stats.getNameServerStats().stream()
            .filter(s -> DnsRating.BAD == s.getRating())
            .map(NameServerStats::getNameServer)
            .filter(nameServers::contains)
            .collect(Collectors.toList());
    }

    private MessageContainer createMessage(List<String> nameServers) {
        return createMessage(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId(),
            "MESSAGE_DNS_UNRELIABLE_NAME_SERVERS_TITLE",
            "MESSAGE_DNS_UNRELIABLE_NAME_SERVERS_CONTENT",
            "MESSAGE_DNS_UNRELIABLE_NAME_SERVERS_LABEL",
            "MESSAGE_DNS_UNRELIABLE_NAME_SERVERS_URL",
            Collections.singletonMap("nameServers", nameServerString(nameServers)),
            false,
            MessageSeverity.INFO);
    }

    private String nameServerString(List<String> nameServers) {
        return Joiner.on(", ").join(nameServers.stream().sorted().collect(Collectors.toList()));
    }
}
