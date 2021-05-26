package org.eblocker.server.http.security;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.http.controller.impl.ControllerTestUtils;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.route.Route;

public class DashboardAuthorizationProcessorImplTest {
    private DashboardAuthorizationProcessor processor;
    private DeviceService deviceService;
    private Request request;
    private Route route;
    private Device currentDevice;
    private Device secondDevice;
    private Device otherUsersDevice;
    private int userId = 23;
    private int otherUserId = 42;

    @Before
    public void setUp() throws Exception {
        deviceService = Mockito.mock(DeviceService.class);
        request = Mockito.mock(Request.class);
        route = Mockito.mock(Route.class);
        Mockito.when(request.getResolvedRoute()).thenReturn(route);
        processor = new DashboardAuthorizationProcessorImpl(deviceService);

        TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
        currentDevice = tdf.createDevice("abcdef000023", "192.168.1.27", true);
        secondDevice = tdf.createDevice("abcdef000024", "192.168.1.29", true);
        otherUsersDevice = tdf.createDevice("abcdef000042", "192.168.1.42", true);
        currentDevice.setOperatingUser(userId);
        secondDevice.setOperatingUser(userId);
        otherUsersDevice.setOperatingUser(otherUserId);
        tdf.addDevice(currentDevice);
        tdf.addDevice(secondDevice);
        tdf.addDevice(otherUsersDevice);
        tdf.commit();

        IpAddress ipAddress = currentDevice.getIpAddresses().get(0);
        ControllerTestUtils.setIpAddressOfMockRequest(ipAddress, request);
    }

    @Test
    public void passUnflaggedRequests() {
        processor.process(request);
    }

    @Test
    public void adminMayAccessAnyDevice() {
        setAppContext(AppContext.ADMINDASHBOARD);
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test
    public void userMayAccessCurrentDevice() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.DEVICE_ID_KEY, currentDevice.getId());
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test
    public void userMayAccessSecondDevice() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.DEVICE_ID_KEY, secondDevice.getId());
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test(expected = UnauthorizedException.class)
    public void userMayNotAccessOtherUsersDevice() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.DEVICE_ID_KEY, otherUsersDevice.getId());
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test
    public void adminMayAccessAnyUser() {
        setAppContext(AppContext.ADMINDASHBOARD);
        flagRoute(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        processor.process(request);
    }

    @Test
    public void userMayAccessOwnSettings() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.USER_ID_KEY, Integer.toString(userId));
        flagRoute(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        processor.process(request);
    }

    @Test(expected = UnauthorizedException.class)
    public void userMayNotAccessOtherUsersSettings() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.USER_ID_KEY, Integer.toString(otherUserId));
        flagRoute(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        processor.process(request);
    }

    @Test(expected = NotFoundException.class)
    public void unknownDeviceId() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.DEVICE_ID_KEY, "device:abcdef555555");
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test(expected = UnauthorizedException.class)
    public void unknownUserId() {
        setAppContext(AppContext.DASHBOARD);
        setRequestHeader(DashboardAuthorizationProcessor.USER_ID_KEY, "99");
        flagRoute(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        processor.process(request);
    }

    @Test(expected = BadRequestException.class)
    public void deviceIdMissing() {
        setAppContext(AppContext.DASHBOARD);
        flagRoute(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        processor.process(request);
    }

    @Test(expected = BadRequestException.class)
    public void userIdMissing() {
        setAppContext(AppContext.DASHBOARD);
        flagRoute(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        processor.process(request);
    }

    private void setAppContext(AppContext appContext) {
        Mockito.when(request.getAttachment("appContext")).thenReturn(appContext);
    }

    private void setRequestHeader(String key, String value) {
        Mockito.when(request.getHeader(key)).thenReturn(value);
    }

    private void flagRoute(String flag) {
        Mockito.when(route.isFlagged(flag)).thenReturn(true);
    }
}