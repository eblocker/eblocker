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
package org.eblocker.server.http.server;

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**This class handles the creation of the signed SSL certificate for the ICAP server
 * It is able to return a SSLContext for the RestExpress server
 * You must call the init() method, to make sure the certificates are available, before
 * making use of other methods of this class.
 *
 * Implemented regeneration of ssl certificate, when DhcpBindListener observes new IP address
 * -> generateSSLCertificate(newIP,...) and set new SSLContext (-> getSSLContext(...)) to EblockerHttpsServer
 *
 */
@SubSystemService(SubSystem.HTTPS_SERVER)
public class SSLContextHandler {
    private static final Logger log = LoggerFactory.getLogger(SSLContextHandler.class);
    public static final int MAX_VALIDITY_IN_DAYS = 825;

    private final String keyStorePath;
    private final String renewalKeyStorePath;
    private final char[] keyStorePassword;
    private final String controlBarHostName;
    private final String emergencyIp;
    private final List<String> defaultLocalNames;
    private final NetworkInterfaceWrapper networkInterface;
    private final SslService sslService;

    private List<SslContextChangeListener> listeners = new ArrayList<>();

    @Inject
    public SSLContextHandler(@Named("network.control.bar.host.name") String controlBarHostName,
                             @Named("network.emergency.ip") String emergencyIp,
                             @Named("dns.server.default.local.names") String defaultLocalNames,
                             @Named("icapserver.keystore.path") String keyStorePath,
                             @Named("icapserver.keystore.password") String keyStorePassword,
                             @Named("icapserver.renewal.keystore.path") String renewalKeyStorePath,
                             NetworkInterfaceWrapper networkInterface,
                             SslService sslService) {
        this.controlBarHostName = controlBarHostName;
        this.emergencyIp = emergencyIp;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.renewalKeyStorePath = renewalKeyStorePath;

        Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
        this.defaultLocalNames = splitter.splitToList(defaultLocalNames);

        this.networkInterface = networkInterface;
        this.sslService = sslService;

        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                try {
                    // check and regenerate certificates if necessary
                    checkCertificates();
                } catch (SslContextException e) {
                    log.error("Failed to initialize context - ssl may be unavailable", e);
                }
            }

            @Override
            public void onCaChange() {
                try {
                    checkCertificates();
                } catch (SslContextException e) {
                    log.error("failed to generate new ssl certificate", e);
                }
            }

            @Override
            public void onRenewalCaChange() {
                try {
                    checkCertificates();
                } catch (SslContextException e) {
                    log.error("failed to generate new ssl certificate", e);
                }
            }

            @Override
            public void onEnable() {
                try {
                    checkCertificates();
                } catch (SslContextException e) {
                    log.error("failed to enable ssl", e);
                }
            }

