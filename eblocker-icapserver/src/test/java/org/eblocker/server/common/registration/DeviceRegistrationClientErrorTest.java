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

import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.error.ClientRequestException;
import org.junit.Test;
import org.mockserver.model.Header;

import java.io.IOException;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DeviceRegistrationClientErrorTest extends DeviceRegistrationTestBase {
    private static final String EMAIL = "test_user_1@eblocker.com";
    private static final String DEVICE_NAME = "John's eBlocker";
    private static final String HARDWARE_ID = "HARDWARE-ID";

    private DeviceRegistrationProperties properties;
    private DeviceRegistrationClient client;

    @Override
    protected boolean doStartMockServer() {
        return true;
    }

    /**
     * Simulate the start of a device.
     *
     * @throws ParseException
     */
    private void startDevice() throws IOException, ParseException {
        properties = createDeviceRegistrationProperties();
        client = createDeviceRegistrationClient(properties);
    }

    /**
     * Real registration process (from devices point of view).
     *
     * @param emailAddress
     * @param deviceName
     * @param licenseKey
     */
    private void attemptRegistration(String emailAddress, String deviceName, String licenseKey, String serialNumber) {
        // Use device registration properties to generate a request object
        DeviceRegistrationRequest request = properties.generateRequest(emailAddress, deviceName, licenseKey, serialNumber, true, null);

        // Use client to send request object to backend and receive response
        client.register(request);
    }

    @Test
    public void test_unspecificServerErrorWithContent() throws IOException, ParseException {
        getClientAndServer().when(
                request()
                        .withMethod("POST")
                        .withPath("/api/registration")
                        .withHeader(new Header("X-eBlockerOS-Version", VERSION))
        ).respond(
                response()
                        .withStatusCode(400)
                        .withBody("something-went-wrong")
        );
        // Start with a fresh device
        startDevice();

        // Try to register the device
        try {
            attemptRegistration(EMAIL, DEVICE_NAME, "LICENSE_KEY", HARDWARE_ID);
            fail("Expected a ClientRequestException");

        } catch (ClientRequestException e) {
            assertEquals(400, e.getStatus());
            assertEquals("something-went-wrong", e.getError().getMessage());
        }
    }

    @Test
    public void test_unspecificServerErrorWithoutContent() throws IOException, ParseException {
        getClientAndServer().when(
                request()
                        .withMethod("POST")
                        .withPath("/api/registration")
        ).respond(
                response()
                        .withStatusCode(400)
        );
        // Start with a fresh device
        startDevice();

        // Try to register the device
        try {
            attemptRegistration(EMAIL, DEVICE_NAME, "LICENSE_KEY", HARDWARE_ID);
            fail("Expected a ClientRequestException");

        } catch (ClientRequestException e) {
            assertEquals(400, e.getStatus());
            assertEquals("<empty>", e.getError().getMessage());
        }
    }

}
