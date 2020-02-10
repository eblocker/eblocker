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
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.common.data.WhiteListConfigDto;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionIdentifier;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import javax.xml.ws.Holder;

import java.util.HashMap;
import java.util.Map;

public class DomainWhiteListControllerImplTest {

    private DeviceService deviceService;
    private PageContextStore pageContextStore;
    private SessionStore sessionStore;
    private UserService userService;
    private DomainWhiteListControllerImpl controller;
    private ObjectMapper objectMapper;
    private UserModule user;

    @Before
    public void setup() {
        deviceService = Mockito.mock(DeviceService.class);
        pageContextStore = Mockito.mock(PageContextStore.class);
        sessionStore = Mockito.mock(SessionStore.class);
        userService = Mockito.mock(UserService.class);
        objectMapper = Mockito.mock(ObjectMapper.class);
        controller = new DomainWhiteListControllerImpl(deviceService, pageContextStore, sessionStore, userService, objectMapper);

        // setup session mock
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn("deviceId");
        Mockito.when(sessionStore.getSession(Mockito.any(TransactionIdentifier.class))).thenReturn(session);

        // setup device mock
        Device device = new Device();
        device.setAssignedUser(123);
        Mockito.when(deviceService.getDeviceById(Mockito.anyString())).thenReturn(device);

        // setup user mock
        user = new UserModule(123, 0, "", "", null, null, false, null, new HashMap<>(), null, null, null);
        Mockito.when(userService.getUserById(123)).thenReturn(user);
    }


    @Test
    public void update() {
        // setup page context
        PageContext pageContext = new PageContext(null, "http://xkcd.org", IpAddress.parse("127.0.0.1"));
        Mockito.when(pageContextStore.get(Mockito.anyString())).thenReturn(pageContext);

        // setup response mock
        Response response = Mockito.mock(Response.class);

        // setup request mock
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("pageContextId")).thenReturn("0x1234");
        Mockito.when(request.getAttachment("transactionIdentifier")).thenReturn(new SessionIdentifier(IpAddress.parse("127.0.0.1"), "agent"));
        Holder<WhiteListConfigDto> dtoHolder = new Holder<>();
        Mockito.when(request.getBodyAs(WhiteListConfigDto.class)).then(im->dtoHolder.value);

        // enable whitelisting for ads and trackers
        dtoHolder.value = new WhiteListConfigDto("xkcd.org", true, true);
        WhiteListConfigDto result = controller.update(request, response);
        Assert.assertEquals("xkcd.org", result.getDomain());
        Assert.assertTrue(result.isAds());
        Assert.assertTrue(result.isTrackers());
        Assert.assertTrue(user.getWhiteListConfigByDomains().containsKey("xkcd.org"));
        Assert.assertTrue(user.getWhiteListConfigByDomains().get("xkcd.org").isAds());
        Assert.assertTrue(user.getWhiteListConfigByDomains().get("xkcd.org").isTrackers());

        // disable whitelisting
        dtoHolder.value = new WhiteListConfigDto("xkcd.org", false, false);
        result = controller.update(request, response);
        Assert.assertEquals("xkcd.org", result.getDomain());
        Assert.assertFalse(result.isAds());
        Assert.assertFalse(result.isTrackers());
        Assert.assertFalse(user.getWhiteListConfigByDomains().containsKey("xkcd.org"));

        // enable whitelisting for ads only
        dtoHolder.value = new WhiteListConfigDto("xkcd.org", true, false);
        result = controller.update(request, response);
        Assert.assertEquals("xkcd.org", result.getDomain());
        Assert.assertTrue(result.isAds());
        Assert.assertFalse(result.isTrackers());
        Assert.assertTrue(user.getWhiteListConfigByDomains().containsKey("xkcd.org"));
        Assert.assertTrue(user.getWhiteListConfigByDomains().get("xkcd.org").isAds());
        Assert.assertFalse(user.getWhiteListConfigByDomains().get("xkcd.org").isTrackers());

