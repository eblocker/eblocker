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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves in RAM when a device was last seen online.
 */
@Singleton
public class DeviceOnlineStatusCache {
    private Clock clock;
    private Map<String, Instant> cache;
    private int deviceOfflineAfterSeconds;

    @Inject
    public DeviceOnlineStatusCache(Clock clock, @Named("device.offline.after.seconds") int deviceOfflineAfterSeconds) {
        this.clock = clock;
        this.cache = new ConcurrentHashMap<String, Instant>();
        this.deviceOfflineAfterSeconds = deviceOfflineAfterSeconds;
    }

    /**
     * Stores the current time in the cache for the current device.
     * Call this method when a device is seen as online.
     *
     * @param deviceId
     */
    public void updateOnlineStatus(String deviceId) {
        cache.put(deviceId, clock.instant());
    }

    /**
     * Sets the device's online flag depending on the current time and the time
     * the device was seen last. A device is considered to be offline if it is not
     * in the cache.
     *
     * @param device
     */
    /**
     * Sets the online status on the given device object based on the last seen
     * timestamp from the cache.
     *
     * @param device the device to set the online status on
     */
    public void setOnlineStatus(Device device) {
        // VPN clients are always online
        if (device.isVpnClient()) {
            device.setOnline(true);
        } else {
            // Get the last seen timestamp from the cache
            Instant lastSeen = cache.get(device.getId());
            if (lastSeen != null) {
                Instant offlineSince = lastSeen.plusSeconds(deviceOfflineAfterSeconds);
                device.setOnline(offlineSince.isAfter(clock.instant()));
                device.setLastSeen(lastSeen);
                device.getOfflineSinceString();
            } else {
                device.setOnline(false);
                device.setLastSeen(clock.instant());
            }
        }
    }

}
