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

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.SslTestUtils;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SSLContextHandlerTest {

    private final String unitTestHttps = "classpath:test-data/eblocker-https.jks";
    private final String unitTestHttpsExpired = "classpath:test-data/eblocker-https-expired.jks";

    private final String controlBarHostName = "controlbar.unit.test";
    private final String emergencyIp = "169.169.169.169";
    private final Ip4Address ip = Ip4Address.parse("10.10.10.10");
    private final Ip4Address vpnIp = Ip4Address.parse("10.8.0.1");
    private final String[] defaultLocalNames = { "eblocker.test", "another.test" };
    private final String keyStorePassword = "unit-test";

    private Path keyStorePath;
    private Path renewalKeyStorePath;

    private NetworkInterfaceWrapper networkInterface;

    private EblockerCa eblockerCa;
    private EblockerCa renewalEblockerCa;
    private SslService sslService;
    private SslService.SslStateListener stateListener;
    private SSLContextHandler.SslContextChangeListener contextListener;

    private SSLContextHandler sslContextHandler;

    @Before
    public void setup() throws Exception {
        // setup mocks
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(ip);
        Mockito.when(networkInterface.getVpnIpv4Address()).thenReturn(vpnIp);

        // load unit test ca
        eblockerCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        renewalEblockerCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.ALTERNATIVE_CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));

        sslService = Mockito.mock(SslService.class);
        Mockito.doAnswer(im -> stateListener = im.getArgument(0)).when(sslService).addListener(Mockito.any(SslService.SslStateListener.class));

        // setup keystore location
        keyStorePath = Files.createTempFile("ssl-context-handler-test-current", ".jks");
        Files.delete(keyStorePath);

        renewalKeyStorePath = Files.createTempFile("ssl-context-handler-test-renewal", ".jks");
        Files.deleteIfExists(renewalKeyStorePath);

        sslContextHandler = new SSLContextHandler(controlBarHostName, emergencyIp, defaultLocalNames[0] + ", " + defaultLocalNames[1], keyStorePath.toString(), keyStorePassword, renewalKeyStorePath.toString(), networkInterface, sslService);
        contextListener = Mockito.mock(SSLContextHandler.SslContextChangeListener.class);
        sslContextHandler.addContextChangeListener(contextListener);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(keyStorePath);
        Files.deleteIfExists(renewalKeyStorePath);
    }

    @Test
    public void testListenerRegistration() {
        Assert.assertNotNull(stateListener);
    }

    @Test
    public void testGetSslContextBeforeInit() {
        Assert.assertNull(sslContextHandler.getSSLContext());
    }

    @Test
    public void testGetSslContextAfterInitSslDisabledNoKeyPair() {
        stateListener.onInit(false);
        Assert.assertNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onDisable();
    }

    @Test
    public void testGetSslContextAfterInitSslEnabledNoKeyPair() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();
        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], ip.toString(), emergencyIp, vpnIp.toString());
    }

    @Test
    public void testGetSslContextAfterInitSslEnabled() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();

        // verify no new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttps), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());
    }

    @Test
    public void testGetSslContextAfterInitSslEnabledIpChange() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("10.10.10.99"));

        setupKeyStore();

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();

        // verify a new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttps), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertNotEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertNotEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());
        Assert.assertTrue(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), eblockerCa.getCertificate()));

        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], "10.10.10.99", emergencyIp, vpnIp.toString());
    }

    // EB1-1897
    @Test
    public void testGetSslContextAfterInitSslEnabledCorruptKeyStore() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        Files.write(keyStorePath, new byte[4096]);

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();

        // verify a new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttps), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertNotEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertNotEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());
        Assert.assertTrue(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), eblockerCa.getCertificate()));

        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], ip.toString(), emergencyIp, vpnIp.toString());
    }

    @Test
    public void testIpChange() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();

        // change ip and notify
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("10.10.10.99"));
        ArgumentCaptor<NetworkInterfaceWrapper.IpAddressChangeListener> ipChangeListenerCaptor = ArgumentCaptor.forClass(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        Mockito.verify(networkInterface).addIpAddressChangeListener(ipChangeListenerCaptor.capture());
        ipChangeListenerCaptor.getValue().onIpAddressChange(Ip4Address.parse("10.10.10.99"));

        // verify a new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttps), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertNotEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertNotEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());
        Assert.assertTrue(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), eblockerCa.getCertificate()));

        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], "10.10.10.99", emergencyIp, vpnIp.toString());
    }

    @Test
    public void testIpChangeWithoutCa() {
        Mockito.when(sslService.isCaAvailable()).thenReturn(false);

        stateListener.onInit(true);

        ArgumentCaptor<NetworkInterfaceWrapper.IpAddressChangeListener> ipChangeListenerCaptor = ArgumentCaptor.forClass(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        Mockito.verify(networkInterface).addIpAddressChangeListener(ipChangeListenerCaptor.capture());
        ipChangeListenerCaptor.getValue().onIpAddressChange(Ip4Address.parse("10.10.10.99"));
        Mockito.verify(sslService, Mockito.never()).getCa();
    }

    @Test
    public void testExpiration() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore(unitTestHttpsExpired);
        stateListener.onInit(true);

        // verify a new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttpsExpired), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertNotEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertNotEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());
        Assert.assertTrue(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), eblockerCa.getCertificate()));

        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], ip.toString(), emergencyIp, vpnIp.toString());

        LocalDate usedCertNotBefore = LocalDateTime.ofInstant(usedKeyPair.getCertificate().getNotBefore().toInstant(), ZoneId.systemDefault()).toLocalDate();
        Assert.assertTrue(usedCertNotBefore.isEqual(LocalDate.now()));
        LocalDate usedCertNotAfter = LocalDateTime.ofInstant(usedKeyPair.getCertificate().getNotAfter().toInstant(), ZoneId.systemDefault()).toLocalDate();
        Assert.assertTrue(usedCertNotAfter.isBefore(LocalDate.now().plusDays(SSLContextHandler.MAX_VALIDITY_IN_DAYS)));
    }

    @Test
    public void testCaChange() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();

        stateListener.onInit(true);

        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Mockito.verify(contextListener).onEnable();

        // change ca
        EblockerCa newCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.ALTERNATIVE_CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        Mockito.when(sslService.getCa()).thenReturn(newCa);

        stateListener.onCaChange();

        // verify a new key has been generated
        CertificateAndKey testKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(unitTestHttps), keyStorePassword);
        CertificateAndKey usedKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertNotEquals(testKeyPair.getKey(), usedKeyPair.getKey());
        Assert.assertNotEquals(testKeyPair.getCertificate(), usedKeyPair.getCertificate());

        Assert.assertFalse(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), eblockerCa.getCertificate()));
        Assert.assertTrue(PKI.verifyCertificateSignature(usedKeyPair.getCertificate(), newCa.getCertificate()));

        checkCertificate(controlBarHostName, controlBarHostName, defaultLocalNames[0], defaultLocalNames[1], ip.toString(), emergencyIp, vpnIp.toString());

        // check context listeners has been re-enabled
        Mockito.verify(contextListener, Mockito.times(2)).onEnable();
    }

    @Test
    public void testRenewalCaChange() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();

        stateListener.onInit(true);

        // check we are prepared for renewal
        Mockito.verify(contextListener).onEnable();
        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Assert.assertNull(sslContextHandler.getRenewalSSLContext());
        CertificateAndKey currentKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        currentKeyPair.getCertificate().verify(eblockerCa.getCertificate().getPublicKey());

        // make renewal ca available
        Mockito.when(sslService.getRenewalCa()).thenReturn(renewalEblockerCa);
        Mockito.when(sslService.isRenewalCaAvailable()).thenReturn(true);
        stateListener.onRenewalCaChange();

        // check renewal certificate has been generated
        Mockito.verify(contextListener, Mockito.times(2)).onEnable();
        Assert.assertNotNull(sslContextHandler.getRenewalSSLContext());
        CertificateAndKey renewalKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(renewalKeyStorePath.toString()), keyStorePassword);
        renewalKeyPair.getCertificate().verify(renewalEblockerCa.getCertificate().getPublicKey());
        Assert.assertTrue(Files.exists(renewalKeyStorePath));

        // check context listeners has been re-enabled
        Mockito.verify(contextListener, Mockito.times(2)).onEnable();
    }

    @Test
    public void testCaRenewal() throws Exception {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.getRenewalCa()).thenReturn(renewalEblockerCa);
        Mockito.when(sslService.isRenewalCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();

        stateListener.onInit(true);

        // check we are prepared for renewal
        Mockito.verify(contextListener).onEnable();
        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Assert.assertNotNull(sslContextHandler.getRenewalSSLContext());
        CertificateAndKey currentKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        CertificateAndKey renewalKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(renewalKeyStorePath.toString()), keyStorePassword);
        currentKeyPair.getCertificate().verify(eblockerCa.getCertificate().getPublicKey());
        renewalKeyPair.getCertificate().verify(renewalEblockerCa.getCertificate().getPublicKey());

        // rollover
        Mockito.when(sslService.getCa()).thenReturn(renewalEblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.getRenewalCa()).thenReturn(null);
        Mockito.when(sslService.isRenewalCaAvailable()).thenReturn(false);
        stateListener.onCaChange();

        // check certificate has been rolled over
        Mockito.verify(contextListener, Mockito.times(2)).onEnable();
        Assert.assertNotNull(sslContextHandler.getSSLContext());
        Assert.assertNull(sslContextHandler.getRenewalSSLContext());
        CertificateAndKey newKeyPair = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);
        Assert.assertEquals(renewalKeyPair.getCertificate(), newKeyPair.getCertificate());
        Assert.assertEquals(renewalKeyPair.getKey(), newKeyPair.getKey());
        Assert.assertFalse(Files.exists(renewalKeyStorePath));

        // check context listeners has been re-enabled
        Mockito.verify(contextListener, Mockito.times(2)).onEnable();
    }

    @Test
    public void testEnable() throws SslService.PkiException, IOException {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(false);

        setupKeyStore();
        sslService.init();

        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        stateListener.onEnable();

        Mockito.verify(contextListener).onEnable();
    }

    @Test
    public void testDisable() throws SslService.PkiException, IOException {
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);

        setupKeyStore();
        sslService.init();

        Mockito.when(sslService.isSslEnabled()).thenReturn(false);
        stateListener.onDisable();

        Mockito.verify(contextListener).onDisable();
    }

    private void checkCertificate(String cn, String... alternativeNames) throws Exception {
        Assert.assertTrue(Files.exists(keyStorePath));
        CertificateAndKey certificateAndKey = SslTestUtils.loadCertificateAndKey(new SimpleResource(keyStorePath.toString()), keyStorePassword);

        Assert.assertEquals(controlBarHostName, PKI.getCN(certificateAndKey.getCertificate()));

        List<String> expectedAltNames = new ArrayList<>();
        for(String altName : alternativeNames) {
            expectedAltNames.add("DNS:" + altName);
            if (altName.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                expectedAltNames.add("IP:" + altName);
            }
        }

        List<String> altNames = certificateAndKey.getCertificate().getSubjectAlternativeNames().stream().map(this::mapSubjectAlternativeName).distinct().collect(Collectors.toList());
        Assert.assertEquals(expectedAltNames, altNames);
    }

    private String mapSubjectAlternativeName(List<?> altName) {
        if (((Integer) altName.get(0)) == 2 ) {
            return "DNS:" + altName.get(1);
        } else if ((Integer) altName.get(0) == 7) {
            return "IP:" + altName.get(1);
        } else {
            Assert.fail("unexpected alt name entry " + altName);
            throw new IllegalStateException();
        }
    }

    private void setupKeyStore() throws IOException {
        setupKeyStore(unitTestHttps);
    }

    private void setupKeyStore(String resource) throws IOException {
        try (InputStream is = ResourceHandler.getInputStream(new SimpleResource(resource))) {
            try (FileOutputStream os = new FileOutputStream(keyStorePath.toString())) {
                ByteStreams.copy(is, os);
            }
        }
    }
}
