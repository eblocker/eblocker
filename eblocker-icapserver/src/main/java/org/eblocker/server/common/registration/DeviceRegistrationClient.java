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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.io.IOUtils;
import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.DynDnsEntry;
import org.eblocker.registration.MobileConnectionCheck;
import org.eblocker.registration.ProductInfo;
import org.eblocker.registration.TosContainer;
import org.eblocker.registration.UpgradeInfo;
import org.eblocker.registration.UpsellInfoWrapper;
import org.eblocker.registration.error.ClientRequestError;
import org.eblocker.registration.error.ClientRequestException;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.exceptions.NetworkConnectionException;
import org.eblocker.server.http.service.SettingsService;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.glassfish.jersey.SslConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;

public class DeviceRegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationClient.class);

    private static final String JERSEY_CONNECT_TIMEOUT_PROPERTY = "jersey.config.client.connectTimeout";
    private static final String JERSEY_READ_TIMEOUT_PROPERTY = "jersey.config.client.readTimeout";

    private static final String DEVICE_UPGRADE_PATH = "/upgrade";
    private static final String DEVICE_UPSELL_INFO_PATH = "/upsellInfo";

    private static final String HTTP_HEADER_EBLOCKER_OS_VERSION = "X-eBlockerOS-Version";

    private final String registrationUrl;
    private final String mobileConnectionCheckUrl;
    private final String dynDnsIpUpdatePath;
    private final String mobileDnsCheckUrl;

    private final String deviceUrl;
    private final boolean addDeviceId;
    private final boolean addDeviceCertificateSerialNumber;

    private final String tosUrl;

    private final KeyStore trustStore;

    private final DeviceRegistrationProperties deviceRegistrationProperties;

    private final SettingsService settingsService;

    private final ObjectMapper objectMapper;

    private final String eBlockerOsVersion;

    private final int connectTimeout;
    private final int readTimeout;

    @Inject
    public DeviceRegistrationClient(
            @Named("baseurl.my") String registrationUrlDomain,
            @Named("registration.backend.url.path") String registrationUrlPath,
            @Named("baseurl.api") String deviceUrlDomain,
            @Named("registration.device.url.path") String deviceUrlPath,
            @Named("baseurl.my") String tosUrlDomain,
            @Named("registration.tos.url.path") String tosUrlPath,
            @Named("registration.device.serialnumber") boolean addDeviceCertificateSerialNumber,
            @Named("registration.device.id") boolean addDeviceId,
            @Named("registration.truststore.resource") String trustStoreResource,
            @Named("registration.truststore.password") String trustStorePassword,
            @Named("registration.connection.connectTimeout") int connectTimeout,
            @Named("registration.connection.readTimeout") int readTimeout,
            @Named("baseurl.api") String mobileConnectionCheckUrlDomain,
            @Named("mobile.connection.check.url.path") String mobileConnectionCheckUrlPath,
            @Named("dyndns.ip.update.path") String dynDnsIpUpdatePath,
            @Named("mobile.dns.check.url.path") String mobileDnsCheckUrlPath,
            DeviceRegistrationProperties deviceRegistrationProperties,
            SettingsService settingsService,
            ObjectMapper objectMapper,
            @Named("project.version") String eBlockerOsVersion
    ) {
        this.registrationUrl = registrationUrlDomain + registrationUrlPath;
        this.deviceUrl = deviceUrlDomain + deviceUrlPath;
        this.tosUrl = tosUrlDomain + tosUrlPath;
        this.addDeviceId = addDeviceId;
        this.addDeviceCertificateSerialNumber = addDeviceCertificateSerialNumber;
        this.trustStore = loadKeyStoreResource(trustStoreResource, trustStorePassword);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.mobileConnectionCheckUrl = mobileConnectionCheckUrlDomain + mobileConnectionCheckUrlPath;
        this.dynDnsIpUpdatePath = deviceUrlDomain + dynDnsIpUpdatePath;
        this.mobileDnsCheckUrl = mobileConnectionCheckUrlDomain + mobileDnsCheckUrlPath;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.eBlockerOsVersion = eBlockerOsVersion;
    }

    private <T> T postDeviceRegistrationRequest(DeviceRegistrationRequest request, String url, SSLContext sslContext, Class<T> clazz) {
        try {
            return post(url, request, sslContext, clazz);
        } catch (ProcessingException e) {
            LOG.error("Could not send registration request to {}", url, e);
            // The cause contains the real problem, e.g.
            // - java.net.UnknownHostException
            // - javax.net.ssl.SSLHandshakeException
            // - java.net.ConnectException
            throw new NetworkConnectionException("Could not send registration request", e.getCause());
        }
    }

    private <U, V> V post(String url, U payload, SSLContext sslContext, Class<V> clazz) {
        Client client = ClientBuilder.newBuilder()
                .sslContext(sslContext)
                // Uncomment the following line for JUL logging
                // TODO: Use jul-to-slf4j bridge to log consistently
                //.register(new org.glassfish.jersey.logging.LoggingFeature(java.util.logging.Logger.getLogger("HTTP"), java.util.logging.Level.INFO, null, null))
                .build();
        client.property(JERSEY_CONNECT_TIMEOUT_PROPERTY, connectTimeout);
        client.property(JERSEY_READ_TIMEOUT_PROPERTY, readTimeout);

        Response response = client
                .target(url)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .headers(createAuthHeaders())
                .header(HttpHeaders.ACCEPT_LANGUAGE, settingsService.getLocaleSettings().getLanguage())
                .header(HTTP_HEADER_EBLOCKER_OS_VERSION, eBlockerOsVersion)
                .post(Entity.entity(payload, MediaType.APPLICATION_JSON_TYPE));
        LOG.info("Submitted POST request to license server: {} --> {}", url, response.getStatus());

        if (response.getStatus() == HttpResponseStatus.OK.code() || response.getStatus() == HttpResponseStatus.CREATED.code()) {
            return response.readEntity(clazz);
        }

        throw newClientRequestException(response, HttpMethod.POST, url);
    }

    private <T> T get(String url, SSLContext sslContext, Class<T> clazz) {
        Client client = ClientBuilder
                .newBuilder()
                .sslContext(sslContext)
                // Uncomment the following line for JUL logging
                // TODO: Use jul-to-slf4j bridge to log consistently
                //.register(new org.glassfish.jersey.logging.LoggingFeature(java.util.logging.Logger.getLogger("HTTP"), java.util.logging.Level.INFO, null, null))
                .build();
        client.property(JERSEY_CONNECT_TIMEOUT_PROPERTY, connectTimeout);
        client.property(JERSEY_READ_TIMEOUT_PROPERTY, readTimeout);

        Response response = client
                .target(url)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .headers(createAuthHeaders())
                .header(HttpHeaders.ACCEPT_LANGUAGE, settingsService.getLocaleSettings().getLanguage())
                .header(HTTP_HEADER_EBLOCKER_OS_VERSION, eBlockerOsVersion)
                .get();
        LOG.info("Submitted GET request to license server: {} --> {}", url, response.getStatus());

        if (response.getStatus() == HttpResponseStatus.OK.code()) {
            return response.readEntity(clazz);
        }

        throw newClientRequestException(response, HttpMethod.GET, url);
    }

    private MultivaluedMap<String, Object> createAuthHeaders() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        if (addDeviceCertificateSerialNumber && deviceRegistrationProperties.getDeviceCertificate() != null) {
            headers.add("X-SSL-Client-Serial", getDeviceCertificateSerialNumber());
        }
        if (addDeviceId && deviceRegistrationProperties.getDeviceId() != null) {
            headers.add("X-eblocker-device-id", deviceRegistrationProperties.getDeviceId());
        }
        return headers;
    }

    private String getDeviceCertificateSerialNumber() {
        return deviceRegistrationProperties.getDeviceCertificate().getSerialNumber().toString(16);
    }

    public DeviceRegistrationResponse register(DeviceRegistrationRequest request) {
        return postDeviceRegistrationRequest(request, registrationUrl, createSSLContext(), DeviceRegistrationResponse.class);
    }

    public UpgradeInfo isUpgradeAvailable() {
        String url = deviceUrl + "/" + deviceRegistrationProperties.getDeviceId() + DEVICE_UPGRADE_PATH;
        return get(url, createSSLContextForDevice(), UpgradeInfo.class);
    }

    public TosContainer getTosContainer() {
        return get(tosUrl, createSSLContextForDevice(), TosContainer.class);
    }

    public DeviceRegistrationResponse upgrade(DeviceRegistrationRequest request) {
        String url = deviceUrl + "/" + deviceRegistrationProperties.getDeviceId() + DEVICE_UPGRADE_PATH;
        return postDeviceRegistrationRequest(request, url, createSSLContextForDevice(), DeviceRegistrationResponse.class);
    }

    public UpsellInfoWrapper getUpsellInfo(String feature) {
        String url = deviceUrl + "/" + deviceRegistrationProperties.getDeviceId() + DEVICE_UPSELL_INFO_PATH + "/" + feature;
        return get(url, createSSLContextForDevice(), UpsellInfoWrapper.class);
    }

    public ProductInfo getProductInfo() {
        return get(registrationUrl + "/" + deviceRegistrationProperties.getDeviceId() + "/product", createSSLContext(), ProductInfo.class);
    }

    public MobileConnectionCheck requestMobileConnectionCheck(MobileConnectionCheck.Protocol protocol, int port, byte[] secret) {
        MobileConnectionCheck test = new MobileConnectionCheck(null, MobileConnectionCheck.State.NEW, null, secret, protocol, null, port);
        return post(mobileConnectionCheckUrl, test, createSSLContextForDevice(), MobileConnectionCheck.class);
    }

    public boolean requestMobileDnsCheck(String name) {
        return post(mobileDnsCheckUrl, name, createSSLContextForDevice(), Boolean.class);
    }

    public MobileConnectionCheck getMobileConnectionCheck(String id) {
        return get(mobileConnectionCheckUrl + "/" + id, createSSLContextForDevice(), MobileConnectionCheck.class);
    }

    public DynDnsEntry updateDynDnsIpAddress() {
        return post(dynDnsIpUpdatePath + "/" + deviceRegistrationProperties.getDeviceId(), "", createSSLContextForDevice(), DynDnsEntry.class);
    }

    private ClientRequestException newClientRequestException(Response response, String method, String url) {
        LOG.error("{} request to license server failed: {} --> {}", method, url, response.getStatus());
        try {
            Object errorEntity = response.getEntity();
            if (errorEntity instanceof InputStream) {
                byte[] errorContent = IOUtils.toByteArray((InputStream) errorEntity);

                try {
                    ClientRequestError error = objectMapper.readValue(errorContent, ClientRequestError.class);
                    return new ClientRequestException(method, url, response.getStatus(), error);
                } catch (IOException e) {
                    String errorMessage = new String(errorContent);
                    LOG.error("Could not read error value", e);
                    return new ClientRequestException(
                            method,
                            url,
                            response.getStatus(),
                            new ClientRequestError(
                                    response.getStatus(),
                                    "UNKNOWN",
                                    (errorMessage.isEmpty() ? "<empty>" : errorMessage),
                                    System.currentTimeMillis(),
                                    Collections.emptyList()
                            )
                    );
                }

            }
            return new ClientRequestException(method, url, response.getStatus(), "(no error content)");

        } catch (IOException e) {
            LOG.error("cannot read error content", e);
            return new ClientRequestException(method, url, response.getStatus(), "(cannot read error content)");
        }
    }

    private SSLContext createSSLContext() {
        return SslConfigurator.
                newInstance().
                trustStore(trustStore).
                createSSLContext();
    }

    private SSLContext createSSLContextForDevice() {
        return SslConfigurator.
                newInstance().
                trustStore(trustStore).
                keyStore(deviceRegistrationProperties.getDeviceKeyStore()).
                keyPassword(deviceRegistrationProperties.getPassword()).
                createSSLContext();
    }

    private KeyStore loadKeyStoreResource(String resourcePath, String password) {
        EblockerResource resource = new SimpleResource(resourcePath);
        try (InputStream is = ResourceHandler.getInputStream(resource)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(is, password.toCharArray());
            return keyStore;

        } catch (GeneralSecurityException | IOException e) {
            String msg = "Cannot load keyStore " + resourcePath + ": " + e.getMessage();
            LOG.error(msg);
            throw new EblockerException(msg, e);
        }
    }
}
