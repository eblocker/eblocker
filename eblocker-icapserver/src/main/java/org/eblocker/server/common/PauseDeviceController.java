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
package org.eblocker.server.common;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.util.RemainingPause;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This object is able to pause the eBlocker on a specific device for a certain amount of time.
 */
public class PauseDeviceController {
    private static final Logger log = LoggerFactory.getLogger(PauseDeviceController.class);

    private final ScheduledExecutorService executorService;
    private final NetworkStateMachine networkStateMachine;
    private final DeviceService deviceService;

    private Map<String, DeviceReactivator> deviceReactivators;

    @Inject
    public PauseDeviceController(NetworkStateMachine networkStateMachine,
                                 @Named("highPrioScheduledExecutor") ScheduledExecutorService executorService,
                                 DeviceService deviceService) {
        this.networkStateMachine = networkStateMachine;
        this.executorService = executorService;
        this.deviceService = deviceService;

        this.deviceReactivators = new HashMap<>();
    }

    public synchronized RemainingPause getRemainingPause(Device device) {
        DeviceReactivator reactivator = deviceReactivators.get(device.getId());
        if (reactivator != null) {
            return new RemainingPause(reactivator.getSecondsUntilReactivation());
        }
        return new RemainingPause(0L);
    }

    private class DeviceReactivator implements Runnable {
        private String associatedDeviceId;
        private ScheduledFuture<?> associatedFuture;

        public DeviceReactivator(String deviceId) {
            this.associatedDeviceId = deviceId;
        }

        public void setAssociatedFuture(ScheduledFuture<?> associatedFuture) {
            this.associatedFuture = associatedFuture;
        }

        public boolean replaceAssociatedFuture(ScheduledFuture<?> newFuture) {
            if (associatedFuture.cancel(false)) {
                associatedFuture = newFuture;
                return true;
            }
            return false;
        }

        public long getSecondsUntilReactivation() {
            return associatedFuture.getDelay(TimeUnit.SECONDS);
        }

        public void runPrematurely() {
            if (associatedFuture.cancel(false)) {
                run();
            }
        }

        @Override
        public void run() {
            synchronized (PauseDeviceController.this) {
                // Pause ends for associated device
                log.info("Reenabling eBlocker for device {} again...", associatedDeviceId);

                //reenable device again
                Device associatedDevice = deviceService.getDeviceById(associatedDeviceId);

                reactivateDevice(associatedDevice);

            }
        }
    }

    public synchronized void reactivateDevice(Device device) {
        setPausedState(device, false);
        networkStateMachine.deviceStateChanged();
        // Remove itself from pausedDevices
        deviceReactivators.remove(device.getId());
    }

    private void setPausedState(Device device, boolean paused) {
        device.setPaused(paused);
        device.setEnabled(!paused);
        deviceService.updateDevice(device);
    }

    /**
     * Pause a device or cancels a pause if the given time is 0
     *
     * @param device
     * @param timeInSeconds the time in seconds from now when the device should be enabled again.
     * @return
     */
    public synchronized RemainingPause pauseDevice(Device device, long timeInSeconds) {
        String deviceId = device.getId();
        DeviceReactivator reactivator = deviceReactivators.get(deviceId);

        if (reactivator != null) {
            // Device already paused
            if (timeInSeconds == 0) {
                // Reactivate eBlocker for this device (cancel the pause)
                reactivator.runPrematurely();

                return new RemainingPause(0L);
            } else {
                // set new remaining pause
                long newDelay = timeInSeconds;
                // Cancel previous ScheduledFuture
                ScheduledFuture<?> newFuture = executorService.schedule(reactivator, newDelay, TimeUnit.SECONDS);
                if (reactivator.replaceAssociatedFuture(newFuture)) {
                    // Replacing went well, first Future is cancelled
                    return new RemainingPause(reactivator.getSecondsUntilReactivation());
                } else {
                    // Replacing did not go well
                    log.error("Increasing pause for device {} {} failed...", deviceId, device);
                    // If canceling failed, return the remaining time of the current AssociatedFuture
                    return new RemainingPause(reactivator.getSecondsUntilReactivation());
                }
            }
        } else if (device.isEnabled() && timeInSeconds > 0) {
            // Device not already paused - start pause
            reactivator = new DeviceReactivator(deviceId);

            log.info("Disabling and pausing device now...");
            setPausedState(device, true);

            try {
                networkStateMachine.deviceStateChanged(device);
            } catch (Exception e) {
                log.error("Could not configure network for pause of device {}", deviceId, e);
                setPausedState(device, false); // pausing failed, return device to enabled/unpaused state
                throw e;
            }

            ScheduledFuture<?> newFuture = executorService.schedule(reactivator, timeInSeconds, TimeUnit.SECONDS);
            reactivator.setAssociatedFuture(newFuture);

            deviceReactivators.put(deviceId, reactivator);

            return new RemainingPause(reactivator.getSecondsUntilReactivation());
        } else {
            // Device was disabled, stays disabled.
            return new RemainingPause(0L);
        }
    }
}
