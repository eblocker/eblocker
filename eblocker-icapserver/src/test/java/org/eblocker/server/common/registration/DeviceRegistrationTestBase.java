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

import org.eblocker.server.common.data.LocaleSettings;
import org.eblocker.server.http.service.SettingsService;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.keys.SystemKey;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.crypto.util.DateUtil;
import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.LicenseType;
import org.eblocker.server.common.system.CpuInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

public abstract class DeviceRegistrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(DeviceRegistrationTestBase.class);

    protected final static int REG_TYPE_1 = 1;
    protected final static int KEY_SIZE = 2048;
    protected final static String CPU_SERIAL = "0123456789abcdef";
    protected final static String ORG_NAME = "Bright Mammoth Brain GmbH";
    protected final static int WARNING_PERIOD = 42;
    protected final static String LIFETIME_INDICATOR = "2099/12/31 23:59:59 +0100";
    protected final static String VERSION = "9.99.999";

    protected static final String HOSTNAME = "localhost";
    protected static final int PORT = 1080;

    protected static final String BASE_URL = "https://" + HOSTNAME + ":" + PORT + "/api";
    protected static final String TRUSTSTORE_RESOURCE = "classpath:test-data/mock-server/root-ca.jks";
    protected static final String TRUSTSTORE_PASSWORD = "password";

    // Create these only once to make the tests faster
    protected static CertificateAndKey deviceIssuer;
    protected static CertificateAndKey licenseIssuer;

    protected SystemKey systemKey;
    protected String registrationPropertiesFileName;
    protected String licenseKeyFileName;
    protected String licenseCertFileName;
    protected String truststoreCopyFileName;

    protected String registrationUrl;
    protected String deviceUrl;
    protected String tosUrl;
    protected String mobileConnectionCheckUrl;
    protected String mobileDnsCheckUrl;
    protected String trustStoreResource;
    protected String trustStorePassword;

    protected SettingsService settingsService;

    private ClientAndServer mockServer;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeClass
    public static void setup() throws IOException, CryptoException {
        deviceIssuer = PKI.generateRoot(ORG_NAME, "Device Root A", 10, KEY_SIZE);
        licenseIssuer = PKI.generateRoot(ORG_NAME, "License Root X", 10, KEY_SIZE);
    }

    @Before
    public void init() throws IOException, ParseException {
        systemKey = new SystemKey(createResource("system.", ".key"));
        registrationPropertiesFileName = createResource("registration.", ".properties");
        licenseKeyFileName = createResource("license.", ".key");
        licenseCertFileName = createResource("license.", ".cert");
        truststoreCopyFileName = createResource("license.", ".truststore");

        registrationUrl = "/registration";
        deviceUrl = "/device";
        tosUrl = "/tos";
        mobileConnectionCheckUrl = "/mobile/tests";
        mobileDnsCheckUrl = "/mobile/dns";
        trustStoreResource = TRUSTSTORE_RESOURCE;
        trustStorePassword = TRUSTSTORE_PASSWORD;

        settingsService = Mockito.mock(SettingsService.class);
        when(settingsService.getLocale()).thenReturn(Locale.US);
        when(settingsService.getLocaleSettings()).thenReturn(new LocaleSettings(null, null, null, null, null));

        if (doStartMockServer()) {
            mockServer = ClientAndServer.startClientAndServer(1080);
        }
    }

    protected boolean doStartMockServer() {
        return false;
    }

    protected ClientAndServer getClientAndServer() {
        return mockServer;
    }

    @After
    public void shutDown() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    protected DeviceRegistrationProperties createDeviceRegistrationProperties() throws IOException, ParseException {
        return createDeviceRegistrationProperties(CPU_SERIAL);
    }

    protected DeviceRegistrationProperties createDeviceRegistrationProperties(String cpuSerial) throws IOException, ParseException {
        CpuInfo cpuInfo = Mockito.mock(CpuInfo.class);
        when(cpuInfo.getSerial()).thenReturn(cpuSerial);

        DeviceRegistrationLicenseState licenseState = Mockito.mock(DeviceRegistrationLicenseState.class);

        return new DeviceRegistrationProperties(
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
                licenseState);
    }

    protected DeviceRegistrationClient createDeviceRegistrationClient() throws IOException, ParseException {
        return createDeviceRegistrationClient(createDeviceRegistrationProperties());
    }

    protected DeviceRegistrationClient createDeviceRegistrationClient(DeviceRegistrationProperties deviceRegistrationProperties) {
        return new DeviceRegistrationClient(
                BASE_URL, registrationUrl,
                BASE_URL, deviceUrl,
                BASE_URL, tosUrl,
                true,
                true,
                trustStoreResource,
                trustStorePassword,
                5000,
                5000,
                BASE_URL,
                mobileConnectionCheckUrl,
                "",
                mobileDnsCheckUrl,
                deviceRegistrationProperties,
                settingsService,
                new ObjectMapper(),
                VERSION
        );
    }

    protected DeviceRegistrationResponse simulateBackend(DeviceRegistrationRequest deviceRegistrationRequest, int licenseValidty, boolean autoRenewal) throws CertificateException, CryptoException {
        X509Certificate deviceCertificate = PKI.generateSignedCertificate(
                decode(deviceRegistrationRequest.getEncodedDeviceCertificate()),
                ORG_NAME,
                deviceRegistrationRequest.getDeviceId(),
                DateUtil.addYears(new Date(), 100),
                deviceIssuer
        );
        byte[] licenseCertificate;
        LicenseType licenseType;
        if (licenseValidty > 0) {
            licenseCertificate = PKI.generateSignedCertificate(
                    decode(deviceRegistrationRequest.getEncodedLicenseCertificate()),
                    ORG_NAME,
                    deviceRegistrationRequest.getDeviceId(),
                    DateUtil.addYears(new Date(), licenseValidty),
                    licenseIssuer
            ).getEncoded();
            licenseType = LicenseType.SUBSCRIPTION;
        } else {
            licenseCertificate = null;
            licenseType = LicenseType.COMMUNITY;
        }

        return new DeviceRegistrationResponse(
                deviceRegistrationRequest.getEmailAddress(),
                deviceRegistrationRequest.getDeviceName(),
                deviceRegistrationRequest.getDeviceId(),
                deviceCertificate.getEncoded(),
                licenseCertificate,
                licenseType.toString(),
                Boolean.toString(autoRenewal),
                null,
            null,
            null
        );
    }

    protected X509Certificate decode(byte[] encoded) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(encoded));
    }

    private String createResource(String prefix, String postfix) {
        try {
            Path path = Files.createTempFile(prefix, postfix);
            Files.delete(path);
            return path.toString();
        } catch (IOException e) {
            log.error("Cannot create resource file: "+e.getMessage());
            throw new IllegalArgumentException("Cannot create resource file: "+e.getMessage(), e);
        }
    }

    protected String jsonify(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Cannot deserialize object " + object, e);
            return null;
        }
    }

}
