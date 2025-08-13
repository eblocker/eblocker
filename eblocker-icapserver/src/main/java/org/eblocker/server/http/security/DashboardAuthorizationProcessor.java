/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.security;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.pipeline.Preprocessor;
import org.restexpress.route.Route;

/**
 * This pre-processor verifies that the current dashboard user has access to routes that are flagged with
 * VERIFY_DEVICE_ID or VERIFY_USER_ID.
 * <p>
 * If a route is flagged with VERIFY_DEVICE_ID it must contain a parameter {deviceId}.
 * <p>
 * If a route is flagged with VERIFY_USER_ID it must contain a parameter {userId}.
 * <p>
 * A user logged in as admin can access all devices and users.
 * The requesting device's operating user may access their own settings and the settings of their devices.
 *
 * Note: during the boot phase this pre-processor runs uninitialized. There is no DB connection yet,
 * so authorization of flagged routes is refused. But to show the boot screen, other routes must be
 * allowed.
 */
@Singleton
@SubSystemService(value = SubSystem.SERVICES, allowUninitializedCalls = true)
public class DashboardAuthorizationProcessor implements Preprocessor {
    public static final String VERIFY_DEVICE_ID = "VERIFY_DEVICE_ID";
    public static final String VERIFY_USER_ID = "VERIFY_USER_ID";
    public static final String DEVICE_ID_KEY = "deviceId";
    public static final String USER_ID_KEY = "userId";

    private final Provider<DeviceService> deviceServiceProvider;
    private DeviceService deviceService; // only after initialization, because it needs the database

    @Inject
    public DashboardAuthorizationProcessor(Provider<DeviceService> deviceServiceProvider) {
        this.deviceServiceProvider = deviceServiceProvider;
    }

    @SubSystemInit
    public void init() {
        deviceService = deviceServiceProvider.get();
    }

    @Override
    public void process(Request request) {
        Route route = request.getResolvedRoute();

        if (route.isFlagged(VERIFY_DEVICE_ID) || route.isFlagged(VERIFY_USER_ID)) {
            AppContext appContext = getAppContext(request);
            if (appContext == AppContext.ADMINDASHBOARD) {
                return; // Admin can access any device or user
            }

            IpAddress requestingIp = ControllerUtils.getRequestIPAddress(request);
            if (requestingIp == null) {
                throw new BadRequestException("Could not get client's IP address");
            }
            if (deviceService == null) {
                throw new UnauthorizedException("Dashboard authorization not initialized yet");
            }
            Device requestingDevice = getRequestingDevice(requestingIp);

            if (route.isFlagged(VERIFY_DEVICE_ID)) {
                verifyDeviceAccess(request.getHeader(DEVICE_ID_KEY), requestingDevice);
            }

            if (route.isFlagged(VERIFY_USER_ID)) {
                verifyUserAccess(request.getHeader(USER_ID_KEY), requestingDevice);
            }
        }
    }

    private void verifyDeviceAccess(String deviceId, Device requestingDevice) {
        if (deviceId == null) {
            throw new BadRequestException("Required parameter 'deviceId' not set in Request");
        }
        if (deviceId.equals(requestingDevice.getId())) {
            return; // Device can access its own settings
        }
        Device accessedDevice = deviceService.getDeviceById(deviceId);
        if (accessedDevice == null) {
            throw new NotFoundException("Could not find device " + deviceId + " in DB");
        }
        if (requestingDevice.getOperatingUser() == accessedDevice.getOperatingUser()) {
            return; // Operating user can access other devices he/she operates
        }
        throw new UnauthorizedException("Operating user " + requestingDevice.getOperatingUser() +
                " of device " + requestingDevice.getId() +
                " is not authorized to access settings of device " + deviceId);
    }

    private void verifyUserAccess(String userIdStr, Device requestingDevice) {
        if (userIdStr == null) {
            throw new BadRequestException("Required parameter 'userId' not set in Request");
        }
        int userId = Integer.parseInt(userIdStr);
        if (requestingDevice.getOperatingUser() == userId) {
            return; // operating user can access his/her own settings
        }
        throw new UnauthorizedException("Operating user " + requestingDevice.getOperatingUser() +
                " of device " + requestingDevice.getId() +
                " is not authorized to access settings of user " + userIdStr);
    }

    private AppContext getAppContext(Request request) {
        AppContext appContext = (AppContext) request.getAttachment(SecurityProcessor.APP_CONTEXT_ATTACHMENT);
        if (appContext == null) {
            throw new EblockerException("Could not get app context");
        }
        return appContext;
    }

    private Device getRequestingDevice(IpAddress ipAddress) {
        Device device = deviceService.getDeviceByIp(ipAddress);
        if (device == null) {
            throw new NotFoundException("Could not find device with IP " + ipAddress);
        }
        return device;
    }
}