        // enable whitelisting for trackers only
        dtoHolder.value = new WhiteListConfigDto("xkcd.org", false, true);
        result = controller.update(request, response);
        Assert.assertEquals("xkcd.org", result.getDomain());
        Assert.assertFalse(result.isAds());
        Assert.assertTrue(result.isTrackers());
        Assert.assertTrue(user.getWhiteListConfigByDomains().containsKey("xkcd.org"));
        Assert.assertFalse(user.getWhiteListConfigByDomains().get("xkcd.org").isAds());
        Assert.assertTrue(user.getWhiteListConfigByDomains().get("xkcd.org").isTrackers());
    }

    @Test
    public void getDomainStatus() throws Exception {
        // setup mocks
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("pageContextId")).thenReturn("0x1234");
        Response response = Mockito.mock(Response.class);

        PageContext pageContext = new PageContext(null, "http://xkcd.org", IpAddress.parse("127.0.0.1"));
        pageContext.setWhiteListConfig(WhiteListConfig.noWhiteListing());
        Mockito.when(pageContextStore.get(Mockito.anyString())).thenReturn(pageContext);

        // check default no whitelisting
        WhiteListConfigDto dto = controller.getDomainStatus(request, response);
        Assert.assertEquals("xkcd.org", dto.getDomain());
        Assert.assertFalse(dto.isAds());
        Assert.assertFalse(dto.isTrackers());

        // allow trackers
        pageContext.setWhiteListConfig(new WhiteListConfig(false, true));
        dto = controller.getDomainStatus(request, response);
        Assert.assertEquals("xkcd.org", dto.getDomain());
        Assert.assertFalse(dto.isAds());
        Assert.assertTrue(dto.isTrackers());

        // allow ads
        pageContext.setWhiteListConfig(new WhiteListConfig(true, false));
        dto = controller.getDomainStatus(request, response);
        Assert.assertEquals("xkcd.org", dto.getDomain());
        Assert.assertTrue(dto.isAds());
        Assert.assertFalse(dto.isTrackers());

        // allow all
        pageContext.setWhiteListConfig(new WhiteListConfig(true, true));
        dto = controller.getDomainStatus(request, response);
        Assert.assertEquals("xkcd.org", dto.getDomain());
        Assert.assertTrue(dto.isAds());
        Assert.assertTrue(dto.isTrackers());
    }

    @Test
    public void testUpdateWhitelistEntryDomain() {
        testUpdateWhitelistEntry("xkcd.org", "xkcd.org");
        testUpdateWhitelistEntry("www.xkcd.org", "xkcd.org");
        testUpdateWhitelistEntry("https://www.xkcd.org/index.html", "xkcd.org");
        testUpdateWhitelistEntry("http.api.xkcd.org", "xkcd.org");
    }

    private void testUpdateWhitelistEntry(String configDomain, String expectedDomain) {
        Response response = Mockito.mock(Response.class);
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(WhiteListConfigDto.class)).thenReturn(new WhiteListConfigDto(configDomain, true, true));
        Mockito.when(request.getAttachment("transactionIdentifier")).thenReturn(new SessionIdentifier(IpAddress.parse("127.0.0.1"), "agent"));

        controller.updateWhitelistEntry(request, response);

        InOrder userServiceInOrder = Mockito.inOrder(userService);

        ArgumentCaptor<Map<String, WhiteListConfig>> captor = ArgumentCaptor.forClass(Map.class);
        userServiceInOrder.verify(userService).updateUser(Mockito.eq(123), captor.capture());
        Assert.assertNotNull(captor.getValue().get(expectedDomain));
        Assert.assertTrue(captor.getValue().get(expectedDomain).isAds());
        Assert.assertTrue(captor.getValue().get(expectedDomain).isTrackers());

        Mockito.when(request.getBodyAs(WhiteListConfigDto.class)).thenReturn(new WhiteListConfigDto(configDomain, false, false));

        controller.updateWhitelistEntry(request, response);
        userServiceInOrder.verify(userService).updateUser(Mockito.eq(123), captor.capture());
        Assert.assertNull(captor.getValue().get(expectedDomain));
    }
}

