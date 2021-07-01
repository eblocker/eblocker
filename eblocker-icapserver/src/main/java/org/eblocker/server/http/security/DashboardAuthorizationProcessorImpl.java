package org.eblocker.server.http.security;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.restexpress.exception.UnauthorizedException;
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
 */
@Singleton
public class DashboardAuthorizationProcessorImpl implements DashboardAuthorizationProcessor {
    private final DeviceService deviceService;

    @Inject
    public DashboardAuthorizationProcessorImpl(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    public void process(Request request) {
        Route route = request.getResolvedRoute();

        if (route.isFlagged(VERIFY_DEVICE_ID)) {
            verifyDeviceAccess(request);
        }

        if (route.isFlagged(VERIFY_USER_ID)) {
            verifyUserAccess(request);
        }
    }

    private void verifyDeviceAccess(Request request) {
        AppContext appContext = getAppContext(request);
        if (appContext == AppContext.ADMINDASHBOARD) {
            return; // Admin can access any device
        }
        String deviceId = request.getHeader(DEVICE_ID_KEY);
        if (deviceId == null) {
            throw new BadRequestException("Required parameter 'deviceId' not set in Request");
        }
        Device requestingDevice = getRequestingDevice(request);
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

    private void verifyUserAccess(Request request) {
        AppContext appContext = getAppContext(request);
        if (appContext == AppContext.ADMINDASHBOARD) {
            return; // Admin can access any user
        }
        String userIdStr = request.getHeader(USER_ID_KEY);
        if (userIdStr == null) {
            throw new BadRequestException("Required parameter 'userId' not set in Request");
        }
        int userId = Integer.parseInt(userIdStr);
        Device requestingDevice = getRequestingDevice(request);
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

    private Device getRequestingDevice(Request request) {
        IpAddress ipAddress = ControllerUtils.getRequestIPAddress(request);
        if (ipAddress == null) {
            throw new BadRequestException("Could not get client's IP address");
        }
        Device device = deviceService.getDeviceByIp(ipAddress);
        if (device == null) {
            throw new NotFoundException("Could not find device with IP " + ipAddress);
        }
        return device;
    }
}
