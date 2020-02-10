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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.MessageCenterService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.common.network.BaseURLs;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import static org.junit.Assert.assertEquals;

public class ControlBarControllerImplTest {
    private ControlBarControllerImpl controller;
    private BaseURLs baseURLs;
    private Response response;

    @Before
    public void setUp() {
        response = new Response();
        baseURLs = Mockito.mock(BaseURLs.class);
        DeviceService deviceService = Mockito.mock(DeviceService.class);
        ParentalControlService parentalControlService = Mockito.mock(ParentalControlService.class);
        UserService userService = Mockito.mock(UserService.class);
        OpenVpnService openVpnService = Mockito.mock(OpenVpnService.class);
        MessageCenterService messageCenterService = Mockito.mock(MessageCenterService.class);
        SessionStore sessionStore = Mockito.mock(SessionStore.class);
        PageContextStore pageContextStore = Mockito.mock(PageContextStore.class);

       controller = new ControlBarControllerImpl(
           baseURLs,
           sessionStore,
           pageContextStore,
           deviceService,
           parentalControlService,
           userService,
           openVpnService,
           messageCenterService,
           "10.8.0.0",
           "255.255.255.0");
    }

    @Test
    public void testGetConsoleIp() throws JsonProcessingException {
        Device device = TestDeviceFactory.createDevice("0123456789ab",  "192.168.101.21", true);
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        Mockito.when(baseURLs.selectIpForPage(false, "http")).thenReturn("http://192.168.101.1");
        Mockito.when(baseURLs.selectIpForPage(false, "https")).thenReturn("https://192.168.101.1");

        Mockito.when(request.getHeader("Scheme")).thenReturn("http");
        controller.getConsoleIp(request, response);
        assertEquals("http://192.168.101.1", controller.getConsoleIp(request, response));

        Mockito.when(request.getHeader("Scheme")).thenReturn("https");
        controller.getConsoleIp(request, response);
        assertEquals("https://192.168.101.1", controller.getConsoleIp(request, response));
    }

    @Test
    public void testGetConsoleIpForVpn() throws JsonProcessingException {
        Device device = TestDeviceFactory.createDevice("0123456789ab",  "10.8.0.11", true);
        Request request = ControllerTestUtils.mockRequestByDevice(device);
        Mockito.when(baseURLs.selectIpForPage(true, "http")).thenReturn("http://10.8.0.1");
        Mockito.when(baseURLs.selectIpForPage(true, "https")).thenReturn("https://10.8.0.1");

        Mockito.when(request.getHeader("Scheme")).thenReturn("http");
        controller.getConsoleIp(request, response);
        assertEquals("http://10.8.0.1", controller.getConsoleIp(request, response));

        Mockito.when(request.getHeader("Scheme")).thenReturn("https");
        controller.getConsoleIp(request, response);
        assertEquals("https://10.8.0.1", controller.getConsoleIp(request, response));
    }
}
