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

import org.eblocker.server.common.util.ByteArrays;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.PrefixOption;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class RouterAdvertisementCache {

    private final Clock clock;
    private final ScheduledExecutorService executorService;
    private final ConcurrentMap<ByteArrays.Key, Entry> cache = new ConcurrentHashMap<>(8, 0.75f, 1);
    private final List<Listener> listeners = new ArrayList<>();

    @Inject
    public RouterAdvertisementCache(Clock clock, @Named("highPrioScheduledExecutor") ScheduledExecutorService executorService) {
        this.clock = clock;
        this.executorService = executorService;
    }

    public void addEntry(RouterAdvertisement advertisement) {
        byte[] sourceAddress = getSourceAddress(advertisement);
        long updated = clock.millis();
        long lifetime = getMaximalLifetime(advertisement);
        if (lifetime > 0) {
            ByteArrays.Key key = new ByteArrays.Key(sourceAddress);
            Entry entry = new Entry(key, updated, lifetime, advertisement);
            cache.put(key, entry);
            executorService.schedule(() -> expire(entry), lifetime, TimeUnit.SECONDS);
            notifyListeners();
        }
    }

    public List<Entry> getEntries() {
        return new ArrayList<>(cache.values());
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private void expire(Entry entry) {
        if (cache.remove(entry.getKey(), entry)) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        List<Entry> entries = getEntries();
        synchronized (listeners) {
            listeners.forEach(l -> l.onUpdate(entries));
        }
    }

    private byte[] getSourceAddress(RouterAdvertisement advertisement) {
        return advertisement.getOptions().stream()
            .filter(o -> SourceLinkLayerAddressOption.TYPE == o.getType())
            .map(o -> ((SourceLinkLayerAddressOption)o).getHardwareAddress())
            .findFirst()
            .orElse(advertisement.getSourceHardwareAddress());
    }

    // checks advertisement and all options and returns the longest lifetime found
    private long getMaximalLifetime(RouterAdvertisement advertisement) {
        long lifetime = advertisement.getRouterLifetime();
        for(Option option : advertisement.getOptions()) {
            switch (option.getType()) {
                case RecursiveDnsServerOption.TYPE:
                    lifetime = Math.max(lifetime, ((RecursiveDnsServerOption) option).getLifetime());
                    break;
                case PrefixOption.TYPE:
                    lifetime = Math.max(lifetime, ((PrefixOption) option).getValidLifetime());
                    break;
                default:
                    break;
            }
        }
        return lifetime;
    }

    public static class Entry {
        private final ByteArrays.Key key;
        private final long lastUpdate;
        private final long lifetime;
        private final RouterAdvertisement advertisement;

        public Entry(ByteArrays.Key key, long lastUpdate, long lifetime,
                     RouterAdvertisement advertisement) {
            this.key = key;
            this.lastUpdate = lastUpdate;
            this.lifetime = lifetime;
            this.advertisement = advertisement;
        }

        private ByteArrays.Key getKey() {
            return key;
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public long getLifetime() {
            return lifetime;
        }

        public RouterAdvertisement getAdvertisement() {
            return advertisement;
        }
    }

    public interface Listener {
        void onUpdate(List<Entry> entries);
    }

}
