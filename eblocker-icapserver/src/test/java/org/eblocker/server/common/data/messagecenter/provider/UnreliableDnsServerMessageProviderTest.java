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
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.data.dns.ResolverStats;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.http.service.DnsStatisticsService;
import org.eblocker.server.http.service.TestClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UnreliableDnsServerMessageProviderTest {

    private final long updateInterval = 300;
    private TestClock clock;
    private EblockerDnsServer dnsServer;
    private DnsStatisticsService statisticsService;
    private UnreliableDnsServerMessageProvider provider;

    @Before
    public void setUp() {
        clock = new TestClock(ZonedDateTime.now());

        dnsServer = Mockito.mock(EblockerDnsServer.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsServer.getDnsResolvers().getDefaultResolver()).thenReturn("custom");
        Mockito.when(dnsServer.getDnsResolvers().getCustomNameServers()).thenReturn(Arrays.asList("8.8.8.8", "8.8.4.4"));

        statisticsService = Mockito.mock(DnsStatisticsService.class);
        provider = new UnreliableDnsServerMessageProvider(updateInterval, clock, statisticsService, dnsServer);
    }

    @Test
    public void testGetMessageIds() {
        Assert.assertEquals(Collections.singleton(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId()), provider.getMessageIds());
    }

    @Test
    public void testUnreliableDnsServerNoMessageCreation() {
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.MEDIUM));

        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(0, container.size());
        Mockito.verify(statisticsService).getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class));
    }

    @Test
    public void testUnreliableDnsServerDnsDisabled() {
        Mockito.when(dnsServer.isEnabled()).thenReturn(false);
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.BAD));

        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(0, container.size());
        Mockito.verify(statisticsService, Mockito.times(0)).getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class));
    }

    @Test
    public void testUnreliableDnsServerMessageCreation() {
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.BAD));

        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        MessageContainer messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage message = messageContainer.getMessage();
        Assert.assertNotNull(message);
        Assert.assertEquals(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId(), message.getId());
        Assert.assertNotNull(message.getContext());
        Assert.assertEquals("8.8.8.8", message.getContext().get("nameServers"));
    }

    @Test
    public void testUnreliableDnsServerMessageUpdateUnchanged() {
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.BAD));

        // first update should insert a new message
        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        MessageContainer messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage initialMessage = messageContainer.getMessage();
        Assert.assertNotNull(initialMessage);

        // run update again, message must not be modified or recreated
        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage message = messageContainer.getMessage();
        Assert.assertNotNull(message);
        Assert.assertTrue(message == initialMessage);
    }

    @Test
    public void testUnreliableDnsServerMessageUpdateChanged() {
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.BAD));

        // first update should insert a new message
        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        MessageContainer messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage initialMessage = messageContainer.getMessage();
        Assert.assertNotNull(initialMessage);

        // run update again, message must be recreated
        clock.setZonedDateTime(ZonedDateTime.now().plusSeconds(updateInterval));
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.4.4", DnsRating.BAD));

        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage message = messageContainer.getMessage();
        Assert.assertNotNull(message);
        Assert.assertTrue(message != initialMessage);
        Assert.assertNotNull(message.getContext());
        Assert.assertEquals("8.8.4.4", message.getContext().get("nameServers"));
    }

    @Test
    public void testUnreliableDnsServerMessageRemoval() {
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(createResolverStats("8.8.8.8", DnsRating.BAD));

        // first update should insert a new message
        Map<Integer, MessageContainer> container = new HashMap<>();
        provider.doUpdate(container);

        Assert.assertEquals(1, container.size());

        MessageContainer messageContainer = container.get(MessageProviderMessageId.MESSAGE_DNS_UNRELIABLE_NAME_SERVER.getId());
        Assert.assertNotNull(messageContainer);

        MessageCenterMessage initialMessage = messageContainer.getMessage();
        Assert.assertNotNull(initialMessage);

        // run update again, message must be removed
        clock.setZonedDateTime(ZonedDateTime.now().plusSeconds(updateInterval));
        Mockito.when(statisticsService.getResolverStatistics(Mockito.eq("custom"), Mockito.any(Instant.class)))
                .thenReturn(new ResolverStats(null, null, Collections.emptyList()));

        provider.doUpdate(container);

        Assert.assertEquals(0, container.size());
    }

    private ResolverStats createResolverStats(String nameServer, DnsRating rating) {
        NameServerStats stats = new NameServerStats(nameServer, -1, -1, -1, -1, -1, -1, -1, -1, rating, null, null);
        return new ResolverStats(null, null, Collections.singletonList(stats));
    }
}
