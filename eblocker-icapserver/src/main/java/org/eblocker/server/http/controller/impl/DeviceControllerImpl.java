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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.PauseDeviceController;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.IconSettings;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.ShowWelcomeFlags;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.RemainingPause;
import org.eblocker.server.http.controller.DeviceController;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DeviceOnlineStatusCache;
import org.eblocker.server.http.service.DevicePermissionsService;
import org.eblocker.server.http.service.DeviceScanningService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ForbiddenException;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeviceControllerImpl implements DeviceController {
    private static final String DEVICE_EBLOCKER_VENDOR = "eBlocker";
    private static final Logger log = LoggerFactory.getLogger(DeviceControllerImpl.class);
    private final AnonymousService anonymousService;
    private final DeviceOnlineStatusCache deviceOnlineStatusCache;
    private final DevicePermissionsService devicePermissionsService;
    private final DeviceScanningService deviceScanningService;
    private final DeviceService deviceService;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterfaceWrapper;
    private final NetworkStateMachine networkStateMachine;
    private final OpenVpnService openVpnService;
    private final PauseDeviceController pauseDeviceController;
    private final DeviceFactory deviceFactory;

    @Inject
    public DeviceControllerImpl(AnonymousService anonymousService,
                                DeviceOnlineStatusCache deviceOnlineStatusCache,
                                DevicePermissionsService devicePermissionsService,
                                DeviceScanningService deviceScanningService,
                                DeviceService deviceService,
                                DeviceRegistrationProperties deviceRegistrationProperties,
                                FeatureToggleRouter featureToggleRouter,
                                NetworkInterfaceWrapper networkInterfaceWrapper,
                                NetworkStateMachine networkStateMachine,
                                OpenVpnService openVpnService,
                                PauseDeviceController pauseDeviceController,
                                DeviceFactory deviceFactory) {
        this.anonymousService = anonymousService;
        this.deviceOnlineStatusCache = deviceOnlineStatusCache;
        this.devicePermissionsService = devicePermissionsService;
        this.deviceScanningService = deviceScanningService;
        this.deviceService = deviceService;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterfaceWrapper = networkInterfaceWrapper;
        this.networkStateMachine = networkStateMachine;
        this.openVpnService = openVpnService;
        this.pauseDeviceController = pauseDeviceController;
        this.deviceFactory = deviceFactory;
    }

    @Override
    public Object deleteDevice(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = new Device();
        device.setId(deviceId);

        if (deviceService.delete(device) != null) {
            networkStateMachine.deviceStateChanged();
        }

        return device;
    }

    @Override
    public Device resetDevice(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device resettedDevice = deviceService.resetDevice(deviceId);
        if (resettedDevice != null) {
            networkStateMachine.deviceStateChanged();
        }
        return resettedDevice;
    }

    @Override
    public List<Device> getAllDevices(Request request, Response response) {
        IpAddress requestIPAddress = ControllerUtils.getRequestIPAddress(request);
        String eBlockerId = "device:" + networkInterfaceWrapper.getHardwareAddressHex();

        Collection<Device> devices = deviceService.getDevices(true);
        List<Device> result = new ArrayList<>(devices.size());
        for (Device device : devices) {
            if (device.getIpAddresses().contains(requestIPAddress)) {
                device.markAsCurrentDevice();
            }
            deviceOnlineStatusCache.setOnlineStatus(device);

            if (device.getId().equals(eBlockerId)) {
                // eBlocker itself should not be in this list. If it is, remove it.
                // TODO: This is not a good solution. The whole Device/Controller/Service has to be refactored. After refactoring, eBlocker should be a device like all others.
                deviceService.delete(device);
            } else {
                result.add(device);
            }
        }
        // Add eblocker
        Device eblockerDevice = new Device();
        eblockerDevice.setIpAddresses(networkInterfaceWrapper.getAddresses().stream()
                .filter(ip -> featureToggleRouter.isIp6Enabled() || ip.isIpv4())
                .collect(Collectors.toList()));
        eblockerDevice.setId(eBlockerId);
        eblockerDevice.setName(deviceRegistrationProperties.getDeviceName());
        eblockerDevice.setVendor(DEVICE_EBLOCKER_VENDOR);
        eblockerDevice.setOnline(true);
        eblockerDevice.setIsEblocker(true);
        result.add(eblockerDevice);

        return result;
    }

    @Override
    public List<Device> getOperatingUserDevices(Request request, Response response) {
        Device current = getCurrentDevice(request);
        int operatingUser = current.getOperatingUser();

        return deviceService.getDevices(true).stream()
                .filter(device -> device.getOperatingUser() == operatingUser)
                .collect(Collectors.toList());
    }

    @Override
    public Device getDeviceById(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device deviceById = deviceService.getDeviceById(deviceId);
        if (deviceById == null) {
            throw new NotFoundException("Device " + deviceId + " not found in datastore.");
        }
        return deviceById;
    }

    @Override
    public Object updateDevice(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = request.getBodyAs(Device.class);
        device.setId(deviceId);

        updateDevice(device);

        return device;
    }

    @Override
    public Object updateDeviceDashboard(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        if (deviceId == null) {
            throw new BadRequestException("Required parameter deviceId is missing in request.");
        }
        IpAddress ipAddress = ControllerUtils.getRequestIPAddress(request);
        Device currentDevice = deviceService.getDeviceByIp(ipAddress);

        if (currentDevice == null) {
            throw new NotFoundException("Device with IP " + ipAddress + " not found in datastore.");
        }

        if (!devicePermissionsService.operatingUserMayUpdate(currentDevice)) { // TODO: Move to DashboardAuthorizationProcessor
            throw new ForbiddenException("Operating user may not update current device");
        }

        Device device = request.getBodyAs(Device.class);
        device.setId(deviceId);

        updateDevice(device);

        return device;
    }

    // FIXME - Most of this method should be moved to the DeviceService
    private void updateDevice(Device device) {
        Device current = deviceService.getDeviceById(device.getId());

        /*
         * The default system user must not be changed. EB1-2220 describes an issue where possible two eBlockers
         * in the network may have caused a device of one eBlocker to be updated in Redis of another, causing the
         * default system user to be updated. To avoid a device being accidentally overridden, an updated
         * default system user indicates an inconsistent state, so that we abort the operation here.
         */
        if (current.getDefaultSystemUser() != device.getDefaultSystemUser()) {
            throw new BadRequestException("Attempt to update the default system user of device " + device.getId() + ". The default system user must not be updated.");
        }

        // Was a paused device reactivated (via console)?
        if (device.isEnabled() && device.isPaused() && !current.isEnabled() && current.isPaused()) {
            // Cancel pause by setting remaining pause to 0 (it remains paused if the pause is now != 0)
            device.setPaused(pauseDeviceController.pauseDevice(device, 0).getPausing() != 0);
            // return since the device was only unpaused and the pauseDeviceController makes relevant calls to deviceService
            return;
        }

        log.debug("SavingDevice {} dhcp: {}", device.toString(), device.isIpAddressFixed());
        // use device service, so that the internal cache is updated
        deviceService.updateDevice(device);

        if ((current.isEnabled() != device.isEnabled())
                || (current.isSslEnabled() != device.isSslEnabled())
                // If the DHCP config needs rewriting:
                || (current.isIpAddressFixed() != device.isIpAddressFixed())) {
            networkStateMachine.deviceStateChanged(device); //adapt firewall and also heal device if in automode
        }

        if (!device.isEnabled()) {
            // device got disabled
            log.debug("Removing device from all lists (torclients and sslclients)");
            if (current.isEnabled() && current.isUseAnonymizationService()) {
                anonymousService.disableVpn(device);
            }
        } else {
            // TOR/VPN management
            if (!device.isUseAnonymizationService()) {
                anonymousService.disableTor(device);
                anonymousService.disableVpn(device);
            } else if (device.isRoutedThroughTor()) {
                anonymousService.enableTor(device);
            } else if (device.getUseVPNProfileID() != null) {
                VpnProfile profile = openVpnService.getVpnProfileById(device.getUseVPNProfileID());
                if (profile != null) {
                    anonymousService.enableVpn(device, profile);
                } else {
                    log.warn("cannot activate non-existing vpn profile {} for {} - resetting", device.getUseVPNProfileID(), device.getId());
                    device.setUseVPNProfileID(null);
                    device.setUseAnonymizationService(false);
                    deviceService.updateDevice(device);

                    anonymousService.disableTor(device);
                    anonymousService.disableVpn(device);
                }
            }
        }

        deviceOnlineStatusCache.setOnlineStatus(device);
    }

    @Override
    public ShowWelcomeFlags updateShowWelcomeFlags(Request request, Response response) {
        IpAddress ipAddress = ControllerUtils.getRequestIPAddress(request);
        Device currentDevice = deviceService.getDeviceByIp(ipAddress);

        if (currentDevice == null) {
            throw new NotFoundException("Device with IP " + ipAddress + " not found in datastore.");
        }

        ShowWelcomeFlags flags = request.getBodyAs(ShowWelcomeFlags.class);
        currentDevice.setShowWelcomePage(flags.isShowWelcomePage());
        currentDevice.setShowBookmarkDialog(flags.isShowBookmarkDialog());
        deviceService.updateDevice(currentDevice);
        return flags;
    }

    @Override
    public Object scanDevices(Request request, Response response) {
        deviceScanningService.scan();
        return true;
    }

    @Override
    public boolean isScanningAvailable(Request request, Response response) {
        return deviceScanningService.isScanAvailable();
    }

    @Override
    public Long getScanningInterval(Request request, Response response) {
        return deviceScanningService.getScanningInterval();
    }

    @Override
    public void setScanningInterval(Request request, Response response) {
        Long interval = request.getBodyAs(Long.class);
        if (interval >= 0) {
            deviceScanningService.setScanningInterval(interval);
        }
    }

    @Override
    public Boolean isAutoEnableNewDevices(Request request, Response response) {
        return deviceFactory.isAutoEnableNewDevices();
    }

    @Override
    public void setAutoEnableNewDevices(Request request, Response response) {
        Boolean isAutoEnableNewDevices = request.getBodyAs(Boolean.class);
        deviceFactory.setAutoEnableNewDevices(isAutoEnableNewDevices);
    }

    @Override
    public void setAutoEnableNewDevicesAndResetExisting(Request request, Response response) {
        Boolean isAutoEnableNewDevices = request.getBodyAs(Boolean.class);
        deviceFactory.setAutoEnableNewDevices(isAutoEnableNewDevices);
        deviceService.setEnabledForAllButCurrentDevice(isAutoEnableNewDevices, getCurrentDevice(request));
    }

    @Override
    public RemainingPause getPauseByDeviceId(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
        return getPause(device);
    }

    @Override
    public RemainingPause setPauseByDeviceId(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
        RemainingPause pause = request.getBodyAs(RemainingPause.class);
        return setPause(device, pause);
    }

    @Override
    public RemainingPause getPauseCurrentDevice(Request request, Response response) {
        Device device = getCurrentDevice(request);
        return getPause(device);
    }

    @Override
    public RemainingPause pauseCurrentDevice(Request request, Response response) {
        RemainingPause pause = request.getBodyAs(RemainingPause.class);
        Device device = getCurrentDevice(request);
        return setPause(device, pause);
    }

    @Override
    public RemainingPause pauseCurrentDeviceIfNotYetPausing(Request request, Response response) {
        RemainingPause pause = request.getBodyAs(RemainingPause.class);
        Device device = getCurrentDevice(request);

        RemainingPause remainingPause = getPause(device);
        if (remainingPause == null) {
            throw new BadRequestException("error.pause.notPossible");

        } else if (remainingPause.getPausing() > 0) {
            // Already pausing
            response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
            return null;
        }

        remainingPause = setPause(device, pause);
        if (remainingPause == null || remainingPause.getPausing() == 0) {
            throw new ForbiddenException("error.pause.notAllowed");
        }
        return remainingPause;
    }

    private RemainingPause getPause(Device device) {
        if (device != null) {
            log.info("Get pause of current device: {} ...", device);
            RemainingPause result = pauseDeviceController.getRemainingPause(device);
            result.setPausingAllowed(devicePermissionsService.operatingUserMayPause(device));
            return result;
        } else {
            return null;
        }
    }

    private RemainingPause setPause(Device device, RemainingPause pause) {
        if (device != null && pause != null) {
            RemainingPause result;

            boolean pausingAllowed = devicePermissionsService.operatingUserMayPause(device);
            if (pausingAllowed) {
                log.info("Pausing the device: {} now for {} seconds...", device, pause.getPausing());
                result = pauseDeviceController.pauseDevice(device, pause.getPausing());
            } else {
                result = new RemainingPause(0L);
            }
            result.setPausingAllowed(pausingAllowed);
            return result;
        } else {
            log.error("Could not pause device with ID {} because it is not known", (device != null ? device.getId() : "NULL"));
            return null;
        }
    }

    @Override
    public boolean getShowWarnings(Request request, Response response) {
        Device device = getCurrentDevice(request);
        return device == null || device.getAreDeviceMessagesSettingsDefault();
    }

    @Override
    public Object postShowWarnings(Request request, Response response) {
        Device device = getCurrentDevice(request);
        if (device != null) {
            Map<String, Boolean> map = request.getBodyAs(Map.class);
            device.setAreDeviceMessagesSettingsDefault(map.get("showWarnings"));
            deviceService.updateDevice(device);
            return device;
        }
        return null;
    }

    @Override
    public void logoutUserFromDevice(Request request, Response response) {
        Device device = getCurrentDevice(request);
        if (device != null) {
            deviceService.logoutUser(device);
        } else {
            throw new BadRequestException("Logout failed, device unknown");
        }
    }

    @Override
    public Device getCurrentDevice(Request request, Response response) {
        return getCurrentDevice(request);
    }

    @Override
    public boolean getPauseDialogStatus(Request request, Response response) {
        Device device = getCurrentDevice(request);
        return device == null || device.isShowPauseDialog();
    }

    @Override
    public boolean getPauseDialogStatusDoNotShowAgain(Request request, Response response) {
        Device device = getCurrentDevice(request);
        return device == null || device.isShowPauseDialogDoNotShowAgain();
    }

    @Override
    public void updatePauseDialogStatusDoNotShowAgain(Request request, Response response) {
        Device device = getCurrentDevice(request);
        if (device != null) {
            Map<String, Boolean> map = request.getBodyAs(Map.class);
            device.setShowPauseDialogDoNotShowAgain(map.get("doNotShowPauseDialogAgain"));
            deviceService.updateDevice(device);
        }
    }

    @Override
    public void updatePauseDialogStatus(Request request, Response response) {
        Device device = getCurrentDevice(request);
        if (device != null) {
            Map<String, Boolean> map = request.getBodyAs(Map.class);
            device.setShowPauseDialog(map.get("showPauseDialog"));
            deviceService.updateDevice(device);
        }
    }

    @Override
    public IconSettings getIconSettings(Request request, Response response) {
        Device device = getCurrentDevice(request);
        return new IconSettings(device);
    }

    @Override
    public IconSettings setIconSettings(Request request, Response response) {
        Device device = getCurrentDevice(request);
        IconSettings iconSettings = request.getBodyAs(IconSettings.class);
        Device changedDevice = deviceService.setIconSettings(device, iconSettings);
        return new IconSettings(changedDevice);
    }

    @Override
    public Device.DisplayIconPosition setIconPosition(Request request, Response response) {
        Device device = getCurrentDevice(request);
        Device.DisplayIconPosition iconPosition = Device.DisplayIconPosition.valueOf(request.getHeader("iconPos"));
        return deviceService.setIconPosition(device, iconPosition);
    }

    @Override
    public void updateDeviceDnsAdsEnabledStatus(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
        Boolean value = request.getBodyAs(Boolean.class);
        device.setFilterAdsEnabled(value);
        deviceService.updateDevice(device);
    }

    @Override
    public void updateDeviceDnsTrackersEnabledStatus(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
        Boolean value = request.getBodyAs(Boolean.class);
        device.setFilterTrackersEnabled(value);
        deviceService.updateDevice(device);
    }

    @Override
    public IconSettings resetIconSettings(Request request, Response response) {
        Device device = getCurrentDevice(request);
        Device resettedDevice = deviceService.resetIconSettings(device);
        return new IconSettings(resettedDevice);
    }

    private Device getCurrentDevice(Request request) {
        IpAddress ipAddress = ControllerUtils.getRequestIPAddress(request);
        return deviceService.getDeviceByIp(ipAddress);
    }
}
