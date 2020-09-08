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

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GeneratingKeyManager extends X509ExtendedKeyManager {

    private final EblockerCa eblockerCa;
    private final KeyPair keyPair;
    private final List<String> defaultNames;

    private final Cache<Parameters, X509Certificate> cache;

    public GeneratingKeyManager(EblockerCa eblockerCa, KeyPair keyPair, int maxSize, int concurrencyLevel, List<String> defaultNames) {
        this.eblockerCa = eblockerCa;
        this.keyPair = keyPair;
        this.defaultNames = defaultNames;
        cache = CacheBuilder.newBuilder().maximumSize(maxSize).concurrencyLevel(concurrencyLevel).build();
    }

    @Override
    public String[] getClientAliases(String s, Principal[] principals) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getServerAliases(String s, Principal[] principals) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
        SSLSocket sslSocket = (SSLSocket) socket;
        ExtendedSSLSession session = (ExtendedSSLSession) sslSocket.getHandshakeSession();
        return chooseAlias(session);
    }

    @Override
    public X509Certificate[] getCertificateChain(String s) {
        try {
            Parameters parameters = decodeParameters(s);
            X509Certificate certificate = cache.get(parameters, () -> generateCertificate(parameters));
            return new X509Certificate[] { certificate };
        } catch (ExecutionException e) {
            throw new CertificateGenerationException("failed to generate certificate", e);
        }
    }

    @Override
    public PrivateKey getPrivateKey(String s) {
        return keyPair.getPrivate();
    }

    @Override
    public String chooseEngineClientAlias(String[] strings, Principal[] principals, SSLEngine sslEngine) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseEngineServerAlias(String s, Principal[] principals, SSLEngine sslEngine) {
        return chooseAlias((ExtendedSSLSession) sslEngine.getHandshakeSession());
    }

    private String chooseAlias(ExtendedSSLSession session) {
        List<String> names = generateNames(session);
        return encodeParameters(names);
    }

    private List<String> generateNames(ExtendedSSLSession session) {
        if (session.getRequestedServerNames().isEmpty()) {
            return defaultNames;
        }

        List<String> names = new ArrayList<>();
        names.add(((SNIHostName)session.getRequestedServerNames().get(0)).getAsciiName());
        names.addAll(defaultNames);
        return names;
    }

    private class CertificateGenerationException extends RuntimeException {
        CertificateGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private X509Certificate generateCertificate(Parameters parameters) throws CryptoException, IOException {
        CertificateAndKey cak = eblockerCa.generateServerCertificate(parameters.names.get(0), keyPair, eblockerCa.getServerNotValidAfter(), parameters.names);
        return cak.getCertificate();
    }

    private class Parameters {
        List<String> names;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Parameters that = (Parameters) o;
            return names.equals(that.names);
        }

        @Override
        public int hashCode() {
            return names.hashCode();
        }
    }

    private String encodeParameters(List<String> names) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%08x", names.size()));
        for(String name : names) {
            sb.append(String.format("%08x", name.length()));
            sb.append(name);
        }
        return sb.toString();
    }

    private Parameters decodeParameters(String encoded) {
        int size = Integer.parseInt(encoded.substring(0, 8), 16);
        Parameters parameters = new Parameters();
        parameters.names = new ArrayList<>(size);
        int offset = 8;
        for(int i = 0; i < size; ++i) {
            int length = Integer.parseInt(encoded.substring(offset, offset + 8), 16);
            parameters.names.add(encoded.substring(offset + 8, offset + 8 + length));
            offset += 8 + length;
        }
        return parameters;
    }
}
