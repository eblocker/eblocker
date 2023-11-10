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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.codec.digest.DigestUtils;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.data.CaOptions;
import org.eblocker.server.common.data.Certificate;
import org.eblocker.server.common.data.DashboardSslStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DistinguishedName;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.ssl.PkiException;
import org.eblocker.server.common.ssl.SslCertificateClientInstallationTracker;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.http.controller.SSLController;
import org.eblocker.server.http.model.SslWhitelistEntryDto;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.AutoTrustAppService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.FailedConnectionSuggestionService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.SSLWhitelistService;
import org.eblocker.server.http.service.UserAgentService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.http.ssl.Suggestions;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.restexpress.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller for SSL settings.
 */
public class SSLControllerImpl extends SessionContextController implements SSLController {
    private static final Logger log = LoggerFactory.getLogger(SSLControllerImpl.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");
    private static final String RECORDING_STATE_ENABLED = "enabled";

    private final DeviceService deviceService;
    private final UserService userService;
    private final UserAgentService userAgentService;
    private final AutoTrustAppService autoTrustAppService;
    private final ParentalControlService parentalControlService;
    private final SslService sslService;
    private final SSLWhitelistService whitelistDomainStore;
    private final SslCertificateClientInstallationTracker tracker;
    private final FailedConnectionSuggestionService failedConnectionSuggestionService;
    private final NetworkStateMachine networkStateMachine;
    private final SquidWarningService squidWarningService;

    private final ObjectMapper objectMapper;

    @Inject
    public SSLControllerImpl(
            SslService sslService,
            SSLWhitelistService whitelistDomainStore,
            SslCertificateClientInstallationTracker tracker,
            DeviceService deviceService,
            SessionStore sessionStore,
            PageContextStore pageContextStore,
            UserService userService,
            ParentalControlService parentalControlService,
            SquidWarningService squidWarningService,
            FailedConnectionSuggestionService failedConnectionSuggestionService,
            NetworkStateMachine networkStateMachine,
            ObjectMapper objectMapper,
            UserAgentService userAgentService,
            AutoTrustAppService autoTrustAppService) {
        super(sessionStore, pageContextStore);
        this.sslService = sslService;
        this.whitelistDomainStore = whitelistDomainStore;
        this.deviceService = deviceService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.tracker = tracker;
        this.squidWarningService = squidWarningService;
        this.failedConnectionSuggestionService = failedConnectionSuggestionService;
        this.networkStateMachine = networkStateMachine;
        this.objectMapper = objectMapper;
        this.userAgentService = userAgentService;
        this.autoTrustAppService = autoTrustAppService;
    }

    /**
     * Download the CA certificate in order to install it in the browser or
     * system; and enable ssl for the requesting device automatically
     */
    @Override
    public ByteBuf getCACertificateByteStream(Request request, Response response) throws IOException {
        if (sslService.getCa() != null) {
            log.debug("Sending back root ca certificate...");

            Session session = getSession(request);
            Device device = deviceService.getDeviceById(session.getDeviceId());

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(10000);
            try (OutputStream out = new ByteBufOutputStream(buffer)) {
                PKI.storeCertificate(sslService.getCa().getCertificate(), out);
                response.setContentType("application/x-x509-ca-cert");
                // automatically activate SSL for the device in use
                setAndUpdateDeviceSSLStatus(device, true);

                response.addHeader("Content-disposition", "filename=" + sslService.generateFileNameForCertificate());

                return buffer;
            } catch (CryptoException e) {
                buffer.release();
                // automatically disable SSL on error
                setAndUpdateDeviceSSLStatus(device, false);
                throw new EblockerException("failed to send root ca", e);
            }
        }
        throw new NotFoundException();
    }

    /**
     * Download the CA certificate in order to install it in the browser or
     * system; and enable ssl for the requesting device automatically
     */
    @Override
    public ByteBuf getCACertificateByteStreamForFirefox(Request request, Response response) throws IOException {
        response.setContentType("application/octet-stream");
        response.addHeader("Content-disposition", "filename=" + sslService.generateFileNameForCertificate());
        log.debug("Sending back root ca certificate...");
        return getCertStreamForFirefox(sslService.getCa().getCertificate(), getSession(request));
    }

    @Override
    public ByteBuf getRenewalCertificateByteStreamForFirefox(Request request, Response response) throws IOException {
        response.setContentType("application/octet-stream");
        log.debug("Sending back renewal certificate...");
        return getCertStreamForFirefox(sslService.getRenewalCa().getCertificate(), getSession(request));
    }

    private ByteBuf getCertStreamForFirefox(X509Certificate cert, Session session) throws IOException {
        if (sslService.getCa() != null) {
            Device device = deviceService.getDeviceById(session.getDeviceId());

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(10000);
            try (OutputStream out = new ByteBufOutputStream(buffer)) {
                PKI.storeCertificate(cert, out);
                // automatically activate SSL for the device in use
                setAndUpdateDeviceSSLStatus(device, true);
                return buffer;
            } catch (CryptoException e) {
                buffer.release();
                // automatically disable SSL on error
                setAndUpdateDeviceSSLStatus(device, false);
                throw new EblockerException("failed to send root ca", e);
            }
        }
        throw new NotFoundException();
    }

    /**
     * Download the renewal certificate in order to install it in the
     * browser or system;
     * and enable ssl for the requesting device automatically
     */
    @Override
    public ByteBuf getRenewalCertificateByteStream(Request request, Response response) throws IOException {
        if (sslService.getRenewalCa() != null) {
            log.debug("Sending back renewal certificate...");

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(10000);
            try (OutputStream out = new ByteBufOutputStream(buffer)) {
                PKI.storeCertificate(sslService.getRenewalCa().getCertificate(), out);
                response.setContentType("application/x-x509-ca-cert");
                return buffer;
            } catch (CryptoException e) {
                buffer.release();
                throw new EblockerException("failed to send renewal", e);
            }
        }
        throw new NotFoundException();
    }

    /**
     * Is the process of creating the root CA certificate and the webserver certificate finished?
     * Can we download the root CA certificate to the clients?
     */
    @Override
    public boolean areCertificatesReady(Request request, Response response) {
        return sslService.getCa() != null;
    }

    @Override
    public boolean setAutoTrustAppState(Request request, Response response) {
        boolean requestedState = request.getBodyAs(Boolean.class);
        autoTrustAppService.setAutoTrustAppEnabled(requestedState);
        return autoTrustAppService.isAutoTrustAppEnabled();
    }

    @Override
    public boolean getAutoTrustAppState(Request request, Response response) {
        return autoTrustAppService.isAutoTrustAppEnabled();
    }

    @Override
    public void setSSLState(Request request, Response response) {
        SslState sslState = request.getBodyAs(SslState.class);

        log.info(" SSL state now: {} != before: {} ", sslState.isEnabled(), sslService.isSslEnabled());

        //reinit SSL context and certificates (if necessary)
        if (sslState.isEnabled()) { //SSL got enabled
            if (sslState.getCaOptions() != null) {
                log.info("Enabling ssl and generating new certificates...");
                try {
                    sslService.generateCa(sslState.getCaOptions());
                } catch (PkiException e) {
                    throw new EblockerException("certificate generation failed", e);
                }
            }
            sslService.enableSsl();
        } else { //ssl got disabled
            //Delete certificates and unbind HTTPS server?
            sslService.disableSsl();
            userAgentService.turnOffCloakingForAllDevices(deviceService.getDevices(false));
            squidWarningService.clearFailedConnections();
        }
    }

    @Override
    public boolean getSSLState(Request request, Response response) {
        return sslService.isSslEnabled();
    }

    @Override
    public void removeWhitelistedUrl(Request request, Response resp) {
        Map<String, String> map = request.getBodyAs(Map.class);
        SSLWhitelistUrl url = new SSLWhitelistUrl(map.get("name"), map.get("url"));
        log.info("Remove whitelisted SSL url {} | {}", url.getName(), url.getUrl());
        whitelistDomainStore.removeDomain(url.getUrl());
    }

    @Override
    public Integer removeAllWhitelistedUrl(Request request, Response resp) {
        Integer count = 0;
        try {
            List<SSLWhitelistUrl> urls = objectMapper.readValue(request.getBodyAsStream(), new TypeReference<List<SSLWhitelistUrl>>() {
            });
            for (SSLWhitelistUrl url : urls) {
                log.info("Remove whitelisted SSL url {} | {}", url.getName(), url.getUrl());
                whitelistDomainStore.removeDomain(url.getUrl());
                count++;
            }
        } catch (IOException e) {
            log.error("Cannot read SSLWhitelistUrl from request body.", e);
            throw new BadRequestException("Cannot read SSLWhitelistUrl from request body: " + e.getMessage());
        }
        return count;
    }

    @Override
    public void addUrlToSSLWhitelist(Request request, Response resp) {
        SslWhitelistEntryDto entry = request.getBodyAs(SslWhitelistEntryDto.class);
        for (String domain : entry.getDomains()) {
            log.debug("Add whitelisted SSL url {} | {}", entry.getLabel(), domain);
            whitelistDomainStore.addDomain(domain, entry.getLabel());
        }
    }

    @Override
    public void markCertificateStatus(Request request, Response response) {
        String deviceId = getSession(request).getDeviceId();
        String userAgent = request.getHeader("User-Agent");
        BigInteger serialNumber = new BigInteger(request.getHeader("serialNumber"));

        if (deviceId == null || userAgent == null || serialNumber == null) {
            log.warn("incomplete request: device id {} user agent {} serialNumber {}", deviceId, userAgent, serialNumber);
            throw new BadRequestException("incomplete request");
        }

        tracker.markCertificateAsInstalled(deviceId, userAgent, serialNumber, false);

        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
    }

    @Override
    public Certificate createNewRootCA(Request request, Response response) {
        CaOptions caOptions = request.getBodyAs(CaOptions.class);

        log.info("Asking to create a CA certificate which will be {} months valid", caOptions.getValidityInMonths());

        try {
            sslService.generateCa(caOptions);
            STATUS.info("Generating and applying new SSL certificate was successful.");
        } catch (PkiException e) {
            log.error("Creating new certificates failed: {}", e.getMessage(), e);
            throw new BadRequestException(e.getMessage());
        }

        return mapX509Certificate(sslService.getCa().getCertificate());
    }

    @Override
    public CaOptions getDefaultCaOptions(Request request, Response response) {
        return sslService.getDefaultCaOptions();
    }

    @Override
    public Certificate getRootCaCertificate(Request request, Response response) {
        if (sslService.getCa() == null) {
            return null;
        }
        return mapX509Certificate(sslService.getCa().getCertificate());
    }

    Certificate getRenewalCertificate() {
        if (sslService.getRenewalCa() == null) {
            return null;
        }
        return mapX509Certificate(sslService.getRenewalCa().getCertificate());
    }

    private Certificate mapX509Certificate(X509Certificate x509Certificate) {
        Certificate certificate = new Certificate();
        certificate.setDistinguishedName(DistinguishedName.fromRfc2253(x509Certificate.getSubjectDN().getName()));
        certificate.setSerialNumber(x509Certificate.getSerialNumber());
        certificate.setNotAfter(x509Certificate.getNotAfter());
        certificate.setNotBefore(x509Certificate.getNotBefore());

        try {
            byte[] encodedX509Certificate = x509Certificate.getEncoded();
            certificate.setFingerprintSha256(prettyPrintBytes(DigestUtils.getSha256Digest().digest(encodedX509Certificate)));
            certificate.setFingerprintSha1(prettyPrintBytes(DigestUtils.getSha1Digest().digest(encodedX509Certificate)));
        } catch (CertificateEncodingException e) {
            log.error("failed to encode certificate", e);
        }

        return certificate;
    }

    /**
     * Pretty print a byte array in the form 00:01:02:03
     */
    private String prettyPrintBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    public static class SslState {
        private boolean enabled;
        private CaOptions caOptions;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public CaOptions getCaOptions() {
            return caOptions;
        }

        public void setCaOptions(CaOptions caOptions) {
            this.caOptions = caOptions;
        }
    }

    @Override
    public boolean getDeviceStatus(Request request, Response response) {
        Session session = getSession(request);
        Device device = deviceService.getDeviceById(session.getDeviceId());
        if (device != null) {
            return device.isSslEnabled();
        }
        throw new BadRequestException("Current device not found");
    }

    @Override
    public boolean setDeviceStatus(Request request, Response response) {
        boolean newDeviceSslState = request.getBodyAs(Boolean.class);

        Session session = getSession(request);
        Device device = deviceService.getDeviceById(session.getDeviceId());
        setAndUpdateDeviceSSLStatus(device, newDeviceSslState);

        return newDeviceSslState;
    }

    private void setAndUpdateDeviceSSLStatus(Device device, boolean newDeviceSslState) {
        if (device == null) {
            throw new BadRequestException("Current device not found");
        }

        // Not possible if device is restricted by parental control settings
        int operatingUser = device.getOperatingUser();
        UserModule user = userService.getUserById(operatingUser);
        if (user == null) {
            throw new BadRequestException(
                    "En-/Disabling SSL for this device not permitted, Parental Control verification failed");
        }
        UserProfileModule userProfile = parentalControlService.getProfile(user.getAssociatedProfileId());
        if (userProfile == null) {
            throw new BadRequestException(
                    "En-/Disabling SSL for this device not permitted, Parental Control verification failed");
        }
        if (!newDeviceSslState && (userProfile.isControlmodeMaxUsage() || userProfile.isControlmodeTime()
                || userProfile.isControlmodeUrls())) {
            // If SSL is to be activated and Parental Control restrictions
            // apply to the device, no changes permitted
            throw new BadRequestException(
                    "En-/Disabling SSL for this device not permitted due to Parental Control restrictions");
        }

        device.setSslEnabled(newDeviceSslState);
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
    }

    public DashboardSslStatus getSslDashboardStatus(Request request, Response response) {
        DashboardSslStatus result = new DashboardSslStatus();

        // Global SSL status
        result.setGlobalSslStatus(getSSLState(request, response));

        // SSL Status of this specific device
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        String userAgent = session.getUserAgent();
        Device device = deviceService.getDeviceById(deviceId);

        if (device != null) {
            result.setDeviceSslStatus(device.isSslEnabled());
            result.setExecuteSslBackgroundCheck(!session.getUserAgentInfo().isMsie());
        } else {
            // TODO: implement -- maybe pass Boolean and set to null in this case. That way we can show in the UI device-unavailable
            log.error("failed to set device SSL status in dashboard status: cannot find device with ID {}", session.getDeviceId());
        }

        // Status of current SSL certificate
        Certificate rootCert = getRootCaCertificate(request, response);
        if (rootCert != null) {
            result.setCurrentCertificate(rootCert);
            result.setRootCertSerialNumber(rootCert.getSerialNumber().toString(10));

            Boolean isInstalled = null;
            if (tracker.isCaCertificateInstalled(deviceId, userAgent).equals(SslCertificateClientInstallationTracker.Status.INSTALLED)) {
                isInstalled = true;
            } else if (tracker.isCaCertificateInstalled(deviceId, userAgent).equals(SslCertificateClientInstallationTracker.Status.NOT_INSTALLED)) {
                isInstalled = false;
            }
            result.setCurrentCertificateInstalled(isInstalled);
        } else {
            log.info("unable to set current certificate in dashboard status: no certificate available.");
        }

        // Status of renewal SSL certificate
        Certificate renewalCert = getRenewalCertificate();
        if (renewalCert != null) {
            result.setRenewalCertificate(renewalCert);
            result.setRenewalCertSerialNumber(renewalCert.getSerialNumber().toString(10));

            Boolean isInstalled = null;
            if (tracker.isFutureCaCertificateInstalled(deviceId, userAgent).equals(SslCertificateClientInstallationTracker.Status.INSTALLED)) {
                isInstalled = true;
            } else if (tracker.isFutureCaCertificateInstalled(deviceId, userAgent).equals(SslCertificateClientInstallationTracker.Status.NOT_INSTALLED)) {
                isInstalled = false;
            }
            result.setRenewalCertificateInstalled(isInstalled);
        } else {
            log.info("No renewal certificate available.");
        }
        result.setCaRenewWeeks(sslService.getCaRenewWeeks());
        return result;
    }

    @Override
    public Suggestions getFailedConnections(Request request, Response response) {
        return failedConnectionSuggestionService.getFailedConnectionsByAppModules();
    }

    @Override
    public void clearFailedConnections(Request request, Response response) {
        squidWarningService.clearFailedConnections();
    }

    @Override
    public Map<String, Boolean> getErrorRecordingEnabled(Request request, Response response) {
        return Collections.singletonMap(RECORDING_STATE_ENABLED, squidWarningService.isEnabled());
    }

    @Override
    public Map<String, Boolean> setErrorRecordingEnabled(Request request, Response response) {
        try {
            Map<String, Boolean> map = request.getBodyAs(Map.class);
            squidWarningService.setRecordingFailedConnectionsEnabled(map.get(RECORDING_STATE_ENABLED));
            return Collections.singletonMap(RECORDING_STATE_ENABLED, squidWarningService.isEnabled());
        } catch (IOException e) {
            throw new ServiceException("failed to set recording to " + squidWarningService, e);
        }
    }
}
