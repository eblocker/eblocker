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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.server.common.data.CaOptions;
import org.eblocker.server.common.data.Certificate;
import org.eblocker.server.common.data.DashboardSslStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserProfileModule.InternetAccessRestrictionMode;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.session.UserAgentInfo;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslCertificateClientInstallationTracker;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.PkiException;
import org.eblocker.server.common.ssl.SslTestUtils;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.http.controller.impl.SSLControllerImpl.SslState;
import org.eblocker.server.http.model.SslWhitelistEntryDto;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.FailedConnectionSuggestionService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.SSLWhitelistService;
import org.eblocker.server.http.service.UserAgentService;
import org.eblocker.server.http.service.UserService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class SSLControllerImplTest {
    private SSLControllerImpl controller;
    private SslService sslService;
    private SSLWhitelistService whitelistDomainStore;
    private SslCertificateClientInstallationTracker tracker;
    private DeviceService deviceService;
    private SessionStore sessionStore;
    private PageContextStore pageContextStore;
    private UserService userService;
    private ParentalControlService parentalControlService;
    private SquidWarningService squidWarningService;
    private FailedConnectionSuggestionService failedConnectionSuggestionService;
    private NetworkStateMachine networkStateMachine;
    private CertificateAndKey unitTestCaCertificateAndKey;
    private UserAgentService userAgentService;
    private static ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        sslService = Mockito.mock(SslService.class);
        whitelistDomainStore = Mockito.mock(SSLWhitelistService.class);
        tracker = Mockito.mock(SslCertificateClientInstallationTracker.class);
        deviceService = Mockito.mock(DeviceService.class);
        sessionStore = Mockito.mock(SessionStore.class);
        pageContextStore = Mockito.mock(PageContextStore.class);
        userService = Mockito.mock(UserService.class);
        parentalControlService = Mockito.mock(ParentalControlService.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        squidWarningService = Mockito.mock(SquidWarningService.class);
        failedConnectionSuggestionService = Mockito.mock(FailedConnectionSuggestionService.class);
        userAgentService = Mockito.mock(UserAgentService.class);
        objectMapper = new ObjectMapper();

        controller = new SSLControllerImpl(
                sslService,
                whitelistDomainStore,
                tracker,
                deviceService,
                sessionStore,
                pageContextStore,
                userService,
                parentalControlService,
                squidWarningService,
                failedConnectionSuggestionService,
                networkStateMachine,
                objectMapper,
                userAgentService
        );

        // load unit test ca
        unitTestCaCertificateAndKey = SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testAreCertificatesReadyCertificatesReady() {
        // Certificates are ready
        when(sslService.getCa()).thenReturn(new EblockerCa(null));
        // Call to controller
        assertTrue(controller.areCertificatesReady(null, null));
        // Verify
        Mockito.verify(sslService).getCa();
    }

    @Test
    public void testAreCertificatesReadyCertificatesNotReady() {
        // Certificates are not ready
        when(sslService.getCa()).thenReturn(null);
        // Call to controller
        assertFalse(controller.areCertificatesReady(null, null));
        // Verify
        Mockito.verify(sslService).getCa();
    }

    @Test
    public void testSetSslStateEnabledNoCaOptions() throws IOException, PkiException {
        // State: enabled, no CaOptions
        SslState newState = new SslState();
        newState.setEnabled(true);
        newState.setCaOptions(null);
        // Request containing new state
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(any())).thenReturn(newState);
        // Actual call to controller
        controller.setSSLState(request, null);
        // Verify
        Mockito.verify(sslService).enableSsl();
        Mockito.verify(sslService, never()).generateCa(any());
        Mockito.verify(sslService, never()).disableSsl();
    }

    @Test
    public void testSetSslStateEnabledCaOptionsGiven() throws PkiException, IOException {
        // State: enabled, CaOptions given
        SslState newStateWithOptions = new SslState();
        newStateWithOptions.setEnabled(true);
        newStateWithOptions.setCaOptions(new CaOptions());
        // Request containing new state
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(any())).thenReturn(newStateWithOptions);
        // Actual call to controller
        controller.setSSLState(request, null);
        // Verify
        Mockito.verify(sslService).enableSsl();
        Mockito.verify(sslService).generateCa(any());
        Mockito.verify(sslService, never()).disableSsl();
    }

    @Test
    public void testSetSslStateDisabled() throws IOException, PkiException {
        // State: disabled
        SslState newStateDisabled = new SslState();
        newStateDisabled.setEnabled(false);
        newStateDisabled.setCaOptions(null);
        // Request containing new state
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(any())).thenReturn(newStateDisabled);
        // Actual call to controller
        controller.setSSLState(request, null);
        // Verify
        Mockito.verify(sslService, never()).enableSsl();
        Mockito.verify(sslService, never()).generateCa(any());
        Mockito.verify(sslService).disableSsl();
    }

    @Test
    public void testGetSslState() {
        when(sslService.isSslEnabled()).thenReturn(true);
        // Call to controller
        assertTrue(controller.getSSLState(null, null));
        // Verify
        Mockito.verify(sslService).isSslEnabled();
    }

    @Test
    public void testRemoveWhitelistedUrl() throws Exception {
        String url = "www.value.url";
        Map<String, String> map = new HashMap<String, String>();
        map.put("name", "value-name");
        map.put("url", url);
        // Request containing URL
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(any())).thenReturn(map);
        // Actual call to controller
        controller.removeWhitelistedUrl(request, null);
        // Verify
        Mockito.verify(whitelistDomainStore).removeDomain(url);
    }

    @Test
    public void testAddUrlToWhitelist() {
        String[] domains = new String[]{ "www.value.url", "api.value.url" };
        String label = "name of url";

        SslWhitelistEntryDto entry = new SslWhitelistEntryDto(label, Arrays.asList(domains));

        // Request containing URL
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(SslWhitelistEntryDto.class)).thenReturn(entry);

        // Actual call to controller
        controller.addUrlToSSLWhitelist(request, null);

        // Verify
        Mockito.verify(whitelistDomainStore).addDomain(domains[0], label);
        Mockito.verify(whitelistDomainStore).addDomain(domains[1], label);
    }

    @Test
    public void testMarkCertificateStatus() {
        String deviceId = "device:11:22:33:44:55:66";
        String userAgent = "Mozilla Firefox";
        String serialNumber = "42";
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request containing data
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        when(request.getHeader(eq("User-Agent"))).thenReturn(userAgent);
        when(request.getHeader(eq("serialNumber"))).thenReturn(serialNumber);
        // Session handling
        Session session = Mockito.mock(Session.class);
        when(session.getDeviceId()).thenReturn(deviceId);
        when(sessionStore.getSession(any())).thenReturn(session);
        // Response
        Response response = Mockito.mock(Response.class);
        // Actual call to controller
        controller.markCertificateStatus(request, response);
        // Verify
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(request).getHeader(eq("User-Agent"));
        Mockito.verify(request).getHeader(eq("serialNumber"));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(tracker)
                .markCertificateAsInstalled(eq(deviceId), eq(userAgent), eq(new BigInteger(serialNumber)), eq(false));
        Mockito.verify(response).addHeader(eq("Access-Control-Allow-Origin"), eq("*"));
        Mockito.verify(response).setResponseCode(eq(HttpResponseStatus.NO_CONTENT.code()));
    }

    @Test
    public void testMarkCertificateStatusRequestCorrupted() {
        String deviceId = "device:11:22:33:44:55:66";
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request containing the data contains corrupted/incomplete data
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        when(request.getHeader(eq("User-Agent"))).thenReturn(null);
        when(request.getHeader(eq("serialNumber"))).thenReturn(null);
        // Session handling
        Session session = Mockito.mock(Session.class);
        when(session.getDeviceId()).thenReturn(deviceId);
        when(sessionStore.getSession(any())).thenReturn(session);
        // Response
        Response response = Mockito.mock(Response.class);
        // Actual call to controller
        boolean exceptionCatched = false;
        try {
            controller.markCertificateStatus(request, null);
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        // Verify
        assertTrue(exceptionCatched);
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(request).getHeader(eq("User-Agent"));
        Mockito.verify(request).getHeader(eq("serialNumber"));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verifyZeroInteractions(tracker, response);
    }

    @Test
    public void testCreateNewRootCertificateFails() throws PkiException {
        // CA Options
        CaOptions caOptions = Mockito.mock(CaOptions.class);
        // Request
        Request request = Mockito.mock(Request.class);
        when(request.getBodyAs(any())).thenReturn(caOptions);
        // SSL Service
        doThrow(new PkiException("exception")).when(sslService).generateCa(any());
        // Call to controller
        boolean exceptionCatched = false;
        try {
            controller.createNewRootCA(request, null);
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        assertTrue(exceptionCatched);
        Mockito.verify(request).getBodyAs(eq(CaOptions.class));
        Mockito.verify(sslService).generateCa(any(CaOptions.class));
    }

    @Test
    public void testGetDefaultCaOptions() {
        // Call to controller
        controller.getDefaultCaOptions(null, null);
        // Verify
        Mockito.verify(sslService).getDefaultCaOptions();
    }

    @Test
    public void testGetRootCaCertificate() {
        // CA
        EblockerCa ca = Mockito.mock(EblockerCa.class);
        when(ca.getCertificate()).thenReturn(unitTestCaCertificateAndKey.getCertificate());
        // SSL service
        when(sslService.getCa()).thenReturn(ca);
        // Call to controller
        Certificate result = controller.getRootCaCertificate(null, null);
        // Verify
        Mockito.verify(sslService, times(2)).getCa();
        Mockito.verify(ca).getCertificate();
        assertEquals("/CN=unit-test-root",
                result.getDistinguishedName().toString());
        assertEquals(new BigInteger("113402347514200015148051599106157440534"), result.getSerialNumber());
        Assert.assertTrue(result.getNotAfter().after(new Date()));
        Assert.assertTrue(result.getNotBefore().before(new Date()));
        // Fingerprint not tested
    }

    @Test
    public void testGetRootCertificateNotFound() {
        when(sslService.getCa()).thenReturn(null);
        // Call to controller
        Certificate result = controller.getRootCaCertificate(null, null);
        // Verify
        assertEquals(null, result);
        Mockito.verify(sslService).getCa();
    }

    @Test
    public void testGetRenewalCertificate() throws CertificateEncodingException {
        // CA
        EblockerCa ca = Mockito.mock(EblockerCa.class);
        when(ca.getCertificate()).thenReturn(unitTestCaCertificateAndKey.getCertificate());
        // SSL service
        when(sslService.getRenewalCa()).thenReturn(ca);
        // Call to controller
        Certificate result = controller.getRenewalCertificate();
        // Verify
        Mockito.verify(sslService, times(2)).getRenewalCa();
        Mockito.verify(ca).getCertificate();
        assertEquals("/CN=unit-test-root",
                result.getDistinguishedName().toString());
        assertEquals(new BigInteger("113402347514200015148051599106157440534"), result.getSerialNumber());
        Assert.assertTrue(result.getNotAfter().after(new Date()));
        Assert.assertTrue(result.getNotBefore().before(new Date()));
        // Fingerprint not tested
    }

    @Test
    public void testGetRenewalCertificateNotFound() {
        when(sslService.getRenewalCa()).thenReturn(null);
        // Call to controller
        Certificate result = controller.getRenewalCertificate();
        // Verify
        assertEquals(null, result);
        Mockito.verify(sslService).getRenewalCa();
    }

    @Test
    public void testGetDeviceStatusDeviceFound() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // Device
        Device device = new Device();
        device.setSslEnabled(true);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // Actual call to controller
        assertTrue(controller.getDeviceStatus(request, null));
        // Verify
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
    }

    @Test
    public void testGetDeviceStatusDeviceNotFound() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // Device service
        // Device service does not know about the device
        when(deviceService.getDeviceById(deviceId)).thenReturn(null);
        // Actual call to controller
        boolean exceptionCatched = false;
        try {
            controller.getDeviceStatus(request, null);
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        // Verify
        assertTrue(exceptionCatched);
        Mockito.verify(deviceService).getDeviceById(deviceId);
    }

    @Test
    public void testSetDeviceStatus() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // User Module
        int userModuleId = 23;
        int associatedProfileId = 1337;
        UserModule user = new UserModule(
                userModuleId,
                associatedProfileId,
                "userName",
                "userNameKey",
                null, null,
                false,
                null,
                Collections.emptyMap(),
                null, null, null);
        // User Profile Module
        int userProfileModuleId = 24;
        UserProfileModule profile = new UserProfileModule(
                userProfileModuleId,
                "userProfileModuleName",
                "userProfileModuleDescription",
                "userProfileModuleNameKey",
                "userProfileModuleDescriptionKey",
                false,
                false,
                Collections.emptySet(),
                Collections.emptySet(),
                InternetAccessRestrictionMode.WHITELIST,
                Collections.emptySet(),
                Collections.emptyMap(),
                null,
                false,
                null);
        profile.setControlmodeMaxUsage(false);
        profile.setControlmodeTime(false);
        profile.setControlmodeUrls(false);
        // Device
        Device device = new Device();
        device.setSslEnabled(false);
        device.setOperatingUser(userModuleId);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // User service
        when(userService.getUserById(eq(userModuleId))).thenReturn(user);
        // Parental Control Service
        when(parentalControlService.getProfile(eq(associatedProfileId))).thenReturn(profile);
        // Actual call to controller
        assertTrue(controller.setDeviceStatus(request, null));
        // Verify
        Mockito.verify(request).getBodyAs(any());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        Mockito.verify(userService).getUserById(eq(userModuleId));
        Mockito.verify(parentalControlService).getProfile(eq(associatedProfileId));
        assertTrue(device.isSslEnabled());
        Mockito.verify(deviceService).updateDevice(eq(device));
        Mockito.verify(networkStateMachine).deviceStateChanged(device);
    }

    @Test
    public void testSetDeviceStatusDeviceNotFound() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(null);
        // Actual call to controller
        boolean exceptionCatched = false;
        try {
            controller.setDeviceStatus(request, null);
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        // Verify
        assertTrue(exceptionCatched);
        Mockito.verify(request).getBodyAs(any());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        Mockito.verifyZeroInteractions(userService, parentalControlService);
        Mockito.verify(deviceService, never()).updateDevice(any());
    }

    @Test
    public void testSetDeviceStatusUserNotFound() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // User Profile Module
        int userProfileModuleId = 24;
        UserProfileModule profile = new UserProfileModule(
                userProfileModuleId,
                "userProfileModuleName",
                "userProfileModuleDescription",
                "userProfileModuleNameKey",
                "userProfileModuleDescriptionKey",
                false,
                false,
                Collections.emptySet(),
                Collections.emptySet(),
                InternetAccessRestrictionMode.WHITELIST,
                Collections.emptySet(),
                Collections.emptyMap(),
                null,
                false,
                null);
        profile.setControlmodeMaxUsage(false);
        profile.setControlmodeTime(false);
        profile.setControlmodeUrls(false);
        // Device
        Device device = new Device();
        device.setSslEnabled(false);
        int userModuleId = 23;
        device.setOperatingUser(userModuleId);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // User service
        when(userService.getUserById(eq(userModuleId))).thenReturn(null);
        // Actual call to controller
        boolean exceptionCatched = false;
        try {
            assertTrue(controller.setDeviceStatus(request, null));
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        // Verify
        assertTrue(exceptionCatched);
        Mockito.verify(request).getBodyAs(any());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        Mockito.verify(userService).getUserById(eq(userModuleId));
        assertFalse(device.isSslEnabled());
        Mockito.verify(deviceService, never()).updateDevice(eq(device));
    }

    @Test
    public void testSetDeviceStatusUserProfileNotFound() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // User Module
        int userModuleId = 23;
        int associatedProfileId = 1337;
        UserModule user = new UserModule(
                userModuleId,
                associatedProfileId,
                "userName",
                "userNameKey",
                null, null,
                false,
                null,
                Collections.emptyMap(),
                null,
                null,
                null);
        // Device
        Device device = new Device();
        device.setSslEnabled(false);
        device.setOperatingUser(userModuleId);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // User service
        when(userService.getUserById(eq(userModuleId))).thenReturn(user);
        // Parental Control Service
        when(parentalControlService.getProfile(eq(associatedProfileId))).thenReturn(null);
        // Actual call to controller
        boolean exceptionCatched = false;
        try {
            assertTrue(controller.setDeviceStatus(request, null));
            // Exception expected
            assertTrue(false);
        } catch (Exception e) {
            exceptionCatched = true;
        }
        // Verify
        assertTrue(exceptionCatched);
        Mockito.verify(request).getBodyAs(any());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        Mockito.verify(userService).getUserById(eq(userModuleId));
        Mockito.verify(parentalControlService).getProfile(eq(associatedProfileId));

        assertFalse(device.isSslEnabled());
        Mockito.verify(deviceService, never()).updateDevice(eq(device));
    }

    @Test
    public void testSetDeviceStatusDeviceParentalControlRestricted() {
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // User Module
        int userModuleId = 23;
        int associatedProfileId = 1337;
        UserModule user = new UserModule(
                userModuleId,
                associatedProfileId,
                "userName",
                "userNameKey",
                null, null,
                false,
                null,
                Collections.emptyMap(),
                null,
                null,
                null);
        // User Profile Module
        int userProfileModuleId = 24;
        UserProfileModule profile = new UserProfileModule(
                userProfileModuleId,
                "userProfileModuleName",
                "userProfileModuleDescription",
                "userProfileModuleNameKey",
                "userProfileModuleDescriptionKey",
                false,
                false,
                Collections.emptySet(),
                Collections.emptySet(),
                InternetAccessRestrictionMode.WHITELIST,
                Collections.emptySet(),
                Collections.emptyMap(),
                null,
                false,
                null);
        profile.setControlmodeMaxUsage(true);
        profile.setControlmodeTime(true);
        profile.setControlmodeUrls(true);
        // Device
        Device device = new Device();
        device.setSslEnabled(false);
        device.setOperatingUser(userModuleId);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // User service
        when(userService.getUserById(eq(userModuleId))).thenReturn(user);
        // Parental Control Service
        when(parentalControlService.getProfile(eq(associatedProfileId))).thenReturn(profile);
        // Actual call to controller
        assertTrue(controller.setDeviceStatus(request, null));
        // Verify
        Mockito.verify(request).getBodyAs(any());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        Mockito.verify(userService).getUserById(eq(userModuleId));
        Mockito.verify(parentalControlService).getProfile(eq(associatedProfileId));

        assertTrue(device.isSslEnabled());
        Mockito.verify(deviceService).updateDevice(eq(device));
    }

    @Test
    public void testGetSslDashboardStatus() {
        // Specific settings of mocks
        // SSL service
        boolean globalSslState = true;
        when(sslService.isSslEnabled()).thenReturn(globalSslState);
        // Transaction Identifier
        TransactionIdentifier txId = Mockito.mock(TransactionIdentifier.class);
        // Request
        Request request = Mockito.mock(Request.class);
        String txIdKey = "transactionIdentifier";
        when(request.getAttachment(eq(txIdKey))).thenReturn(txId);
        boolean newSslState = true;
        when(request.getBodyAs(any())).thenReturn(newSslState);
        // Session
        Session session = Mockito.mock(Session.class);
        String deviceId = "device:00:11:22:33:44:55";
        when(session.getDeviceId()).thenReturn(deviceId);
        when(session.getUserAgentInfo()).thenReturn(UserAgentInfo.OTHER_BROWSER);
        String userAgent = "Mozilla Firefox";
        when(session.getUserAgent()).thenReturn(userAgent);
        // Session store
        when(sessionStore.getSession(eq(txId))).thenReturn(session);
        // Device
        Device device = new Device();
        boolean deviceSslEnabled = true;
        device.setSslEnabled(deviceSslEnabled);
        // Device service
        when(deviceService.getDeviceById(eq(deviceId))).thenReturn(device);
        // Actual call to controller
        DashboardSslStatus result = controller.getSslDashboardStatus(request, null);
        // Verify
        Mockito.verify(sslService).isSslEnabled();
        assertEquals(globalSslState, result.getGlobalSslStatus());
        Mockito.verify(request).getAttachment(eq(txIdKey));
        Mockito.verify(sessionStore).getSession(eq(txId));
        Mockito.verify(session).getDeviceId();
        Mockito.verify(deviceService).getDeviceById(eq(deviceId));
        assertEquals(deviceSslEnabled, result.getDeviceSslStatus());
        assertTrue(result.isExecuteSslBackgroundCheck());

        // Other parts of the tested function are merely calls of functions, not tested here
    }
}
