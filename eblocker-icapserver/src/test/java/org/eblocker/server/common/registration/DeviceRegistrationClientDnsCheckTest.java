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
package org.eblocker.server.common.registration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.text.ParseException;

public class DeviceRegistrationClientDnsCheckTest extends DeviceRegistrationTestBase {

    private DeviceRegistrationClient deviceRegistrationClient;

    @Before
    public void setUp() throws IOException, ParseException {
        deviceRegistrationClient =  createDeviceRegistrationClient();
    }

    @Test
    public void testSuccess() {
        getClientAndServer().when(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/mobile/dns")
                .withBody("helloworld.com")
        ).respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody("true")
        );

        Assert.assertTrue(deviceRegistrationClient.requestMobileDnsCheck("helloworld.com"));
    }

    @Test
    public void testFail() {
        getClientAndServer().when(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/mobile/dns")
                .withBody("helloworld.com")
        ).respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody("false")
        );

        Assert.assertFalse(deviceRegistrationClient.requestMobileDnsCheck("helloworld.com"));
    }

    @Override
    protected boolean doStartMockServer() {
        return true;
    }
}
