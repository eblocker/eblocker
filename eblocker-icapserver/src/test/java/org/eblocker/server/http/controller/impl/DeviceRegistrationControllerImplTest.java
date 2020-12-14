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

import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DeviceRegistrationParameters;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationInfo;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.http.service.CustomerInfoService;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.RegistrationServiceAvailabilityCheck;
import org.eblocker.server.http.service.ReminderService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

public class DeviceRegistrationControllerImplTest {

    private static final String FALLBACK_PRODUCT_ID = "fallback-product-id";
    private static final String FALLBACK_PRODUCT_NAME = "fallback-product-name";
    private static final String FALLBACK_PRODUCT_FEATURES = "feature-1, feature-2,feature-3,  feature-4  ";

    private CustomerInfoService customerInfoService;
    private DataSource dataSource;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private DeviceRegistrationClient deviceRegistrationClient;
    private ProductInfoService productInfoService;
    private RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck;
    private ReminderService reminderService;
    private SslService sslService;
    private SystemUpdater systemUpdater;

    private DeviceRegistrationControllerImpl controller;

    @Before
    public void setUp() {
        customerInfoService = Mockito.mock(CustomerInfoService.class);
        dataSource = Mockito.mock(DataSource.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        deviceRegistrationClient = Mockito.mock(DeviceRegistrationClient.class);
        reminderService = Mockito.mock(ReminderService.class);
        registrationServiceAvailabilityCheck = Mockito.mock(RegistrationServiceAvailabilityCheck.class);
        systemUpdater = Mockito.mock(SystemUpdater.class);

        productInfoService = Mockito.mock(ProductInfoService.class);
        Mockito.when(productInfoService.get()).thenReturn(new ProductInfo(null, null, new String[]{ "WOL" }));

        sslService = Mockito.mock(SslService.class);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);

        controller = new DeviceRegistrationControllerImpl(FALLBACK_PRODUCT_ID, FALLBACK_PRODUCT_NAME,
            FALLBACK_PRODUCT_FEATURES, deviceRegistrationProperties, deviceRegistrationClient, productInfoService,
            reminderService, null, sslService, systemUpdater, null, null, dataSource, customerInfoService,
            registrationServiceAvailabilityCheck);
    }

    @Test
    public void testNormalRegistration() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(DeviceRegistrationParameters.class)).thenReturn(new DeviceRegistrationParameters("mail@mail.com", "name", "1234-5678", "12345678", null, "2.3", false));
        Response response = Mockito.mock(Response.class);

        DeviceRegistrationRequest registrationRequest = Mockito.mock(DeviceRegistrationRequest.class);
        Mockito.when(deviceRegistrationProperties.generateRequest("mail@mail.com", "name", "1234-5678", "12345678", null, "2.3")).thenReturn(registrationRequest);

        DeviceRegistrationResponse registrationResponse = new DeviceRegistrationResponse("mail@mail.com", "name", "5678", new byte[0], new byte[0], "test", "false", false, null, null);
        Mockito.when(deviceRegistrationClient.register(registrationRequest)).thenReturn(registrationResponse);
        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK);
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("name");

        ProductInfo productInfo = new ProductInfo("product-id", "product-name", new String[]{ "feature" });
        Mockito.when(deviceRegistrationClient.getProductInfo()).thenReturn(productInfo);

        DeviceRegistrationInfo info = controller.register(request, response);

        Assert.assertEquals(RegistrationState.OK, info.getRegistrationState());
        Assert.assertEquals(productInfo, info.getProductInfo());
        Assert.assertEquals("name", info.getDeviceName());

        Mockito.verify(deviceRegistrationProperties).processResponse(registrationResponse);
        Mockito.verify(reminderService).setReminder();
        Mockito.verify(productInfoService).save(productInfo);
    }

    @Test(expected = BadRequestException.class)
    public void testFallbackRegistrationAvailable() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(DeviceRegistrationParameters.class)).thenReturn(new DeviceRegistrationParameters(null, null, null, null, null, null, true));

        Response response = Mockito.mock(Response.class);
        Mockito.when(registrationServiceAvailabilityCheck.isRegistrationAvailable()).thenReturn(true);

        controller.register(request, response);
    }

    //    @Test(expected = BadRequestException.class)
    @Test
    public void testFallbackRegistrationNotAvailable() {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getBodyAs(DeviceRegistrationParameters.class)).thenReturn(new DeviceRegistrationParameters(null, "name", null, null, null, null, true));

        Response response = Mockito.mock(Response.class);
        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK_UNREGISTERED);
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("name");

        DeviceRegistrationInfo info = controller.register(request, response);

        Assert.assertEquals(RegistrationState.OK_UNREGISTERED, info.getRegistrationState());
        Assert.assertEquals("name", info.getDeviceName());
        Assert.assertNotNull(info.getProductInfo());
        Assert.assertEquals(FALLBACK_PRODUCT_ID, info.getProductInfo().getProductId());
        Assert.assertEquals(FALLBACK_PRODUCT_NAME, info.getProductInfo().getProductName());
        Assert.assertArrayEquals(new String[]{ "feature-1", "feature-2", "feature-3", "feature-4" }, info.getProductInfo().getProductFeatures());

        Mockito.verify(deviceRegistrationProperties).registrationFallback("name");
    }
}
