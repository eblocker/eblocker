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
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.RegistrationState;
import org.eblocker.registration.error.ClientRequestException;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@Ignore("Tests need running eBlocker Backend Server with fresh database")
public class DeviceRegistrationClientTest extends DeviceRegistrationTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationClientTest.class);

    protected static final String EMAIL = "test_user_1@eblocker.com";
    protected static final String DEVICE_NAME = "John's eBlocker";
    protected static final String HARDWARE_ID = "HARDWARE-ID";

    // Please note: We are using a real backend with a real database.
    // Used licenses will actually be maked "activated" in the backend DB.
    // So do not use the same license key in different unit tests!
    protected static final String LICENSE_KEY_1 = "EA15-00000000-0000-0000-0000-000000000001";
    protected static final String LICENSE_KEY_2 = "EA15-00000000-0000-0000-0000-000000000002";
    protected static final String LICENSE_KEY_3 = "EA15-00000000-0000-0000-0000-000000000003";
    protected static final String LICENSE_KEY_4 = "EA15-00000000-0000-0000-0000-000000000004";

    private static final String OTHER_TRUSTSTORE_RESOURCE = "classpath:default-ca/other-truststore.jks";
    private static final String OTHER_TRUSTSTORE_PASSWORD = "other-truststore";

    private DeviceRegistrationProperties properties;
    private DeviceRegistrationClient client;

    /**
     * Simulate the start of a new device by creating a fresh set of registration properties,
     * including a new device ID.
     *
     * @throws ParseException
     */
    private void newDevice() throws IOException, ParseException {
        init();
    }

    /**
     * Simulate the start of a device.
     *
     * @throws ParseException
     */
    private void startDevice() throws IOException, ParseException {
        //
        // In a real application, these (singleton) objects can be injected with Guice.
        //
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
    private void register(String emailAddress, String deviceName, String licenseKey, String serialNumber) {
        // Use device registration properties to generate a request object
        DeviceRegistrationRequest request = properties.generateRequest(emailAddress, deviceName, licenseKey, serialNumber, true, null);

        // Use client to send request object to backend and receive response
        DeviceRegistrationResponse response = client.register(request);

        // Let device registration properties process the response object
        properties.processResponse(response);
    }

    @Test
    public void test_ok() throws IOException, ParseException {
        // Start with a fresh device
        startDevice();

        // Make sure the device is not yet registered
        assertEquals(RegistrationState.NEW, properties.getRegistrationState());

        // Register the device
        register(EMAIL, DEVICE_NAME, LICENSE_KEY_1, HARDWARE_ID);

        // Assert that the device has actually been registered
        assertEquals(RegistrationState.OK, properties.getRegistrationState());
        assertNotNull(properties.getDeviceCertificate());

        // Make license cert and key available
        properties.makeLicenseCredentialsAvailable();

        LOG.info("licenseCertFileName=" + licenseCertFileName);
        Files.readAllLines(Paths.get(licenseCertFileName)).forEach(LOG::info);
        LOG.info("licenseKeyFileName=" + licenseKeyFileName);
        Files.readAllLines(Paths.get(licenseKeyFileName)).forEach(LOG::info);
    }

    @Test(expected = javax.ws.rs.ProcessingException.class)
    public void test_untrustedServer() throws IOException, GeneralSecurityException, ParseException {
        //
        // Provide a different truststore, so that the backend's server certificate cannot be validated
        //
        trustStoreResource = OTHER_TRUSTSTORE_RESOURCE;
        trustStorePassword = OTHER_TRUSTSTORE_PASSWORD;

        // Start with a fresh device
        startDevice();

        // Try to egister the device -> should fail with an exception
        register(EMAIL, DEVICE_NAME, LICENSE_KEY_2, HARDWARE_ID);
    }

    @Test
    public void test_failWithUsedLicenseKey() throws IOException, ParseException {
        // Start with a fresh device
        startDevice();

        // Register the device
        try {
            register(EMAIL, DEVICE_NAME, LICENSE_KEY_3, HARDWARE_ID);
        } catch (ClientRequestException e) {
            fail("Exception not yet expected here!");
        }

        // Start with another device
        newDevice();
        startDevice();

        // Try to register the second device with the same license key
        try {
            register("other-" + EMAIL, DEVICE_NAME + " #2", LICENSE_KEY_3, HARDWARE_ID);
        } catch (ClientRequestException e) {
            assertEquals("x", e.getMessage());
            return;
        }
        fail("Expected ClientRequestException exception");
    }

    @Test(expected = ClientRequestException.class)
    public void test_invalidRequest() throws IOException, ParseException {
        // Start with a fresh device
        startDevice();

        // Make sure the device is not yet registered
        assertEquals(RegistrationState.NEW, properties.getRegistrationState());

        // Register the device
        register(null, null, null, null);

    }

}
