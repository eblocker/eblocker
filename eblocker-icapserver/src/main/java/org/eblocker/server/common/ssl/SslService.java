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
package org.eblocker.server.common.ssl;

import org.eblocker.server.common.data.CaOptions;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DistinguishedName;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.utils.NormalizationUtils;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
@SubSystemService(value = SubSystem.HTTPS_SERVER, initPriority = 100)
public class SslService {
    private static final Logger log = LoggerFactory.getLogger(SslService.class);

    private final String keyStorePath;
    private final String renewalKeyStorePath;
    private final char[] keyStorePassword;
    private final int maxCaValidityInMonths;
    private final int caKeySize;
    private final String caCertDnFormat;
    private final int caRenewWeeks;

    private final DataSource dataSource;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final ScheduledExecutorService executorService;

    private boolean initialized;
    private Boolean sslEnabled;
    private List<SslStateListener> listeners = new ArrayList<>();
    private List<ScheduledFuture<?>> futures = new ArrayList<>();

    private EblockerCa ca;
    private EblockerCa renewalCa;
    private final int sslCertificateFriendlyNameLength;
    private final String sslCertificateFriendlyNameFallback;

    @Inject
    public SslService(@Named("ca.keystore.path") String keyStorePath,
                      @Named("ca.keystore.password") String keyStorePassword,
                      @Named("ca.cert.max.validity.months") int maxCaValidityInMonths,
                      // TODO: rename
                      @Named("ca.cert.dn.format") String caCertDnFormat,
                      @Named("ca.key.size") int caKeySize,
                      @Named("ca.renew.weeks") int caRenewWeeks,
                      @Named("ca.renewal.keystore.path") String renewalKeyStorePath,
                      DataSource dataSource,
                      DeviceRegistrationProperties deviceRegistrationProperties,
                      @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                      @Named("ssl.certificate.friendlyname.length") int sslCertificateFriendlyNameLength,
                      @Named("ssl.certificate.friendlyname.fallback") String sslCertificateFriendlyNameFallback) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.maxCaValidityInMonths = maxCaValidityInMonths;
        this.caCertDnFormat = caCertDnFormat;
        this.caKeySize = caKeySize;
        this.caRenewWeeks = caRenewWeeks;
        this.renewalKeyStorePath = renewalKeyStorePath;
        this.dataSource = dataSource;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.executorService = executorService;
        this.sslCertificateFriendlyNameLength = sslCertificateFriendlyNameLength;
        this.sslCertificateFriendlyNameFallback = sslCertificateFriendlyNameFallback;
    }

    @SubSystemInit
    public void init() throws PkiException {
        if (initialized) {
            throw new IllegalStateException("service has already been initialized");
        }

        sslEnabled = dataSource.getSSLEnabledState();
        ca = loadCa(keyStorePath, keyStorePassword);
        renewalCa = loadCa(renewalKeyStorePath, keyStorePassword);

        if (sslEnabled && ca == null) {
            throw new PkiException("ssl is enabled but no keypair available!");
        }

        checkCaExpiration();
        initialized = true;
        listeners.forEach(l -> l.onInit(sslEnabled));
    }

    public void addListener(SslStateListener listener) {
        listeners.add(listener);
    }

    public boolean isSslEnabled() {
        requireInitialization();
        return sslEnabled;
    }

    public void enableSsl() {
        requireInitialization();
        //save new SSL state
        sslEnabled = true;
        dataSource.setSSLEnabledState(sslEnabled);
        listeners.forEach(SslStateListener::onEnable);
    }

    public void disableSsl() {
        requireInitialization();
        sslEnabled = false;
        dataSource.setSSLEnabledState(sslEnabled);
        listeners.forEach(SslStateListener::onDisable);
    }

    public EblockerCa getCa() {
        requireInitialization();
        return ca;
    }

    public boolean isCaAvailable() {
        requireInitialization();
        return ca != null;
    }

    public EblockerCa getRenewalCa() {
        requireInitialization();
        return renewalCa;
    }

    public boolean isRenewalCaAvailable() {
        requireInitialization();
        return renewalCa != null;
    }

    public CaOptions getDefaultCaOptions() {
        CaOptions options = new CaOptions();

        // this should be configurable
        options.setValidityInMonths(maxCaValidityInMonths);

        // create device name
        String deviceName = deviceRegistrationProperties.getDeviceName();
        if (StringUtils.isBlank(deviceName)) {
            deviceName = "My eBlocker";  // TODO: is there any other source available for this?
        }
        DistinguishedName dn = new DistinguishedName();
        dn.setCommonName(deviceName);
        options.setDistinguishedName(dn);

        return options;
    }

    public void generateCa(CaOptions options) throws PkiException {
        requireInitialization();
        ca = generateCa(options.getDistinguishedName().getCommonName(), options.getValidityInMonths(), keyStorePath, keyStorePassword);
        listeners.forEach(SslStateListener::onCaChange);
        dataSource.save(options);
        disableRenewalCa();
        checkCaExpiration();
    }

    public int getCaRenewWeeks() {
        return caRenewWeeks;
    }

    private EblockerCa generateCa(String optionsCommonName, int validityInMonths, String keyStorePath, char[] keyStorePassword) throws PkiException {
        if (validityInMonths > maxCaValidityInMonths) {
            log.warn("Generating CA certificate requested validity of {} months but maximum is {}.", validityInMonths, maxCaValidityInMonths);
            throw new PkiException("Maximum CA validity exceeded");
        }

        // TODO: name implies we are going to use more than common name, refactor to common name alone
        // adjust cn to format
        String[] date = DateTimeFormatter.ISO_DATE.format(LocalDateTime.now()).split("-");
        String commonName = String.format(caCertDnFormat, optionsCommonName, date[0], date[1], date[2]);

        ZonedDateTime notBefore = ZonedDateTime.now();
        ZonedDateTime notAfter = notBefore.plus(validityInMonths, ChronoUnit.MONTHS);
        long daysValid = ChronoUnit.DAYS.between(notBefore, notAfter);
        log.info("Generating CA certificate for {} which is valid for {} days...", commonName, daysValid);
        try {
            CertificateAndKey certificateAndKey = PKI.generateRoot(null,
                commonName,
                Date.from(notBefore.toInstant()),
                Date.from(notAfter.toInstant()),
                caKeySize);

            try (FileOutputStream keyStoreStream = new FileOutputStream(keyStorePath)) {
                PKI.generateKeyStore(certificateAndKey, "root", keyStorePassword, keyStoreStream);
            }

            return new EblockerCa(certificateAndKey);
        } catch (CryptoException | IOException e ) {
            throw new PkiException("ca generation failed " + e.getMessage(), e);
        }
    }

    public String generateFileNameForCertificate(){
        // Collect information to include in the filename
        // Validity
        Date notBefore = ca.getCertificate().getNotBefore();
        String dateString = new SimpleDateFormat("yyyy-MM-dd").format(notBefore);

        // Name
        CaOptions caOptions = dataSource.get(CaOptions.class);
        String cname="";
        if (caOptions == null || caOptions.getDistinguishedName() == null
                || StringUtils.isBlank(caOptions.getDistinguishedName().getCommonName())) {
            // Fallback
            cname = deviceRegistrationProperties.getDeviceName();
        } else {
            // Name supplied by user during creation of certificate
            cname = caOptions.getDistinguishedName().getCommonName();
        }
        // If name not present, fall back
        if (StringUtils.isBlank(cname)) {
            return "eBlocker-Certificate-" + dateString + ".crt";
        }
        String cleanCname = NormalizationUtils.normalizeStringForFilename(cname, sslCertificateFriendlyNameLength, "");
        return sslCertificateFriendlyNameFallback + "-" + cleanCname + "-" + dateString + ".crt";
    }

    private void disableRenewalCa() throws PkiException {
        try {
            if (renewalCa != null) {
                log.debug("deleting previously generated renewal ca");
                renewalCa = null;
                Files.deleteIfExists(Paths.get(renewalKeyStorePath));
            }
            log.debug("canceling all scheduled tasks for ca expiration");
            futures.forEach(f -> f.cancel(false));
        } catch (IOException e) {
            throw new PkiException("failed to delete obsolete renewal ca", e);
        }
    }

    private void checkCaExpiration() {
        if (ca == null) {
            return;
        }

        if (ca.getCertificate().getNotAfter().getTime() <= System.currentTimeMillis()) {
            handleCaExpiration();
        } else {
            scheduleExpirationTasks();
        }
    }

    private void scheduleExpirationTasks() {
        ZonedDateTime notAfter = ZonedDateTime.ofInstant(ca.getCertificate().getNotAfter().toInstant(), ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now();
        Duration duration = Duration.between(now, notAfter);

        if (renewalCa == null) {
            ZonedDateTime prepareCaExpiration = notAfter.minus(caRenewWeeks, ChronoUnit.WEEKS);
            Duration delay = Duration.between(now, prepareCaExpiration);
            futures.add(executorService.schedule(this::generateRenewalCa, delay.toMinutes(), TimeUnit.MINUTES));
            log.info("scheduled renewal ca generation in {} days", delay.toDays());
        }
        futures.add(executorService.schedule(this::handleCaExpiration, duration.toMillis(), TimeUnit.MILLISECONDS));
    }

    private void generateRenewalCa() {
        try {
            log.debug("generating renewal ca");
            renewalCa = regenerateCa(renewalKeyStorePath, keyStorePassword);
            listeners.forEach(l -> l.onRenewalCaChange());
        } catch (PkiException e) {
            log.error("failed to generate renewal ca", e);
        }
    }

    private void handleCaExpiration() {
        try {
            log.info("ca is expired");
            if (renewalCa == null) {
                log.warn("no previously renewed ca has been generated, generating one now!");
                ca = regenerateCa(keyStorePath, keyStorePassword);
            } else {
                log.info("replacing expired ca with previously generated one");
                ca = renewalCa;
                Files.move(Paths.get(renewalKeyStorePath), Paths.get(keyStorePath), StandardCopyOption.REPLACE_EXISTING);
            }

            disableRenewalCa();

            // suppress callbacks if we handle expiration on init
            if (initialized) {
                listeners.forEach(SslStateListener::onCaChange);
                listeners.forEach(l -> l.onRenewalCaChange());
            }
            scheduleExpirationTasks();
        } catch (PkiException e) {
            log.error("failed to regenerate ca", e);
        } catch (IOException e) {
            log.error("failed to replace current ca storage with new one", e);
        }
    }

    private EblockerCa regenerateCa(String keyStorePath, char[] keyStorePassword) throws PkiException {
        // use previously used options or default ones if not available
        CaOptions caOptions = dataSource.get(CaOptions.class);
        if (caOptions == null) {
            log.debug("no previously used options available, using defaults.");
            caOptions = getDefaultCaOptions();
        }

        return generateCa(caOptions.getDistinguishedName().getCommonName(), caOptions.getValidityInMonths(), keyStorePath, keyStorePassword);
    }

    private EblockerCa loadCa(String keyStorePath, char[] keyStorePassword) throws PkiException {
        if (!Files.exists(Paths.get(keyStorePath))) {
            return null;
        }

        try (FileInputStream keyStoreStream = new FileInputStream(keyStorePath)) {
            KeyStore keyStore = PKI.loadKeyStore(keyStoreStream, keyStorePassword);
            String alias = keyStore.aliases().nextElement();
            return new EblockerCa(new CertificateAndKey((X509Certificate) keyStore.getCertificate(alias),
                (PrivateKey) keyStore.getKey(alias, keyStorePassword)));
        } catch (CryptoException | IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new PkiException("failed to load ca keystore", e);
        }
    }

    private void requireInitialization() {
        if (!initialized) {
            throw new IllegalStateException("method call not allowed on uninitialized service");
        }
    }

    public class PkiException extends Exception {
        public PkiException(String message) {
            super(message);
        }

        private PkiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public interface SslStateListener {
        void onInit(boolean sslEnabled);
        void onCaChange();
        void onEnable();
        void onDisable();
        void onRenewalCaChange();
    }

    public static class BaseStateListener implements SslStateListener {

        @Override
        public void onInit(boolean sslEnabled) {
        }

        @Override
        public void onCaChange() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public void onRenewalCaChange() {
        }
    }

}
