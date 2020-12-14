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

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.crypto.util.DateUtil;
import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.LicenseType;
import org.eblocker.server.common.system.CpuInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class DeviceRegistrationPropertiesTest extends DeviceRegistrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistrationPropertiesTest.class);

    protected final static String EMAIL = "john.doe@example.com";
    protected final static String DEVICE_NAME = "John's eBlocker";
    protected final static String LICENSE_ID = UUID.randomUUID().toString();
    protected final static String HARDWARE_ID = "HARDWARE-ID";
    protected final static Boolean CONFIRMED = true;
    protected final static String TOS_VERSION = "23.42.1337";

    protected final static int LICENSE_VALIDITY = 1;

    protected final static String OTHER_CPU_SERIAL = "fedcba987654";

    private Date earliest; // Registration timestamp must not be BEFORE this date
    private Date latest;   // Registration timestamp must not be AFTER  this date

    private DeviceRegistrationProperties drp;

    @Before
    public void init() throws IOException, ParseException {
        super.init();
        drp = createDeviceRegistrationProperties();
    }

    @Test
    public void testInitial() throws CertificateException, IOException, CryptoException, ParseException {
        log.info("registration.properties:\n{}", new String(Files.readAllBytes(Paths.get(registrationPropertiesFileName))));

        // We should now have a file with initial registration properties
        assertTrue(Files.exists(Paths.get(registrationPropertiesFileName)));

        // Initially, the device should not be registered
        assertEquals(RegistrationState.NEW, drp.getRegistrationState());

        // The device ID should not be null or empty
        String deviceId = drp.getDeviceId();
        assertNotNull(deviceId);
        assertFalse(deviceId.isEmpty());

        // Most other propertes should be null
        assertNull(drp.getDeviceName());
        assertNull(drp.getDeviceRegisteredAt());
        assertNull(drp.getDeviceRegisteredBy());
        assertNull(drp.getDeviceCertificate());
        assertEquals(LicenseType.NONE, drp.getLicenseType());
        assertNull(drp.getLicenseNotValidAfter());
        assertNull(drp.getLicenseCertificate());
        assertFalse(drp.isLicenseAutoRenewal());

        // Read current content from file
        Properties initialProperties = loadPropertiesFile();

        // Check some properties from the file, fow which we do not have getters
        assertNotNull(initialProperties.getProperty("registrationId"));
        assertNotNull(UUID.fromString(initialProperties.getProperty("registrationId"))); // should be a valid UUID
        assertEquals(CPU_SERIAL, initialProperties.getProperty("registrationCpuSerial"));
        assertEquals(REG_TYPE_1, Integer.valueOf(initialProperties.getProperty("registrationType")).intValue());

        // Simulate restart of unregistered device by initializing a new instance
        DeviceRegistrationProperties drp2 = createDeviceRegistrationProperties();

        // The device should still not be registered
        assertEquals(RegistrationState.NEW, drp2.getRegistrationState());

        // Make sure that the device ID has not changed
        assertEquals(deviceId, drp2.getDeviceId());

        // Make sure that the complete registration properties file has not changed
        assertEquals(initialProperties, loadPropertiesFile());
    }

    @Test
    public void testGenerateRequest() throws CertificateException, IOException, CryptoException {
        String deviceId = drp.getDeviceId();

        // Read current content from file
        Properties initialProperties = loadPropertiesFile();

        // Generate a device registration request
        DeviceRegistrationRequest deviceRegistrationRequest = drp.generateRequest(EMAIL, DEVICE_NAME, LICENSE_ID, HARDWARE_ID, CONFIRMED, TOS_VERSION);

        // Still not registered
        assertEquals(RegistrationState.NEW, drp.getRegistrationState());

        // Make sure that the registration properties have not changed
        assertEquals(initialProperties, loadPropertiesFile());

        // Verify registration request
        assertNotNull(deviceRegistrationRequest);
        assertEquals(deviceId, deviceRegistrationRequest.getDeviceId());
        assertEquals(EMAIL, deviceRegistrationRequest.getEmailAddress());
        assertEquals(DEVICE_NAME, deviceRegistrationRequest.getDeviceName());
        assertEquals(LICENSE_ID, deviceRegistrationRequest.getLicenseKey());

        assertNotNull(deviceRegistrationRequest.getEncodedDeviceCertificate());
        // Certificate in request should be self-signed
        X509Certificate certificate = decode(deviceRegistrationRequest.getEncodedDeviceCertificate());
        assertTrue(PKI.verifyCertificateSignature(certificate, certificate));

        assertNotNull(deviceRegistrationRequest.getEncodedLicenseCertificate());
        // Certificate in request should be self-signed
        certificate = decode(deviceRegistrationRequest.getEncodedLicenseCertificate());
        assertTrue(PKI.verifyCertificateSignature(certificate, certificate));
    }

    @Test
    public void testProcessResponse() throws CertificateException, CryptoException, IOException {

        // Read current content from file
        Properties initialProperties = loadPropertiesFile();

        // The device ID should not be null or empty
        String deviceId = drp.getDeviceId();

        // Generate a device registration request
        DeviceRegistrationRequest deviceRegistrationRequest = drp.generateRequest(EMAIL, DEVICE_NAME, LICENSE_ID, HARDWARE_ID, CONFIRMED, TOS_VERSION);

        // Send the request to a simulated backend, receive a response
        earliest = DateUtil.stripMillis(new Date(), 0);
        DeviceRegistrationResponse deviceRegistrationResponse = simulateBackend(deviceRegistrationRequest, LICENSE_VALIDITY, true);
        latest = DateUtil.stripMillis(new Date(), 1);

        // Process the response
        drp.processResponse(deviceRegistrationResponse);
        log.info("registration.properties:\n{}", new String(Files.readAllBytes(Paths.get(registrationPropertiesFileName))));

        // Read current content from file
        Properties registeredProperties = loadPropertiesFile();

        // Now the device should be registered
        assertEquals(RegistrationState.OK, drp.getRegistrationState());

        // Check other properties
        assertEquals(initialProperties.getProperty("registrationId"), registeredProperties.getProperty("registrationId"));
        assertEquals(initialProperties.getProperty("registrationMac"), registeredProperties.getProperty("registrationMac"));
        assertEquals(initialProperties.getProperty("registrationType"), registeredProperties.getProperty("registrationType"));
        assertEquals(deviceId, drp.getDeviceId());
        assertEquals(EMAIL, drp.getDeviceRegisteredBy());
        assertEquals(DEVICE_NAME, drp.getDeviceName());
        assertEquals(LicenseType.SUBSCRIPTION, drp.getLicenseType());
        assertTrue(drp.isLicenseAutoRenewal());

        // Registration date should be "now"
        assertFalse(drp.getDeviceRegisteredAt().before(earliest));
        assertFalse(drp.getDeviceRegisteredAt().after(latest));

        // License end date should be in a year from now
        assertFalse(drp.getLicenseNotValidAfter().before(DateUtil.addYears(earliest, LICENSE_VALIDITY)));
        assertFalse(drp.getLicenseNotValidAfter().after(DateUtil.addYears(latest, LICENSE_VALIDITY)));

        assertNotNull(drp.getDeviceCertificate());
        assertTrue(PKI.verifyCertificateSignature(drp.getDeviceCertificate(), deviceIssuer.getCertificate()));

        assertNotNull(drp.getLicenseCertificate());
        assertTrue(PKI.verifyCertificateSignature(drp.getLicenseCertificate(), licenseIssuer.getCertificate()));

    }

    @Test
    public void testRegisteredReloadOk() throws CertificateException, CryptoException, IOException, ParseException {
        // Register the device with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        // Read current content from file
        Properties registeredProperties = loadPropertiesFile();

        // Simulate restart of registered device by initializing a new instance
        DeviceRegistrationProperties drp2 = createDeviceRegistrationProperties();

        // Assert that the device is still registered
        assertEquals(RegistrationState.OK, drp2.getRegistrationState());

        // Assert that the registration properties have not been changed
        assertEquals(registeredProperties, loadPropertiesFile());
    }

    @Test
    public void testRegisterCommunityOk() throws CertificateException, CryptoException, IOException {
        // Register the device with COMMUNITY license
        registerWithCommunityLicense();

        // Now the device should be registered
        assertEquals(RegistrationState.OK, drp.getRegistrationState());

        // Check other properties
        assertEquals(EMAIL, drp.getDeviceRegisteredBy());
        assertEquals(DEVICE_NAME, drp.getDeviceName());
        assertEquals(LicenseType.COMMUNITY, drp.getLicenseType());
        assertFalse(drp.isLicenseAutoRenewal());

        // Registration date should be "now"
        assertFalse(drp.getDeviceRegisteredAt().before(earliest));
        assertFalse(drp.getDeviceRegisteredAt().after(latest));

        assertNotNull(drp.getDeviceCertificate());
        assertTrue(PKI.verifyCertificateSignature(drp.getDeviceCertificate(), deviceIssuer.getCertificate()));

        // We have no license certificate
        assertNull(drp.getLicenseCertificate());
        // Community license end date should be as long as the device CA exists
        assertEquals(deviceIssuer.getCertificate().getNotAfter(), drp.getLicenseNotValidAfter());
    }

    @Test
    public void testMakeLicenseCredentialsAvailable() throws CertificateException, CryptoException, IOException {
        // Register the device with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        // License should not be marked as "about to expire"
        assertFalse(drp.isLicenseAboutToExpire());

        drp.makeLicenseCredentialsAvailable();

        assertTrue(drp.isSubscriptionValid());
        assertLicenseCredentialsAreAvailable();
    }

    @Test
    public void testMakeLicenseCredentialsUnavailable() throws CertificateException, CryptoException, IOException {
        // Register the device with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        // Initially, credentials should not be available
        assertLicenseCredentialsAreUnavailable();

        // Now, make license credentials available
        drp.makeLicenseCredentialsAvailable();

        // And make them unavailable again
        drp.makeLicenseCredentialsUnavailable();

        assertLicenseCredentialsAreUnavailable();
    }

    @Test
    public void testMakeLicenseCredentialsAvailable_invalid() throws CertificateException, CryptoException, IOException, ParseException {
        // Cannot make license credentials available for unregistered device
        drp.makeLicenseCredentialsAvailable();
        assertFalse(drp.isSubscriptionValid());

        // Register with COMMUNITY license
        registerWithCommunityLicense();

        // Cannot make license credentials available for COMMUNITY license
        drp.makeLicenseCredentialsAvailable();
        assertFalse(drp.isSubscriptionValid());

        // reset registration
        drp.reset();

        // Register with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        // Cross check that license credentials can now be made available
        drp.makeLicenseCredentialsAvailable();
        assertTrue(drp.isSubscriptionValid());

        // Load registration properties on different HW
        DeviceRegistrationProperties drp2 = createDeviceRegistrationProperties(OTHER_CPU_SERIAL);

        // Assert that license credentials are not available now
        assertLicenseCredentialsAreUnavailable();

        // Assert that they cannot be made available on wrong device
        drp2.makeLicenseCredentialsAvailable();
        assertFalse(drp2.isSubscriptionValid());

        // reset registration
        drp.reset();

        // Register with *EXPIRED* SUBSCRIPTION license
        registerWithSubscriptionLicense(-1);

        // Assert that expired license credentials cannot be made available
        drp.makeLicenseCredentialsAvailable();
        assertFalse(drp.isSubscriptionValid());

    }

    @Test
    public void testFileHackedInvalid_deregister() throws CertificateException, CryptoException, IOException, ParseException {
        // Register the device with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        String deviceId = drp.getDeviceId();

        // Read current content from file
        Properties registeredProperties = loadPropertiesFile();

        // Hack the properties, so that the file cannot be read anymore
        registeredProperties.setProperty("registrationState", "UNKNOWN_STATE");
        registeredProperties.store(Files.newBufferedWriter(Paths.get(registrationPropertiesFileName)), "HACKED");
        log.info("registration.properties:\n{}", new String(Files.readAllBytes(Paths.get(registrationPropertiesFileName))));

        // Simulate restart of registered device by initializing a new instance
        DeviceRegistrationProperties drp2 = createDeviceRegistrationProperties();

        // Assert that the device is now unregistered
        assertEquals(RegistrationState.NEW, drp2.getRegistrationState());

        // Assert that the deviceId has changed
        assertNotEquals(deviceId, drp2.getDeviceId());

        // Most other propertes should be null
        assertNull(drp2.getDeviceName());
        assertNull(drp2.getDeviceRegisteredAt());
        assertNull(drp2.getDeviceRegisteredBy());
        assertNull(drp2.getDeviceCertificate());
        assertEquals(LicenseType.NONE, drp2.getLicenseType());
        assertNull(drp2.getLicenseNotValidAfter());
        assertNull(drp2.getLicenseCertificate());
        assertFalse(drp2.isLicenseAutoRenewal());

    }

    @Test
    public void testFileHackedDeviceID_invalid() throws CertificateException, CryptoException, IOException, ParseException {
        // Register the device with SUBSCRIPTION license
        registerWithSubscriptionLicense();

        // Simulate restart of registered device ON DIFFERENT HW by initializing a new instance WITH DIFFERENT MAC!
        DeviceRegistrationProperties drp2 = createDeviceRegistrationProperties(OTHER_CPU_SERIAL);

        // Assert that the device registration is now invalid
        assertEquals(RegistrationState.INVALID, drp2.getRegistrationState());

        // Assert that the deviceId has not changed
        assertEquals(drp.getDeviceId(), drp2.getDeviceId());

        // Assert that other propertes have not changed
        assertEquals(drp.getDeviceName(), drp2.getDeviceName());
        assertEquals(drp.getDeviceRegisteredAt(), drp2.getDeviceRegisteredAt());
        assertEquals(drp.getDeviceRegisteredBy(), drp2.getDeviceRegisteredBy());
        assertEquals(drp.getDeviceCertificate(), drp2.getDeviceCertificate());
        assertEquals(drp.getLicenseType(), drp2.getLicenseType());
        assertEquals(drp.getLicenseNotValidAfter(), drp2.getLicenseNotValidAfter());
        assertEquals(drp.getLicenseCertificate(), drp2.getLicenseCertificate());
        assertEquals(drp.isLicenseAutoRenewal(), drp2.isLicenseAutoRenewal());
    }

    @Test
    public void testCertificateRevoked() throws IOException, InterruptedException, CertificateException, CryptoException, ParseException {
        CpuInfo cpuInfo = Mockito.mock(CpuInfo.class);
        when(cpuInfo.getSerial()).thenReturn(CPU_SERIAL);

        DeviceRegistrationLicenseState revokationState = Mockito.mock(DeviceRegistrationLicenseState.class);
        when(revokationState.checkCertificate()).thenReturn(RegistrationState.INVALID);

        registerWithSubscriptionLicense();

        DeviceRegistrationProperties drp = new DeviceRegistrationProperties(
                systemKey,
                registrationPropertiesFileName,
                licenseKeyFileName,
                licenseCertFileName,
                trustStoreResource,
                trustStorePassword,
                truststoreCopyFileName,
                KEY_SIZE,
                REG_TYPE_1,
                WARNING_PERIOD,
                LIFETIME_INDICATOR,
                Files.createTempDirectory(null).toString(),
                cpuInfo,
                revokationState);

        // Initially the registration state is ok
        assertEquals(RegistrationState.OK, drp.getRegistrationState());
        drp.acquireRevokationState();
        assertEquals(RegistrationState.REVOKED, drp.getRegistrationState());
    }

    private void registerWithCommunityLicense() throws CertificateException, CryptoException {
        // Register the device with COMMUNITY license
        DeviceRegistrationRequest deviceRegistrationRequest = drp.generateRequest(EMAIL, DEVICE_NAME, LICENSE_ID, HARDWARE_ID, CONFIRMED, TOS_VERSION);
        earliest = DateUtil.stripMillis(new Date(), 0);
        DeviceRegistrationResponse deviceRegistrationResponse = simulateBackend(deviceRegistrationRequest, 0, false);
        latest = DateUtil.stripMillis(new Date(), 1);
        drp.processResponse(deviceRegistrationResponse);
    }

    private void registerWithSubscriptionLicense() throws CertificateException, CryptoException {
        registerWithSubscriptionLicense(LICENSE_VALIDITY);
    }

    private void registerWithSubscriptionLicense(int licenseValidity) throws CertificateException, CryptoException {
        // Register the device with COMMUNITY license
        DeviceRegistrationRequest deviceRegistrationRequest = drp.generateRequest(EMAIL, DEVICE_NAME, LICENSE_ID, HARDWARE_ID, CONFIRMED, TOS_VERSION);
        earliest = DateUtil.stripMillis(new Date(), 0);
        DeviceRegistrationResponse deviceRegistrationResponse = simulateBackend(deviceRegistrationRequest, licenseValidity, true);
        latest = DateUtil.stripMillis(new Date(), 1);
        drp.processResponse(deviceRegistrationResponse);
    }

    private void assertLicenseCredentialsAreAvailable() throws IOException {
        log.debug("licenseKeyFileName:  " + licenseKeyFileName);
        log.debug("licenseCertFileName: " + licenseCertFileName);

        assertTrue(Files.exists(Paths.get(licenseCertFileName)));
        assertTrue(Files.exists(Paths.get(licenseKeyFileName)));

        List<String> certFileLines = Files.readAllLines(Paths.get(licenseCertFileName));
        assertTrue(certFileLines.get(0).contains("CERTIFICATE"));
        assertTrue(certFileLines.get(certFileLines.size() - 1).contains("CERTIFICATE"));

        List<String> keyFileLines = Files.readAllLines(Paths.get(licenseKeyFileName));
        assertTrue(keyFileLines.get(0).contains("PRIVATE KEY"));
        assertTrue(keyFileLines.get(keyFileLines.size() - 1).contains("PRIVATE KEY"));
    }

    private void assertLicenseCredentialsAreUnavailable() throws IOException {
        log.info("licenseKeyFileName:  " + licenseKeyFileName);
        log.info("licenseCertFileName: " + licenseCertFileName);

        // Test if they are really unavailable / Ok, if file does not exist
        if (Files.exists(Paths.get(licenseCertFileName))) {
            // If file exists, it must not contain PRIVATE KEY
            List<String> keyFileLines = Files.readAllLines(Paths.get(licenseKeyFileName));
            assertFalse(keyFileLines.get(0).contains("PRIVATE KEY"));
            assertFalse(keyFileLines.get(keyFileLines.size() - 1).contains("PRIVATE KEY"));
        }
        if (Files.exists(Paths.get(licenseKeyFileName))) {
            // If file exists, it must not contain PRIVATE KEY
            List<String> certFileLines = Files.readAllLines(Paths.get(licenseCertFileName));
            assertFalse(certFileLines.get(0).contains("CERTIFICATE"));
            assertFalse(certFileLines.get(certFileLines.size() - 1).contains("CERTIFICATE"));
        }
    }

    private Properties loadPropertiesFile() throws IOException {
        Properties properties = new Properties();
        properties.load(Files.newInputStream(Paths.get(registrationPropertiesFileName)));
        return properties;
    }
}
