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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.DisplayIconMode;
import org.eblocker.server.common.data.IconSettings;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.MacPrefix;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.IpResponseTable;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.icap.resources.DefaultEblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = true)
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private final DataSource datasource;
    private final UserAgentService userAgentService;

    private final DeviceRegistrationProperties deviceRegistrationProperties;

    private final ConcurrentMap<String, Device> devicesById = new ConcurrentHashMap<>(64, 0.75f, 2);
    private final ConcurrentMap<IpAddress, Device> devicesByIp = new ConcurrentHashMap<>(64, 0.75f, 2);
    private final List<DeviceChangeListener> listeners = new ArrayList<>();
    private final NetworkInterfaceWrapper networkInterfaceWrapper;
    private final DeviceFactory deviceFactory;

    private final MacPrefix macPrefix = new MacPrefix();

    private final IpResponseTable ipResponseTable;
    private final Clock clock;
    private final int deviceOfflineAfterSeconds;

    @Inject
    public DeviceService(DataSource datasource,
                         DeviceRegistrationProperties deviceRegistrationProperties, UserAgentService userAgentService,
                         NetworkInterfaceWrapper networkInterfaceWrapper, DeviceFactory deviceFactory,
                         IpResponseTable ipResponseTable, Clock clock,
                         @Named("device.offline.after.seconds") int deviceOfflineAfterSeconds) {
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.datasource = datasource;
        this.userAgentService = userAgentService;
        this.deviceFactory = deviceFactory;
        this.networkInterfaceWrapper = networkInterfaceWrapper;
        this.ipResponseTable = ipResponseTable;
        this.clock = clock;
        this.deviceOfflineAfterSeconds = deviceOfflineAfterSeconds;
    }

    @SubSystemInit
    public void init() {
        networkInterfaceWrapper.addIpAddressChangeListener(this::onIpAddressChange);
        ipResponseTable.addLatestTimestampUpdateListener(this::onLatestTimestampUpdate);
        try {
            macPrefix.addInputStream(ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES));
        } catch (IOException e) {
            log.error("Could not read MAC prefixes", e);
        }

        onIpAddressChange(true, false);
    }

    public Collection<Device> getDevices(boolean refresh) {
        if (refresh) {
            refresh();
        }
        return new HashSet<>(devicesById.values());
    }

    public void updateDevice(Device device) {
        // Check ID of operating user actually references an existing user
        if (datasource.get(UserModule.class, device.getOperatingUser()) == null
                || datasource.get(UserModule.class, device.getAssignedUser()) == null) {
            throw new BadRequestException("Device "
                    + device.getUserFriendlyName()
                    + " references non-existing operating user "
                    + device.getOperatingUser()
                    + " or non-existing assigned user "
                    + device.getAssignedUser());
        }

        // handle icon visibility (if in auto mode) depending on device and global SSL status
        device = updateIconStatus(device);

        if (!device.isSslEnabled()) {
            userAgentService.turnOffCloakingForDevice(device.getAssignedUser(), device.getId());
        }

        datasource.save(device);

        resolveIpAddressConflicts(device);
        cacheDevice(device);
        notifyListeners(device);
        // ip may have been changed
        removeStaleIpAddressEntries();
    }

    private Device updateIconStatus(Device dev) {
        Device device = dev;
        if (device.isControlBarAutoMode() && device.isSslEnabled() && isSslEnabled() &&
                device.getIconMode() == DisplayIconMode.OFF) {
            device.setIconMode(DisplayIconMode.ON_ALL_DEVICES);
        } else if (device.isControlBarAutoMode() && (!device.isSslEnabled() || !isSslEnabled())) {
            device.setIconMode(DisplayIconMode.OFF);
        }
        return device;
    }

    private boolean isSslEnabled() {
        return datasource.getSSLEnabledState();
    }

    public Device getDeviceByIp(IpAddress ip) {
        return getDevice(devicesByIp, ip);
    }

    public boolean showWelcomePageForDevice(Device device) {
        return device.isShowWelcomePage() && deviceRegistrationProperties.getRegistrationState() == RegistrationState.OK;
    }

    public Device getDeviceById(String id) {
        return getDevice(devicesById, id);
    }

    /**
     * Take the device and look for other devices in the list with the same IP address;
     * Resolving the conflict by flagging the other devices (which can not be online with the same IP address at the same time -> assumption)
     * as inactive.
     *
     * @param device IP address of device changed, or device was seen for the first time
     */
    private void resolveIpAddressConflicts(Device device) {
        Collection<Device> allDevices = getDevices(false);
        allDevices.remove(device);

        resolveIpAddressConflicts(device.getIpAddresses(), allDevices);
    }

    private void resolveIpAddressConflicts(Collection<IpAddress> ips, Collection<Device> allDevices) {
        for (Device currentDevice : allDevices) {
            List<IpAddress> currentDeviceIpAddresses = currentDevice.getIpAddresses();
            if (currentDeviceIpAddresses.removeAll(ips)) {
                if (currentDeviceIpAddresses.isEmpty() || !currentDeviceIpAddresses.get(0).equals(currentDevice.getIpAddresses().get(0))) {
                    currentDevice.setIpAddressFixed(false);
                }
                currentDevice.setIpAddresses(currentDeviceIpAddresses);
                datasource.save(currentDevice);
                notifyListeners(currentDevice);
            }
        }
    }

    private <T> Device getDevice(Map<T, Device> cache, T key) {
        Device device = cache.get(key);
        if (device != null) {
            return device;
        }
        refresh();
        return cache.get(key);
    }

    public void refresh() {
        Set<Device> devices = datasource.getDevices();
        devices.forEach(this::setVendor);
        updateCacheEntries(devices);
        removeDeletedDevices(devices);
        removeStaleIpAddressEntries();
    }

    public void addListener(DeviceChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Delete a device from the cache and data source
     *
     * @param device the device to be deleted
     */
    public Device delete(Device device) {
        // Make sure online devices are not deleted
        Device deviceFromCache = devicesById.get(device.getId());
        if (deviceFromCache != null && deviceFromCache.isOnline()) {
            return null;
        }
        datasource.delete(device);
        // TODO: Is it possible that the device is not in the cache?
        //       If so, shouldn't we still execute the listeners?
        Device cachedDevice = devicesById.remove(device.getId());
        cachedDevice.getIpAddresses().forEach(devicesByIp::remove);
        listeners.forEach(listener -> listener.onDelete(cachedDevice));
        return cachedDevice;
    }

    private Set<Device> delete(Predicate<Device> predicate) {
        Set<Device> deletedDevices = new HashSet<>();
        Iterator<Device> i = devicesById.values().iterator();
        while (i.hasNext()) {
            Device device = i.next();
            if (predicate.test(device)) {
                delete(device);
                deletedDevices.add(device);
            }
        }
        return deletedDevices;
    }

    private void updateCacheEntries(Set<Device> devices) {
        devices.forEach(this::cacheDevice);
    }

    /**
     * Removes deleted devices from cache
     *
     * @param devices list of all current known devices
     */
    private void removeDeletedDevices(Set<Device> devices) {
        delete(device -> !devices.contains(device));
    }

    /**
     * Removes stale ip-address entries
     */
    private void removeStaleIpAddressEntries() {
        Iterator<Map.Entry<IpAddress, Device>> i = devicesByIp.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<IpAddress, Device> entry = i.next();
            Device device = devicesById.get(entry.getValue().getId());
            // remove this entry if its ip-address is not current anymore. Note: device can be null here in case of
            // concurrent removal of a device
            if (device == null || !device.getIpAddresses().contains(entry.getKey())) {
                i.remove();
            }
        }
    }

    private void setVendor(Device device) {
        device.setVendor(macPrefix.getVendor(device.getHardwareAddressPrefix()));
    }

    private void cacheDevice(Device device) {
        devicesById.put(device.getId(), device);
        device.getIpAddresses().forEach(ip -> devicesByIp.put(ip, device));
    }

    private void notifyListeners(Device device) {
        listeners.forEach(listener -> listener.onChange(device));
    }

    public void setEnabledForAllButCurrentDevice(boolean enabled, Device currentDevice) {
        getDevices(true).stream()
                .filter(device -> device.isEnabled() != enabled)
                .filter(device -> !device.equals(currentDevice))
                .forEach(device -> {
                    device.setEnabled(enabled);
                    updateDevice(device);
                });
    }

    private void onLatestTimestampUpdate(String hardwareAddress, long millis) {
        Device device = getDeviceById(Device.ID_PREFIX + hardwareAddress);
        if (device == null) {
            log.error("Could not update lastSeen of device {}, because it does not exist.", hardwareAddress);
            return;
        }
        device.setLastSeen(Instant.ofEpochMilli(millis));
        datasource.updateLastSeen(device);
    }

    public void setOnlineStatus(Device device) {
        if (device.isVpnClient()) {
            device.setOnline(true);
        } else {
            Instant lastSeen = device.getLastSeen();
            if (lastSeen != null) {
                Instant now = clock.instant();
                device.setOnline(lastSeen.plusSeconds(deviceOfflineAfterSeconds).isAfter(now));
            } else {
                device.setOnline(false);
            }
        }
    }

    public interface DeviceChangeListener {
        void onChange(Device device);

        void onDelete(Device device);

        void onReset(Device device);
    }

    public void logoutUser(Device device) {
        device.setOperatingUser(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID);
        updateDevice(device);
    }

    public Device resetIconSettings(Device device) {
        device.setIconMode(DisplayIconMode.getDefault());
        device.setIconPosition(Device.DisplayIconPosition.getDefault());
        updateDevice(device);
        return device;
    }

    public Device setIconSettings(Device device, IconSettings iconSettings) {
        device.setIconPosition(iconSettings.getIconPosition());
        device.setIconMode(iconSettings.getDisplayIconMode());
        updateDevice(device);
        return device;
    }

    public Device.DisplayIconPosition setIconPosition(Device device, Device.DisplayIconPosition iconPosition) {
        device.setIconPosition(iconPosition);
        updateDevice(device);
        return iconPosition;
    }

    private void onIpAddressChange(boolean ip4Updated, boolean ip6Updated) {
        // only resolve IPv4 conflicts for now
        if (!ip4Updated) {
            return;
        }
        Ip4Address newIp = networkInterfaceWrapper.getFirstIPv4Address();
        resolveIpAddressConflicts(Collections.singletonList(newIp), getDevices(true));
    }

    public Device resetDevice(String deviceId) {
        // Check if there exists such a device to be reset
        Device deviceToBeReset = getDeviceById(deviceId);
        if (deviceToBeReset == null) {
            return null;
        }
        // Preserve some values
        List<IpAddress> ipAddresses = deviceToBeReset.getIpAddresses();
        String vendor = deviceToBeReset.getVendor();

        // Remove everything connected to the device
        delete(deviceToBeReset);

        // Create new device with default values
        Device resetDevice = deviceFactory.createDevice(deviceId, ipAddresses, false);
        resetDevice.setVendor(vendor);
        // This calls UserService to set the Assigned User / Default System User / Operating User
        listeners.forEach(listener -> listener.onReset(resetDevice));

        // Save new device
        updateDevice(resetDevice);

        return resetDevice;
    }
}
