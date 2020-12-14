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

import org.eblocker.server.common.network.icmpv6.PrefixOption;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.http.service.TestClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RouterAdvertisementCacheTest {

    private TestClock clock;
    private ScheduledExecutorService executorService;
    private RouterAdvertisementCache cache;

    @Before
    public void setUp() {
        clock = new TestClock(LocalDateTime.of(2019, 3, 12, 16, 30, 0));
        executorService = Mockito.mock(ScheduledExecutorService.class);
        cache = new RouterAdvertisementCache(clock, executorService);
    }

    @Test
    public void testCachingNoOptions() {
        RouterAdvertisement advertisement = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 0, 0, Collections.emptyList());
        cache.addEntry(advertisement);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());
        Assert.assertEquals(clock.millis(), cache.getEntries().get(0).getLastUpdate());
        Assert.assertEquals(1000, cache.getEntries().get(0).getLifetime());
        Assert.assertSame(advertisement, cache.getEntries().get(0).getAdvertisement());

        Mockito.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq(1000L), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testCachingPrefixOptionWithLifetime() {
        RouterAdvertisement advertisement = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10,
            Collections.singletonList(new PrefixOption((short) 0, false, false, 2000, 2000, null)));
        cache.addEntry(advertisement);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());
        Assert.assertEquals(clock.millis(), cache.getEntries().get(0).getLastUpdate());
        Assert.assertEquals(2000, cache.getEntries().get(0).getLifetime());
        Assert.assertSame(advertisement, cache.getEntries().get(0).getAdvertisement());

        Mockito.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq(2000L), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testCachingRdnsOptionWithLifetime() {
        RouterAdvertisement advertisement = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10,
            Collections.singletonList(new RecursiveDnsServerOption(2000, Collections.emptyList())));
        cache.addEntry(advertisement);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());
        Assert.assertEquals(clock.millis(), cache.getEntries().get(0).getLastUpdate());
        Assert.assertEquals(2000, cache.getEntries().get(0).getLifetime());
        Assert.assertSame(advertisement, cache.getEntries().get(0).getAdvertisement());

        Mockito.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq(2000L), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testCachingSourceLinkLayerOption() {
        RouterAdvertisement advertisementLinkLayerOption = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10,
            Collections.singletonList(new SourceLinkLayerAddressOption(new byte[]{ 0x1 })));
        cache.addEntry(advertisementLinkLayerOption);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());
        Assert.assertEquals(clock.millis(), cache.getEntries().get(0).getLastUpdate());
        Assert.assertEquals(1000, cache.getEntries().get(0).getLifetime());
        Assert.assertSame(advertisementLinkLayerOption, cache.getEntries().get(0).getAdvertisement());

        clock = new TestClock(LocalDateTime.of(2019, 3, 12, 16, 30, 0));
        RouterAdvertisement advertisementNoLinkLayerOption = new RouterAdvertisement(new byte[]{ 0x1 }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10, Collections.emptyList());
        cache.addEntry(advertisementNoLinkLayerOption);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());
        Assert.assertEquals(clock.millis(), cache.getEntries().get(0).getLastUpdate());
        Assert.assertEquals(1000, cache.getEntries().get(0).getLifetime());
        Assert.assertSame(advertisementNoLinkLayerOption, cache.getEntries().get(0).getAdvertisement());
    }

    @Test
    public void testExpiration() {
        RouterAdvertisement advertisement = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10, Collections.emptyList());
        cache.addEntry(advertisement);

        Assert.assertNotNull(cache.getEntries());
        Assert.assertEquals(1, cache.getEntries().size());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).schedule(runnableCaptor.capture(), Mockito.eq(1000L), Mockito.eq(TimeUnit.SECONDS));
        runnableCaptor.getValue().run();
        Assert.assertEquals(0, cache.getEntries().size());
    }

    @Test
    public void testListener() {
        RouterAdvertisementCache.Listener listener = Mockito.mock(RouterAdvertisementCache.Listener.class);
        cache.addListener(listener);

        // insert entry
        RouterAdvertisement advertisement = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10, Collections.emptyList());
        cache.addEntry(advertisement);
        ArgumentCaptor<List<RouterAdvertisementCache.Entry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(listener).onUpdate(entriesCaptor.capture());
        Assert.assertEquals(1, entriesCaptor.getValue().size());
        Assert.assertSame(advertisement, entriesCaptor.getValue().get(0).getAdvertisement());

        // update entry
        RouterAdvertisement advertisement2 = new RouterAdvertisement(new byte[]{ 0x7f }, null, new byte[0], null, (short) 0, false, false, false, RouterAdvertisement.RouterPreference.MEDIUM, 1000, 1000, 10, Collections.emptyList());
        cache.addEntry(advertisement2);
        Mockito.verify(listener, Mockito.times(2)).onUpdate(entriesCaptor.capture());
        Assert.assertEquals(1, entriesCaptor.getValue().size());
        Assert.assertSame(advertisement2, entriesCaptor.getValue().get(0).getAdvertisement());

        // expire entry
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService, Mockito.times(2)).schedule(runnableCaptor.capture(), Mockito.eq(1000L), Mockito.eq(TimeUnit.SECONDS));
        runnableCaptor.getValue().run();
        Mockito.verify(listener, Mockito.times(3)).onUpdate(entriesCaptor.capture());
        Assert.assertEquals(0, entriesCaptor.getValue().size());
    }
}
