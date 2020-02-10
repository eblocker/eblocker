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

import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GeneratingKeyManagerTest {

    private static final int CACHE_SIZE = 3;
    private static final String DEFAULT_NAME = "eblocker.unit.test";

    private EblockerCa eblockerCa;
    private KeyPair keyPair;
    private GeneratingKeyManager keyManager;

    @Before
    public void setup() throws Exception {
        CertificateAndKey certificateAndKey = SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
        eblockerCa = new EblockerCa(certificateAndKey);
        keyPair = PKI.generateRSAKeyPair(2048);
        keyManager = new GeneratingKeyManager(eblockerCa, keyPair, CACHE_SIZE, 1, Collections.singletonList(DEFAULT_NAME));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientAliases() {
        keyManager.getClientAliases("www.eblocker", new Principal[0]);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testChooseClientAlias() {
        keyManager.chooseClientAlias(new String[] { "www.eblocker.com"}, new Principal[0], new Socket());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetServerAliases() {
        keyManager.getServerAliases("www.eblocker.com", new Principal[0]);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testChooseEngineClientAlias() {
        keyManager.chooseEngineClientAlias(new String[] { "www.eblocker.com" }, new Principal[0], Mockito.mock(SSLEngine.class));
    }

    @Test
    public void testChooseEngineServerAlias() throws CertificateParsingException {
        ExtendedSSLSession session = Mockito.mock(ExtendedSSLSession.class);
        Mockito.when(session.getRequestedServerNames()).thenReturn(Collections.singletonList(new SNIHostName("www.eblocker.com")));
        SSLEngine sslEngine = Mockito.mock(SSLEngine.class);
        Mockito.when(sslEngine.getHandshakeSession()).thenReturn(session);
        Mockito.when(sslEngine.getPeerHost()).thenReturn("138.68.124.96");

        String key = keyManager.chooseEngineServerAlias("www.eblocker.com", null, sslEngine);
        X509Certificate[] chain = keyManager.getCertificateChain(key);
        assertChain("www.eblocker.com", chain);
    }

    @Test
    public void testGetPrivateKey() {
        Assert.assertEquals(keyPair.getPrivate(), keyManager.getPrivateKey(null));
        Assert.assertEquals(keyPair.getPrivate(), keyManager.getPrivateKey("does not"));
        Assert.assertEquals(keyPair.getPrivate(), keyManager.getPrivateKey("matter"));
    }

    @Test
    public void testSingleCreationNoSni() throws UnknownHostException, CertificateParsingException {
        SSLSocket socket = createMockSslSocket(null, "138.68.124.96", 443);

        String key = keyManager.chooseServerAlias("EC_EC", null, socket);
        X509Certificate[] chain = keyManager.getCertificateChain(key);

        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
        Assert.assertNotNull(chain[0]);
        Assert.assertEquals(keyPair.getPublic(), chain[0].getPublicKey());
        Assert.assertEquals(chain[0].getIssuerX500Principal(), eblockerCa.getCertificate().getSubjectX500Principal());
        Assert.assertEquals(new X500Principal("cn=eblocker.unit.test"), chain[0].getSubjectX500Principal());
        Assert.assertEquals(1, chain[0].getSubjectAlternativeNames().size());
        Assert.assertTrue(chain[0].getSubjectAlternativeNames().contains(Arrays.asList(2, "eblocker.unit.test")));
    }

    @Test
    public void testSingleCreationSniAvailable() throws UnknownHostException, CertificateParsingException {
        SSLSocket socket = createMockSslSocket("www.eblocker.com", "138.68.124.96", 443);

        String key = keyManager.chooseServerAlias("EC_EC", null, socket);
        X509Certificate[] chain = keyManager.getCertificateChain(key);
        assertChain("www.eblocker.com", chain);
    }

    @Test
    public void testCaching() throws UnknownHostException, CertificateParsingException {
        List<String> hostnames = new ArrayList<>();
        List<String> ipAddresses = new ArrayList<>();
        List<Socket> sockets = new ArrayList<>();
        for(int i = 0; i < CACHE_SIZE + 1; ++i) {
            hostnames.add(i + ".eblocker.com");
            ipAddresses.add("138.68.124." + i);
            sockets.add(createMockSslSocket(hostnames.get(i), ipAddresses.get(i), 443));
        }

        List<X509Certificate[]> chains = new ArrayList<>();
        for(int i = 0; i < hostnames.size(); ++i) {
            String key = keyManager.chooseServerAlias("EC_EC", null, sockets.get(i));
            X509Certificate[] chain = keyManager.getCertificateChain(key);
            assertChain(hostnames.get(i), chain);
            chains.add(chain);
        }

        // last CACHE_SIZE entries must be cached
        for(int i = 1; i < hostnames.size(); ++i) {
            String key = keyManager.chooseServerAlias("EC_EC", null, sockets.get(i));
            X509Certificate[] chain = keyManager.getCertificateChain(key);
            Assert.assertSame(chains.get(i)[0], chain[0]);
        }

        // first entry must have been evicted so it is a equal chain but not the same certificate
        String key = keyManager.chooseServerAlias("EC_EC", null, sockets.get(0));
        X509Certificate[] chain = keyManager.getCertificateChain(key);
        assertChain(hostnames.get(0), chain);
        Assert.assertNotSame(chains.get(0)[0], chain[0]);
        Assert.assertNotEquals(chains.get(0)[0], chain[0]);
    }

    private void assertChain(String hostname, X509Certificate[] chain) throws CertificateParsingException {
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
        Assert.assertNotNull(chain[0]);
        Assert.assertEquals(keyPair.getPublic(), chain[0].getPublicKey());
        Assert.assertEquals(chain[0].getIssuerX500Principal(), eblockerCa.getCertificate().getSubjectX500Principal());
        Assert.assertEquals(new X500Principal("cn=" + hostname), chain[0].getSubjectX500Principal());
        Assert.assertEquals(2, chain[0].getSubjectAlternativeNames().size());
        Assert.assertTrue(chain[0].getSubjectAlternativeNames().contains(Arrays.asList(2, hostname)));
        Assert.assertTrue(chain[0].getSubjectAlternativeNames().contains(Arrays.asList(2, DEFAULT_NAME)));
    }

    private SSLSocket createMockSslSocket(String hostname, String ipAddress, int port) throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(hostname, InetAddress.getByName(ipAddress).getAddress());
        ExtendedSSLSession session = Mockito.mock(ExtendedSSLSession.class);
        if (hostname != null) {
            Mockito.when(session.getRequestedServerNames()).thenReturn(Collections.singletonList(new SNIHostName(hostname)));
        }
        SSLSocket socket = Mockito.mock(SSLSocket.class);
        Mockito.when(socket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress(address, port));
        Mockito.when(socket.getHandshakeSession()).thenReturn(session);
        return socket;
    }

}
