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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.keys.KeyWrapper;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.LicenseType;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.CpuInfo;
import org.eblocker.server.common.util.DateUtil;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

public class DeviceRegistrationProperties {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistrationProperties.class);

    private static final String DEVICE_KEY_ALIAS = "eBlocker Device Key";
    private static final String LICENSE_KEY_ALIAS = "eBlocker License Key";
    private static final int MIN_ALLOWED_REG_TYPE = 0;

    private static final String PROP_REG_STATE = "registrationState";
    private static final String PROP_REG_ID = "registrationId";
    private static final String PROP_REG_TYPE = "registrationType";
    private static final String PROP_REG_CPU_SERIAL = "registrationCpuSerial";

    private static final String PROP_DEVICE_ID = "deviceId";
    private static final String PROP_DEVICE_NAME = "deviceName";
    private static final String PROP_DEVICE_CRED = "deviceCredentials";
    private static final String PROP_DEVICE_REG_AT = "deviceRegisteredAt";
    private static final String PROP_DEVICE_REG_BY = "deviceRegisteredBy";

    private static final String PROP_LICENSE_TYPE = "licenseType";
    private static final String PROP_LICENSE_NVA = "licenseNotValidAfter";
    private static final String PROP_LICENSE_AUTO = "licenseAutoRenewal";
    private static final String PROP_LICENSE_CRED = "licenseCredentials";

    private static final String PROP_TOS_VERSION = "tosVersion";

    private static final String PROP_COMMENTS = " AUTO-GENERATED - DO NOT EDIT! - EBLOCKER WILL NOT RUN WITH INVALID CONFIGURATION!";

    private final char[] password;
    private final EblockerResource registrationProperties;
    private final EblockerResource licenseKey;
    private final EblockerResource licenseCert;
    private final EblockerResource truststore;
    private final char[] truststorePassword;
    private final EblockerResource truststoreCopy;
    private final int keySize;
    private final CpuInfo cpuInfo;
    private final int defaultRegistrationType;
    private final int warningPeriodDays;
    private final Date lifetimeIndicator;
    private final Path tmpDir;

    private RegistrationData registration;

    private String deviceId;
    private String deviceName;
    private String deviceCredentials; // Base64 encoded JKS keystore, protected with hex-encoded systemKey as password
    private CertificateAndKey decodedDeviceCredentials;
    private Date deviceRegisteredAt;
    private String deviceRegisteredBy;

    private LicenseType licenseType;
    private Date licenseNotValidAfter;
    private boolean licenseAutoRenewal;
    private String licenseCredentials; // Base64 encoded JKS keystore, protected with hex-encoded systemKey as password
    private CertificateAndKey decodedLicenseCredentials;
    private DeviceRegistrationLicenseState licenseState;

    private String tosVersion;
    private DateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");

    @Inject
    public DeviceRegistrationProperties(@Named("systemKey") KeyWrapper systemKey,
                                        @Named("registration.properties") String registrationProperties,
                                        @Named("registration.license.key") String licenseKey,
                                        @Named("registration.license.cert") String licenseCert,
                                        @Named("registration.truststore.resource") String truststore,
                                        @Named("registration.truststore.password") String truststorePassword,
                                        @Named("registration.truststore.copy") String truststoreCopy,
                                        @Named("registration.keySize") int keySize,
                                        @Named("registration.default.type") int defaultRegistrationType,
                                        @Named("registration.warning.period") int warningPeriodDays,
                                        @Named("registration.lifetime.indicator") String lifetimeIndicator,
                                        @Named("tmpDir") String tmpDir,
                                        CpuInfo cpuInfo,
                                        DeviceRegistrationLicenseState licenseState) throws ParseException {
        this.password = Hex.encodeHex(systemKey.get());
        this.registrationProperties = new SimpleResource(registrationProperties);
        this.licenseKey = new SimpleResource(licenseKey);
        this.licenseCert = new SimpleResource(licenseCert);
        this.truststore = new SimpleResource(truststore);
        this.truststorePassword = truststorePassword.toCharArray();
        this.truststoreCopy = new SimpleResource(truststoreCopy);
        this.keySize = keySize;
        this.defaultRegistrationType = defaultRegistrationType;
        this.cpuInfo = cpuInfo;
        this.warningPeriodDays = warningPeriodDays;
        this.lifetimeIndicator = format.parse(lifetimeIndicator);
        this.tmpDir = Paths.get(tmpDir);
        this.licenseState = licenseState;

        init();
    }

    private void init() {
        log.info("Initialising device registration handler");
        if (ResourceHandler.exists(registrationProperties) && load()) {
            if (registration.hasBeenRegisteredBefore()) {
                verify();
            }
        } else {
            reset();
        }
        makeLicenseCredentialsUnavailable();
    }

    public void reset() {
        log.info("Reset registration configuration in file {}", registrationProperties.getPath());

        RegistrationData newRegistration = new RegistrationData();
        newRegistration.state = RegistrationState.NEW;
        newRegistration.type = defaultRegistrationType;
        newRegistration.id = UUID.randomUUID();
        newRegistration.cpuSerial = cpuInfo.getSerial();

        String newDeviceId = newRegistration.generateDeviceId();

        try {
            decodedDeviceCredentials = PKI.generateSelfSignedCertificateRequest(newDeviceId, keySize);
            deviceCredentials = Base64.encodeBase64String(encodeKeyStore(decodedDeviceCredentials, DEVICE_KEY_ALIAS));

            decodedLicenseCredentials = PKI.generateSelfSignedCertificateRequest(newDeviceId, keySize);
            licenseCredentials = Base64.encodeBase64String(encodeKeyStore(decodedLicenseCredentials, LICENSE_KEY_ALIAS));

        } catch (CryptoException | IOException e) {
            String msg = "Cannot generate initial device keys: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }

        deviceId = newDeviceId;
        deviceRegisteredAt = null;
        deviceRegisteredBy = null;
        deviceName = null;
        licenseType = LicenseType.NONE;
        licenseNotValidAfter = null;
        licenseAutoRenewal = false;
        tosVersion = null;
        registration = newRegistration;

        store();
    }

    private boolean load() {
        try {
            doLoad();
            return true;

        } catch (Exception e) {
            String msg = "Cannot read registration properties, purging all registration data: " + e.getMessage();
            log.warn(msg, e);
            return false;

        }
    }

    private void doLoad() {
        log.info("Loading registration configuration from file " + registrationProperties.getPath());
        Properties properties = new Properties();
        try (InputStream in = ResourceHandler.getInputStream(registrationProperties)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));

        } catch (IOException e) {
            String msg = "Cannot load registration properties: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }

        registration = new RegistrationData();
        registration.state = RegistrationState.valueOf(properties.getProperty(PROP_REG_STATE));
        registration.id = UUID.fromString(properties.getProperty(PROP_REG_ID));
        registration.type = Integer.valueOf(properties.getProperty(PROP_REG_TYPE));
        registration.cpuSerial = properties.getProperty(PROP_REG_CPU_SERIAL);

        deviceId = properties.getProperty(PROP_DEVICE_ID);
        deviceCredentials = properties.getProperty(PROP_DEVICE_CRED);
        decodedDeviceCredentials = decodeKeyStore(Base64.decodeBase64(deviceCredentials), DEVICE_KEY_ALIAS);

        licenseCredentials = properties.getProperty(PROP_LICENSE_CRED);
        decodedLicenseCredentials = decodeKeyStore(Base64.decodeBase64(licenseCredentials), LICENSE_KEY_ALIAS);
        licenseType = LicenseType.valueOf(properties.getProperty(PROP_LICENSE_TYPE));

        tosVersion = properties.getProperty(PROP_TOS_VERSION, null);

        if (registration.hasBeenRegisteredBefore()) {
            deviceRegisteredBy = properties.getProperty(PROP_DEVICE_REG_BY);
            deviceRegisteredAt = decodedDeviceCredentials.getCertificate().getNotBefore();
            deviceName = properties.getProperty(PROP_DEVICE_NAME);
            if (licenseType.isSubscription()) {
                licenseNotValidAfter = decodedLicenseCredentials.getCertificate().getNotAfter();
                licenseAutoRenewal = Boolean.valueOf(properties.getProperty(PROP_LICENSE_AUTO));
            }
        } else if (isFallbackRegistration()) {
            deviceName = properties.getProperty(PROP_DEVICE_NAME);
        }
    }

    private void store() {
        Properties properties = new Properties();

        properties.setProperty(PROP_REG_STATE, registration.state.toString());
        properties.setProperty(PROP_REG_TYPE, Integer.toString(registration.type));
        properties.setProperty(PROP_REG_ID, registration.id.toString());
        properties.setProperty(PROP_REG_CPU_SERIAL, registration.cpuSerial);

        properties.setProperty(PROP_DEVICE_ID, deviceId);
        properties.setProperty(PROP_DEVICE_CRED, deviceCredentials);
        if (registration.hasBeenRegisteredBefore()) {
            properties.setProperty(PROP_DEVICE_NAME, deviceName);
            properties.setProperty(PROP_DEVICE_REG_AT, format.format(deviceRegisteredAt));
            properties.setProperty(PROP_DEVICE_REG_BY, deviceRegisteredBy);
        } else if (isFallbackRegistration()) {
            properties.setProperty(PROP_DEVICE_NAME, deviceName);
        }

        properties.setProperty(PROP_LICENSE_CRED, licenseCredentials);
        properties.setProperty(PROP_LICENSE_TYPE, licenseType.toString());
        if (registration.hasBeenRegisteredBefore()) {
            if (licenseType.isSubscription()) {
                properties.setProperty(PROP_LICENSE_NVA, format.format(licenseNotValidAfter));
                properties.setProperty(PROP_LICENSE_AUTO, Boolean.toString(licenseAutoRenewal));
            }
        }

        if (tosVersion != null) {
            properties.setProperty(PROP_TOS_VERSION, tosVersion);
        }

        try {
            Path temp = Files.createTempFile(tmpDir, "registration.", ".properties");
            try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
                properties.store(writer, PROP_COMMENTS);
            }
            ResourceHandler.replaceContent(registrationProperties, temp);
        } catch (IOException e) {
            String msg = "Cannot create temp file to store new registration properties: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    private void verify() {
        RegistrationState oldRegistrationState = registration.state;
        registration.state = verifiedRegistrationState();
        if (oldRegistrationState != registration.state) {
            store();
        }
    }

    private RegistrationState verifiedRegistrationState() {
        if (deviceName == null) {
            return RegistrationState.NEW;
        }

        if (registration.state == RegistrationState.OK_UNREGISTERED) {
            return RegistrationState.OK_UNREGISTERED;
        }

        if (registration.type < MIN_ALLOWED_REG_TYPE) {
            return RegistrationState.INVALID;
        }

        if (!registration.cpuSerial.equals(cpuInfo.getSerial())) {
            return RegistrationState.INVALID;
        }

        if (!deviceId.equals(registration.generateDeviceId())) {
            return RegistrationState.INVALID;
        }

        if (!isExpired() && registration.state == RegistrationState.REVOKED) {
            return RegistrationState.REVOKED;
        }

        return RegistrationState.OK;
    }

    private byte[] encodeKeyStore(CertificateAndKey certificateAndKey, String alias) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PKI.generateKeyStore(certificateAndKey, alias, password, baos);
            return baos.toByteArray();

        } catch (CryptoException | IOException e) {
            String msg = "Cannot generate key store " + alias + " from key and certificate: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    private CertificateAndKey decodeKeyStore(byte[] encodedKeyStore, String alias) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(encodedKeyStore);
            return PKI.loadKeyStore(alias, bais, password);

        } catch (IOException | CryptoException e) {
            String msg = "Cannot decode key store '" + alias + "': " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    private KeyStore decodeKeyStore(byte[] encodedKeyStore) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(encodedKeyStore);
            return PKI.loadKeyStore(bais, password);

        } catch (IOException | CryptoException e) {
            String msg = "Cannot decode key store: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    private X509Certificate decodeCertificate(byte[] encodedCertificate) {
        try {
            try (InputStream in = new ByteArrayInputStream(encodedCertificate)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509Certificate) cf.generateCertificate(in);
            }
        } catch (IOException | CertificateException e) {
            String msg = "Cannot decode certificate: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }


    private boolean isFallbackRegistration() {
        return registration.state == RegistrationState.OK_UNREGISTERED;
    }

    public boolean isSubscriptionValid() {
        if (!registration.hasBeenRegisteredBefore()) {
            // Unregistered device can never have a valid subscription
            return false;
        }

        if (registration.state != RegistrationState.OK) {
            return false;
        }

        if (!licenseType.isSubscription()) {
            return false;
        }

        if (isExpired()) {
            return false;
        }

        String subject = decodedLicenseCredentials.getCertificate().getSubjectX500Principal().getName();
        log.info("Subject of license certificate: " + subject);

        return subject.toLowerCase().contains(deviceId.toLowerCase());
    }

    private boolean isExpired() {
        return new Date().after(decodedLicenseCredentials.getCertificate().getNotAfter());
    }

    public DeviceRegistrationRequest generateRequest(String email, String deviceName, String licenseKey,
                                                     String serialNumber, Boolean isConfirmed, String tosVersion) {
        try {
            this.tosVersion = tosVersion;
            return new DeviceRegistrationRequest(
                    email, deviceName, licenseKey, deviceId, serialNumber,
                    decodedDeviceCredentials.getCertificate().getEncoded(),
                    decodedLicenseCredentials.getCertificate().getEncoded(),
                    isConfirmed,
                    tosVersion
            );

        } catch (CertificateEncodingException e) {
            String msg = "Cannot encode device certificate: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    /*
     * Checks and returns true if the license certificate has been revoked. Revocation is assumed when a certificate error occurs although the certificate is not expired.
     */
    public void acquireRevokationState() {
        try {
            if (!isExpired() && (registration.state == RegistrationState.OK || registration.state == RegistrationState.REVOKED)) {
                makeLicenseCredentialsAvailable();
                if (licenseState.checkCertificate() == RegistrationState.INVALID) {
                    registration.state = RegistrationState.REVOKED;
                } else {
                    registration.state = RegistrationState.OK;
                }
            }
        } catch (Exception e) {
            String msg = "Cannot verify license revocation state: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    public void processResponse(DeviceRegistrationResponse deviceRegistrationResponse) {
        X509Certificate deviceCertificate = decodeCertificate(deviceRegistrationResponse.getEncodedDeviceCertificate());
        X509Certificate licenseCertficate = null;
        if (deviceRegistrationResponse.getEncodedLicenseCertificate() != null) {
            licenseCertficate = decodeCertificate(deviceRegistrationResponse.getEncodedLicenseCertificate());
        }

        registration.state = RegistrationState.OK;
        // registrationId - unchanged
        // registrationType - unchanged
        // registrationMac - unchanged

        // deviceId - unchanged
        deviceName = deviceRegistrationResponse.getDeviceName();
        decodedDeviceCredentials = new CertificateAndKey(deviceCertificate, decodedDeviceCredentials.getKey());
        deviceCredentials = Base64.encodeBase64String(encodeKeyStore(decodedDeviceCredentials, DEVICE_KEY_ALIAS));
        deviceRegisteredAt = deviceCertificate.getNotBefore();
        deviceRegisteredBy = deviceRegistrationResponse.getEmailAddress();

        licenseType = LicenseType.getFailSafeValue(deviceRegistrationResponse.getLicenseType());
        if (licenseType.isSubscription()) {
            decodedLicenseCredentials = new CertificateAndKey(licenseCertficate, decodedLicenseCredentials.getKey());
            licenseCredentials = Base64.encodeBase64String(encodeKeyStore(decodedLicenseCredentials, LICENSE_KEY_ALIAS));
            licenseNotValidAfter = decodedLicenseCredentials.getCertificate().getNotAfter();
            licenseAutoRenewal = Boolean.valueOf(deviceRegistrationResponse.getAutoRenewal());
        } else {
            licenseNotValidAfter = decodedDeviceCredentials.getCertificate().getNotAfter();
        }
        store();
    }

    public void registrationFallback(String deviceName) {
        reset();
        registration.state = RegistrationState.OK_UNREGISTERED;
        this.deviceName = Strings.isNullOrEmpty(deviceName) ? "My eBlocker" : deviceName;
        store();
    }

    public void makeLicenseCredentialsAvailable() {
        if (isSubscriptionValid() || RegistrationState.REVOKED == registration.state) {
            try {
                Path temp = Files.createTempFile(tmpDir, "license.", ".cert");
                PKI.storeCertificate(decodedLicenseCredentials.getCertificate(), Files.newOutputStream(temp));
                ResourceHandler.replaceContent(licenseCert, temp);

            } catch (CryptoException | IOException e) {
                String msg = "Cannot create temp file to store new license certificate: " + e.getMessage();
                log.error(msg);
                throw new EblockerException(msg, e);
            }

            try {
                Path temp = Files.createTempFile(tmpDir, "license.", ".key");
                PKI.storePrivateKey(decodedLicenseCredentials.getKey(), Files.newOutputStream(temp));
                ResourceHandler.replaceContent(licenseKey, temp);

            } catch (CryptoException | IOException e) {
                String msg = "Cannot create temp file to store new license key: " + e.getMessage();
                log.error(msg);
                throw new EblockerException(msg, e);
            }

            try {
                Path temp = Files.createTempFile(tmpDir, "license.", ".truststore");
                X509Certificate[] certificates = PKI.loadTrustStore(ResourceHandler.getInputStream(truststore), truststorePassword);
                try (OutputStream out = Files.newOutputStream(temp)) {
                    PKI.storeCertificates(certificates, out);
                }
                ResourceHandler.replaceContent(truststoreCopy, temp);
            } catch (CryptoException | IOException e) {
                String msg = "Cannot create temp file to store copy of truststore: " + e.getMessage();
                log.error(msg);
                throw new EblockerException(msg, e);
            }
        }
    }

    public void makeLicenseCredentialsUnavailable() {
        try {
            Path temp = Files.createTempFile(tmpDir, "license.", ".cert");
            Files.write(temp, "-unavailable-".getBytes());
            ResourceHandler.replaceContent(licenseCert, temp);

        } catch (IOException e) {
            String msg = "Cannot create temp file to store new license certificate: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }

        try {
            Path temp = Files.createTempFile(tmpDir, "license.", ".key");
            Files.write(temp, "-unavailable-".getBytes());
            ResourceHandler.replaceContent(licenseKey, temp);

        } catch (IOException e) {
            String msg = "Cannot create temp file to store new license key: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
    }

    public RegistrationState getRegistrationState() {
        registration.state = verifiedRegistrationState();
        return registration.state;
    }

    public Date getDeviceRegisteredAt() {
        return deviceRegisteredAt;
    }

    public String getDeviceRegisteredBy() {
        return deviceRegisteredBy;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public LicenseType getLicenseType() {
        return licenseType;
    }

    public Date getLicenseNotValidAfter() {
        return licenseNotValidAfter;
    }

    public boolean isLicenseAutoRenewal() {
        return licenseAutoRenewal;
    }

    public boolean isLicenseAboutToExpire() {
        if (licenseNotValidAfter == null) {
            return false;
        }
        return DateUtil.isBeforeDays(licenseNotValidAfter, new Date(), warningPeriodDays);
    }

    public boolean isLicenseExpired() {
        if (licenseNotValidAfter == null) {
            return false;
        }
        return (licenseNotValidAfter.getTime() - new Date().getTime() <= 0);
    }

    public Long getLicenseRemainingDays() {
        if (licenseNotValidAfter == null) {
            return null;
        }
        return (licenseNotValidAfter.getTime() - new Date().getTime()) / 86400000;
    }

    public boolean isLicenseLifetime() {
        if (licenseNotValidAfter == null) {
            return false;
        }
        return !licenseNotValidAfter.before(lifetimeIndicator);
    }

    public X509Certificate getDeviceCertificate() {
        if (!registration.hasBeenRegisteredBefore() || decodedDeviceCredentials == null) {
            return null;
        }
        return decodedDeviceCredentials.getCertificate();
    }

    public X509Certificate getLicenseCertificate() {
        if (!registration.hasBeenRegisteredBefore() || !licenseType.isSubscription() || decodedLicenseCredentials == null) {
            return null;
        }
        return decodedLicenseCredentials.getCertificate();
    }

    public KeyStore getDeviceKeyStore() {
        if (!registration.hasBeenRegisteredBefore() || deviceCredentials == null) {
            return null;
        }
        return decodeKeyStore(Base64.decodeBase64(deviceCredentials));
    }

    public KeyStore getLicenseKeyStore() {
        if (!registration.hasBeenRegisteredBefore() || !licenseType.isSubscription() || licenseCredentials == null) {
            return null;
        }
        return decodeKeyStore(Base64.decodeBase64(licenseCredentials));
    }

    public char[] getPassword() {
        return password;
    }

    private static class RegistrationData {
        private RegistrationState state;
        private int type;
        private UUID id;
        private String cpuSerial;

        private boolean hasBeenRegisteredBefore() {
            return state != RegistrationState.NEW && state != RegistrationState.OK_UNREGISTERED;
        }

        private String generateDeviceId() {
            StringBuilder normalized = new StringBuilder();
            switch (type) {

                case 0:
                    normalized.append(id.toString());
                    normalized.append("::");
                    normalized.append("LH4ylkeL!43fh@VVu!#3LZLl2%F#1Stn");
                    break;

                case 1:
                    normalized.append(id.toString());
                    normalized.append("::");
                    normalized.append(cpuSerial);
                    normalized.append("::");
                    normalized.append("j6Ko%!5OhEG17@6NuPNJjqkTVyzRIX9z");
                    break;

                default:
                    // This should never generate a valid, verifiable registration ID
                    normalized.append(UUID.randomUUID().toString());
            }

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(normalized.toString().getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();
                return Hex.encodeHexString(digest);

            } catch (NoSuchAlgorithmException e) {
                String msg = "Cannot generate registration ID: " + e.getMessage();
                log.error(msg);
                throw new EblockerException(msg, e);
            }
        }

    }
}
