/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import com.google.inject.Singleton;
import org.eblocker.server.common.data.IpAddress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * This table stores the last time a hardware address has responded to a specific IP address
 * (via ARP response or Neighbor Advertisement).
 *
 * Listeners can be notified if the latest timestamp for a specific hardware address has been
 * updated. They are not notified more frequently than once per minute.
 */
@Singleton
public class IpResponseTable {
    private static final long MAX_NOTIFICATION_FREQ_MILLIS = 60*1000;
    private final Map<String, IpTimestamps> table;
    private final List<LatestTimestampUpdateListener> latestTimestampUpdateListeners = new ArrayList<>();

    public IpResponseTable() {
        table = new HashMap<>();
    }

    public Long get(String hardwareAddress, IpAddress ipAddress) {
        IpTimestamps ipTimestamps = table.get(hardwareAddress);
        if (ipTimestamps == null) {
            return null;
        }
        return ipTimestamps.get(ipAddress);
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public synchronized void put(String hardwareAddress, IpAddress sourceAddress, long millis) {
        IpTimestamps ipTimestamps = table.computeIfAbsent(hardwareAddress, k -> new IpTimestamps());
        ipTimestamps.put(sourceAddress, millis);

        // notify listeners?
        if (ipTimestamps.latestNotification + MAX_NOTIFICATION_FREQ_MILLIS <= millis) {
            notifyListeners(hardwareAddress, millis);
            ipTimestamps.latestNotification = millis;
        }
    }

    public boolean activeSince(String hardwareAddress, long millis) {
        Long latest = latestTimestamp(hardwareAddress);
        if (latest == null) {
            return false;
        }
        return latest >= millis;
    }

    public Long latestTimestamp(String hardwareAddress) {
        IpTimestamps ipTimestamps = table.get(hardwareAddress);
        if (ipTimestamps == null) {
            return null;
        }
        return ipTimestamps.latestTimestamp;
    }

    public void forEachActiveSince(long millis, BiConsumer<String, Set<IpAddress>> consumer) {
        table.forEach((hardwareAddress, ipTimestamps) -> {
            Set<IpAddress> activeAddresses = ipTimestamps.activeAddressesSince(millis);
            if (!activeAddresses.isEmpty()) {
                consumer.accept(hardwareAddress, activeAddresses);
            }
        });
    }

    public void removeAll(String hardwareAddress, Collection<IpAddress> ipAddresses) {
        IpTimestamps ipTimestamps = table.get(hardwareAddress);
        if (ipTimestamps == null) {
            return;
        }
        ipAddresses.forEach(ipAddress -> ipTimestamps.remove(ipAddress));
    }

    public boolean contains(String hardwareAddress, IpAddress ipAddress) {
        IpTimestamps ipTimestamps = table.get(hardwareAddress);
        if (ipTimestamps == null) {
            return false;
        }
        return ipTimestamps.contains(ipAddress);
    }

    public void remove(String hardwareAddress) {
        table.remove(hardwareAddress);
    }

    @Override
    public String toString() {
        return "IpResponseTable: {\n" + table.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "  " + e.getKey() + ": " + e.getValue().toString())
                .collect(Collectors.joining(",\n")) + "\n}";
    }

    /**
     * Store timestamps of IP addresses while keeping track of the most recent timestamp
     */
    private static class IpTimestamps {
        private volatile long latestTimestamp = Long.MIN_VALUE;
        private long latestNotification = Long.MIN_VALUE;
        private final Map<IpAddress, Long> timestamps = new HashMap<>();

        private Long get(IpAddress ipAddress) {
            return timestamps.get(ipAddress);
        }
        private void put(IpAddress ipAddress, long millis) {
            if (millis > latestTimestamp) {
                latestTimestamp = millis;
            }
            timestamps.put(ipAddress, millis);
        }
        private boolean contains(IpAddress ipAddress) {
            return timestamps.containsKey(ipAddress);
        }
        private Long remove(IpAddress ipAddress) {
            return timestamps.remove(ipAddress);
        }

        private Set<IpAddress> activeAddressesSince(long millis) {
            return timestamps.entrySet().stream()
                    .filter(e -> e.getValue() > millis)
                    .map(e -> e.getKey())
                    .collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            return "latest " + latestTimestamp + ", {" + timestamps.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().toString()))
                    .map(e -> e.getKey() + " => " + e.getValue())
                    .collect(Collectors.joining(", ")) + "}";
        }
    }

    private void notifyListeners(String hardwareAddress, long millis) {
        latestTimestampUpdateListeners.forEach(listener -> listener.onLatestTimestampUpdate(hardwareAddress, millis));
    }

    public void addLatestTimestampUpdateListener(LatestTimestampUpdateListener listener) {
        latestTimestampUpdateListeners.add(listener);
    }

    public interface LatestTimestampUpdateListener {
        void onLatestTimestampUpdate(String hardwareAddress, long millis);
    }
}
