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

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import org.eblocker.server.http.server.HttpTransactionIdentifier;
import org.eblocker.server.http.service.CustomDomainFilterConfigService;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

public class CustomDomainFilterConfigControllerImplTest {

    private static final IpAddress IP_ADDRESS = IpAddress.parse("192.168.3.3");

    private CustomDomainFilterConfigService service;

    private CustomDomainFilterConfigControllerImpl controller;
    private Request request;
    private Response response;

    @Before
    public void setUp() {
        request = Mockito.mock(Request.class);
        Mockito.when(request.getHeader("userId")).thenReturn("1");

        response = Mockito.mock(Response.class);
        service = Mockito.mock(CustomDomainFilterConfigService.class);

        controller = new CustomDomainFilterConfigControllerImpl(service);
    }

    @Test
    public void getFilter() {
        CustomDomainFilterConfig persistentConfig = new CustomDomainFilterConfig();
        Mockito.when(service.getCustomDomainFilterConfig(1)).thenReturn(persistentConfig);
        CustomDomainFilterConfig config = controller.getFilter(request, response);
        Assert.assertSame(persistentConfig, config);
    }

    @Test(expected = NotFoundException.class)
    public void getFilterUnknownDevice() {
        Mockito.when(request.getHeader("userId")).thenReturn(null);
        controller.getFilter(request, response);
    }

    @Test
    public void setFilter() {
        Mockito.when(service.setCustomDomainFilterConfig(Mockito.eq(1), Mockito.any(CustomDomainFilterConfig.class))).then(im -> im.getArgument(1));
        CustomDomainFilterConfig config = new CustomDomainFilterConfig();
        config.setBlacklistedDomains(Sets.newHashSet("www.a.com", "http://www.b.com/index.html", "https://www.c.com/index.html", "http.d.com"));
        config.setWhitelistedDomains(Sets.newHashSet("www.e.com", "http://www.f.com/index.html", "https://www.g.com/index.html", "http.h.com"));
        Mockito.when(request.getBodyAs(CustomDomainFilterConfig.class)).thenReturn(config);

        CustomDomainFilterConfig savedConfig = controller.setFilter(request, response);

        Assert.assertEquals(Sets.newHashSet("www.a.com", "www.b.com", "www.c.com", "http.d.com"), savedConfig.getBlacklistedDomains());
        Assert.assertEquals(Sets.newHashSet("www.e.com", "www.f.com", "www.g.com", "http.h.com"), savedConfig.getWhitelistedDomains());
    }
}
