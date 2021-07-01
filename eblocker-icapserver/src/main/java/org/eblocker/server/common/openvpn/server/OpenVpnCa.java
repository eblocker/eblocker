/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.openvpn.server;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.crypto.pki.RevocationInfo;
import org.eblocker.crypto.pki.RevocationReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenVpnCa {
    private static final String CERT_EXTENSION = ".crt";
    private static final String KEY_EXTENSION = ".key";
    private static final String CA_CERT_FILE_NAME = "ca.crt";
    private static final String CA_KEY_FILE_NAME = "ca.key";
    private static final String CRL_FILE_NAME = "crl.pem";
    private static final String SERVER_CERT_FILE_NAME = "eblocker.crt";
    private static final String SERVER_KEY_FILE_NAME = "eblocker.key";

    private static final String ORG_NAME = "eBlocker Open Source";

    private static final String CA_ROOT_NAME = "eBlocker Mobile CA";
    private static final int CA_VALIDITY_YEARS = 10;
    private static final int CA_KEY_SIZE = 2048;

    private static final String SERVER_NAME = "eBlocker Mobile Server";
    private static final int SERVER_VALIDITY_DAYS = 10 * 365;
    private static final int SERVER_KEY_SIZE = 2048;

    private static final int CLIENT_VALIDITY_DAYS = 10 * 365;
    private static final int CLIENT_KEY_SIZE = 2048;

    private static final int CRL_VALIDITY_DAYS = 10 * 365;

    private static final Logger log = LoggerFactory.getLogger(OpenVpnCa.class);

    private Path basePath;
    private Path clientsPath;

    @Inject
    public OpenVpnCa(@Named("openvpn.server.ca.path") String caPath) {
        basePath = Paths.get(caPath);
        clientsPath = basePath.resolve("clients");
    }

    public void generateCa() throws CryptoException, IOException {
        CertificateAndKey caKeys = PKI.generateRoot(ORG_NAME, CA_ROOT_NAME, CA_VALIDITY_YEARS, CA_KEY_SIZE);
        storeCaKeys(caKeys);

        // Create empty initial CRL
        X509CRL crl = PKI.generateCrl(Collections.emptyList(), caKeys, getNextCrlUpdate());
        PKI.storeCrl(crl, outstream(getCrlPath()));

        log.info("Created " + CA_ROOT_NAME);
    }

    public void generateServerCertificate() throws CryptoException, IOException {
        CertificateAndKey caKeys = loadCaKeys();
        CertificateAndKey clientReq = PKI.generateSelfSignedCertificateRequest(SERVER_NAME, SERVER_KEY_SIZE);
        Date notAfter = getDateAfterDays(SERVER_VALIDITY_DAYS);
        X509Certificate clientCert = PKI.generateTLSServerCertificate(clientReq.getCertificate(), ORG_NAME, SERVER_NAME, notAfter, caKeys);
        PKI.storePrivateKey(clientReq.getKey(), outstream(getServerKeyPath()));
        PKI.storeCertificate(clientCert, outstream(getServerCertificatePath()));
        log.info("Issued certificate for " + SERVER_NAME);
    }

    public void generateClientCertificate(String clientId) throws CryptoException, IOException {
        CertificateAndKey caKeys = loadCaKeys();
        CertificateAndKey clientReq = PKI.generateSelfSignedCertificateRequest(clientId, CLIENT_KEY_SIZE);
        Date notAfter = getDateAfterDays(CLIENT_VALIDITY_DAYS);
        X509Certificate clientCert = PKI.generateTLSClientCertificate(clientReq.getCertificate(), ORG_NAME, clientId, notAfter, caKeys);
        PKI.storePrivateKey(clientReq.getKey(), outstream(getClientKeyPath(clientId)));
        PKI.storeCertificate(clientCert, outstream(getClientCertificatePath(clientId)));
        log.info("Issued certificate for client " + clientId);
    }

    /**
     * Read current CRL and add the given certificate.
     *
     * @param clientId
     * @throws CryptoException
     * @throws IOException
     */
    public synchronized void revokeClientCertificate(String clientId) throws CryptoException, IOException {
        CertificateAndKey caKeys = loadCaKeys();
        Path certificatePath = getClientCertificatePath(clientId);
        Path keyPath = getClientKeyPath(clientId);
        X509Certificate clientCert = PKI.loadCertificate(instream(certificatePath));
        BigInteger clientSerial = clientCert.getSerialNumber();
        Set<RevocationInfo> existingRevocations = readCrlEntries();
        List<RevocationInfo> allRevocations = new ArrayList<RevocationInfo>(existingRevocations.size() + 1);
        allRevocations.addAll(existingRevocations);
        allRevocations.add(new RevocationInfo(clientSerial, new Date(), RevocationReason.REVOKED));
        X509CRL crl = PKI.generateCrl(allRevocations, caKeys, getNextCrlUpdate());
        PKI.storeCrl(crl, outstream(getCrlPath()));
        certificatePath.toFile().delete();
        keyPath.toFile().delete();
        log.info("Revoked certificate for client " + clientId);
    }

    /**
     * Destroy the CA and all keys and certificates
     */
    public void tearDown() {
        log.info("Removing " + CA_ROOT_NAME);
        getCaCertificatePath().toFile().delete();
        getCaKeyPath().toFile().delete();
        getCrlPath().toFile().delete();
        getServerKeyPath().toFile().delete();
        getServerCertificatePath().toFile().delete();
        deleteAllClientFiles();
    }

    /**
     * Returns IDs of all clients that currently have a certificate which has not been revoked
     */
    public Set<String> getActiveClientIds() throws IOException {
        Set<String> result = new HashSet<>();
        Files.newDirectoryStream(clientsPath, "*" + CERT_EXTENSION)
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String basename = filename.substring(0, filename.length() - CERT_EXTENSION.length());
                    result.add(basename);
                });
        return result;
    }

    public Path getCaCertificatePath() {
        return basePath.resolve(CA_CERT_FILE_NAME);
    }

    private Path getCaKeyPath() {
        return basePath.resolve(CA_KEY_FILE_NAME);
    }

    public Path getCrlPath() {
        return basePath.resolve(CRL_FILE_NAME);
    }

    public Path getClientCertificatePath(String clientId) {
        return clientsPath.resolve(clientId + CERT_EXTENSION);
    }

    public Path getClientKeyPath(String clientId) {
        return clientsPath.resolve(clientId + KEY_EXTENSION);
    }

    public Path getServerCertificatePath() {
        return basePath.resolve(SERVER_CERT_FILE_NAME);
    }

    public Path getServerKeyPath() {
        return basePath.resolve(SERVER_KEY_FILE_NAME);
    }

    private OutputStream outstream(Path path) throws FileNotFoundException {
        return new FileOutputStream(path.toFile());
    }

    private InputStream instream(Path path) throws FileNotFoundException {
        return new FileInputStream(path.toFile());
    }

    private Date getDateAfterDays(int days) {
        return Date.from(Instant.now().plus(Duration.ofDays(days)));
    }

    private Date getNextCrlUpdate() {
        return getDateAfterDays(CRL_VALIDITY_DAYS);
    }

    private void storeCaKeys(CertificateAndKey keys) throws CryptoException, IOException {
        PKI.storePrivateKey(keys.getKey(), outstream(getCaKeyPath()));
        PKI.storeCertificate(keys.getCertificate(), outstream(getCaCertificatePath()));
    }

    private CertificateAndKey loadCaKeys() throws CryptoException, IOException {
        X509Certificate cert = PKI.loadCertificate(instream(getCaCertificatePath()));
        PrivateKey key = PKI.loadPrivateKey(instream(getCaKeyPath()));
        return new CertificateAndKey(cert, key);
    }

    // Ensure that all CRL entries have a revocation reason
    private Set<RevocationInfo> readCrlEntries() throws CryptoException, IOException {
        X509CRL crl = PKI.loadCrl(instream(getCrlPath()));
        Set<RevocationInfo> entries = PKI.getRevocationInfoEntries(crl);
        return entries.stream()
                .map(OpenVpnCa::ensureRevocationReason)
                .collect(Collectors.toSet());
    }

    private static RevocationInfo ensureRevocationReason(RevocationInfo entry) {
        if (entry.getRevocationReason() != null) {
            return entry;
        } else {
            return new RevocationInfo(entry.getSerialNumber(), entry.getRevocationDate(), RevocationReason.REVOKED);
        }
    }

    private void deleteAllClientFiles() {
        try {
            Files.newDirectoryStream(clientsPath)
                    .forEach(path -> {
                        path.toFile().delete();
                    });
        } catch (IOException e) {
            log.error("Could not delete client keys/certificates", e);
        }
    }
}