            @Override
            public void onDisable() {
                listeners.forEach(SslContextChangeListener::onDisable);
            }
        });

        networkInterface.addIpAddressChangeListener(ip -> notifyIpChange());
    }

    public void addContextChangeListener(SslContextChangeListener listener) {
        listeners.add(listener);
    }

    public SSLContext getSSLContext() {
        return createSslContext(keyStorePath);
    }

    public SSLContext getRenewalSSLContext() {
        return createSslContext(renewalKeyStorePath);
    }

    /**
     * Generate a new signed SSL certificate with current IP address and return the SSL context for this new certificate
     * @return
     */
    private void notifyIpChange(){
        try {
            log.info("Updating the SSL Context...");
            if(sslService.isCaAvailable()) {
                generateSSLCertificate(sslService.getCa(), keyStorePath);
            }
            if (sslService.isSslEnabled()) {
                listeners.forEach(SslContextChangeListener::onEnable);
            }
        } catch (Exception e) {
            log.error("Error while updating SSL Context",e);
        }
    }

    private void checkCertificates() throws SslContextException {
        if (!sslService.isCaAvailable()) {
            log.info("no eblocker ca available");
            listeners.forEach(SslContextChangeListener::onDisable);
            return;
        }

        // generate eBlocker certificates if they doesn't exist or ip doesn't match anymore
        checkEblockerCertificates();

        if (sslService.isSslEnabled()) {
            listeners.forEach(SslContextChangeListener::onEnable);
        } else {
            listeners.forEach(SslContextChangeListener::onDisable);
        }
    }

    private void checkEblockerCertificates() throws SslContextException {
        if (!checkEblockerCertificate(sslService.getCa(), keyStorePath)) {
            if (checkEblockerCertificate(sslService.getCa(), renewalKeyStorePath)) {
                log.info("eBlocker server certificate rollover");
                try {
                    Files.move(Paths.get(renewalKeyStorePath), Paths.get(keyStorePath), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new SslContextException("certificate rollover failed", e);
                }
            } else {
                log.info("eBlocker server certificate missing or ip doesn't match, generating a new one.");
                generateSSLCertificate(sslService.getCa(), keyStorePath);
            }
        }

        if (sslService.isRenewalCaAvailable() && !checkEblockerCertificate(sslService.getRenewalCa(), renewalKeyStorePath)) {
            log.info("eBlocker server renewal certificate missing or ip doesn't match, generating a new one.");
            generateSSLCertificate(sslService.getRenewalCa(), renewalKeyStorePath);
        }
    }

    private boolean checkEblockerCertificate(EblockerCa issuer, String keyStorePath) throws SslContextException {
        EblockerResource eBlockerCertificateResource = new SimpleResource(keyStorePath);
        if (!ResourceHandler.exists(eBlockerCertificateResource)) {
            return false;
        }

        X509Certificate certificate = getFirstEntryFromKeyStore(eBlockerCertificateResource);
        if (certificate == null) {
            return false;
        }

        if (!verifyCertificate(certificate, issuer.getCertificate())) {
            return false;
        }

        return checkEblockerCertificateCn(certificate) && checkEblockerCertificateSubjectAlternativeNames(certificate);
    }

    private boolean checkEblockerCertificateCn(X509Certificate certificate) throws SslContextException {
        try {
            String cn = PKI.getCN(certificate);
            log.debug("extracted cn from certificate: {}", cn);
            return controlBarHostName.equals(cn);
        } catch (CryptoException e) {
            throw new SslContextException("failed to extract cn", e);
        }
    }

    private boolean checkEblockerCertificateSubjectAlternativeNames(X509Certificate certificate) throws SslContextException {
        List<String> currentNames = getSubjectAlternativeNames();
        List<String> names = extractAltNames(certificate);
        return currentNames.equals(names);
    }

    private List<String> getSubjectAlternativeNames() {
        String currentIp = networkInterface.getFirstIPv4Address().toString();
        List<String> currentNames = new ArrayList<>();
        currentNames.add(controlBarHostName);
        currentNames.addAll(defaultLocalNames);
        currentNames.add(currentIp);
        currentNames.add(emergencyIp);

        IpAddress vpnIpAddress = networkInterface.getVpnIpv4Address();
        if (vpnIpAddress != null) {
            currentNames.add(vpnIpAddress.toString());
        }

        return new ArrayList<>(currentNames);
    }

    private boolean verifyCertificate(X509Certificate certificate, X509Certificate issuer) {
        try {
            certificate.checkValidity();
            certificate.verify(issuer.getPublicKey());
            return isValidityPeriodShorterThan(certificate, MAX_VALIDITY_IN_DAYS);
        } catch (GeneralSecurityException e) {
            log.debug("certificate validation failed", e);
            return false;
        }
    }

    private boolean isValidityPeriodShorterThan(X509Certificate certificate, int maxValidityInDays) {
        java.util.Date notBefore = certificate.getNotBefore();
        java.util.Date notAfter = certificate.getNotAfter();
        long certificateValidity = ChronoUnit.DAYS.between(notBefore.toInstant(), notAfter.toInstant());
        return certificateValidity < maxValidityInDays;
    }

    private X509Certificate getFirstEntryFromKeyStore(EblockerResource eBlockerCertificateResource) {
        try (InputStream keyStoreStream = ResourceHandler.getInputStream(eBlockerCertificateResource)) {
            KeyStore keyStore = PKI.loadKeyStore(keyStoreStream, keyStorePassword);
            String alias = keyStore.aliases().nextElement();
            return (X509Certificate) keyStore.getCertificate(alias);
        } catch (CryptoException | IOException | KeyStoreException e) {
            log.error("failed to retrieve first entry from key store", e);
            return null;
        }
    }

    private List<String> extractAltNames(X509Certificate certificate) throws SslContextException {
        try {
            List<String> altNames = certificate.getSubjectAlternativeNames().stream()
                .map(this::mapSubjectAlternativeName)
                .distinct()
                .collect(Collectors.toList());

            log.debug("certificates alt names:");
            altNames.stream().forEach(n->log.debug("    {}", n));

            return altNames;
        } catch (CertificateParsingException e) {
            throw new SslContextException("failed to extract alternative names", e);
        }
    }

    private String mapSubjectAlternativeName(List<?> altName) {
        Integer type = (Integer)altName.get(0);
        if (type == 2 || type == 7) {
            return (String) altName.get(1);
        }
       throw new IllegalStateException("unsupported alt name type " + type + " in certificate!");
    }

    private boolean generateSSLCertificate(EblockerCa ca, String keyStorePath) throws SslContextException {
        try {
            final IpAddress currentIPAddress = networkInterface.getFirstIPv4Address();

            log.info("Creating signed SSL certificate with currentIPAddress: {}", currentIPAddress);

            LocalDate notAfter = getNotAfter();
            long validDays = ChronoUnit.DAYS.between(LocalDate.now(), notAfter);
            log.info("root certificate is valid until {} ({} days)", notAfter, validDays);

            //start running the script (block until finished)
            log.info("Creating webserver certificate which is valid for: {} days", validDays);

            List<String> subjectAlternativeNames = new ArrayList<>(getSubjectAlternativeNames());
            CertificateAndKey certificateAndKey = ca.generateServerCertificate(controlBarHostName,
                Date.from(notAfter.atStartOfDay().atOffset(ZoneOffset.UTC).toInstant()),
                subjectAlternativeNames);

            try (FileOutputStream keyStoreStream = new FileOutputStream(keyStorePath)) {
                PKI.generateKeyStore(certificateAndKey, "https", keyStorePassword, keyStoreStream);
            }

            return true;
        } catch (IOException | CryptoException e) {
            throw new SslContextException("failed to generate webserver certificate", e);
        }
    }

    private LocalDate getNotAfter() {
        LocalDate caNotAfter = LocalDateTime.ofInstant(sslService.getCa().getCertificate().getNotAfter().toInstant(), ZoneId.systemDefault()).toLocalDate();
        int recommendedNotAfterInDays = MAX_VALIDITY_IN_DAYS - 1;
        LocalDate maxNotAfter = LocalDate.now().plusDays(recommendedNotAfterInDays);
        return caNotAfter.isAfter(maxNotAfter) ? maxNotAfter : caNotAfter;
    }

    private SSLContext createSslContext(String keyStorePath) {
        // check keystore existence
        SimpleResource keyStoreResource = new SimpleResource(keyStorePath);
        if(!ResourceHandler.exists(keyStoreResource)) {
            log.error("Keystore file does not exist here: {}", keyStorePath);
            return null;
        }

        // create context
        try (InputStream keyStoreStream = ResourceHandler.getInputStream(keyStoreResource)) {
            KeyStore keyStore = PKI.loadKeyStore(keyStoreStream, keyStorePassword);
            return createSslContext(keyStore, keyStorePassword);
        } catch (IOException | CryptoException | SslContextException e) {
            throw new EblockerException("Error while creating SSLContext! " + e.getMessage(), e);
        }
    }

    private SSLContext createSslContext(KeyStore keyStore, char[] password) throws SslContextException {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS"); //NOSONAR: Lesser security is acceptable here and excluding old clients should be avoided
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new SslContextException("failed to create ssl context", e);
        }
    }

    public class SslContextException extends Exception {
        private SslContextException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public interface SslContextChangeListener {
        void onEnable();
        void onDisable();
    }
}
