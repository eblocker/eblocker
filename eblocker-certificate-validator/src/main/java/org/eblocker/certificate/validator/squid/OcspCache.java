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
package org.eblocker.certificate.validator.squid;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.io.ByteStreams;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class OcspCache {

    private static final Logger log = LoggerFactory.getLogger(OcspCache.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private final Cache<String, Entry> ocspResponses;
    private final DigestCalculatorProvider digestCalculatorProvider;

    private final long maximumAgeSuccess;
    private final long maximumAgeError;
    private final Clock clock;
    private final HttpUrlConnectionBuilderFactory connectionBuilderFactory;

    public OcspCache(long maximumSize, int concurrencyLevel, long maximumAgeSuccess, long maximumAgeError, Clock clock, HttpUrlConnectionBuilderFactory connectionBuilderFactory) {
        this.maximumAgeSuccess = maximumAgeSuccess;
        this.maximumAgeError = maximumAgeError;
        this.clock = clock;
        this.connectionBuilderFactory = connectionBuilderFactory;
        ocspResponses = CacheBuilder.newBuilder().maximumSize(maximumSize).recordStats().concurrencyLevel(concurrencyLevel).build();
        digestCalculatorProvider = new BcDigestCalculatorProvider();
    }

    public OcspCache(long maximumSize, int concurrencyLevel, long maximumAgeSuccess, long maximumAgeError, Clock clock, HttpUrlConnectionBuilderFactory connectionBuilderFactory, InputStream in) {
        this(maximumSize, concurrencyLevel, maximumAgeSuccess, maximumAgeError, clock, connectionBuilderFactory);
        readEntriesFromStreamAsync(in);
    }

    public byte[] getOcspResponse(X509Certificate certificate, X509Certificate issuerCertificate) {
        try {
            String url = getOcspResponderUrl(certificate);
            if (url == null) {
                log.debug("{} has no ocsp provider", certificate.getSubjectDN());
                return null;
            }

            String key = url + ":" + certificate.getSerialNumber();
            try {
                return ocspResponses.get(key, () -> ocspQuery(url, issuerCertificate, certificate.getSerialNumber())).encodedOcspResponse;
            } catch (ExecutionException e) {
                log.warn("{} ocsp request failed: ", url, e);
                return null;
            }
        } catch (IOException e) {
            log.warn("failed to extract authority access information from certificate {}", certificate.getSubjectDN(), e);
            return null;
        }
    }

    public long size() {
        return ocspResponses.size();
    }

    public void refresh() {
        log.info("starting refresh");
        long start = System.currentTimeMillis();
        long now = clock.millis();
        Iterator<Map.Entry<String, Entry>> i = ocspResponses.asMap().entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Entry> e = i.next();
            if (e.getValue().expires < now) {
                log.debug("{} expired", e.getKey());
                i.remove();
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        STATUS_LOG.info("finished refresh in {}ms", elapsed);
    }

    public void logStats() {
        CacheStats stats = ocspResponses.stats();
        STATUS_LOG.info("size: {} hits: {} loads: {} miss: {} evictions: {}", ocspResponses.size(), stats.hitCount(), stats.loadCount(), stats.missCount(), stats.evictionCount());
    }

    public void writeToStream(OutputStream out) throws IOException {
        DataOutputStream oout = new DataOutputStream(out);
        for (Map.Entry<String, Entry> e : ocspResponses.asMap().entrySet()) {
            oout.writeBoolean(true);
            oout.writeUTF(e.getKey());
            oout.writeLong(e.getValue().expires);
            oout.writeInt(e.getValue().encodedOcspResponse.length);
            oout.write(e.getValue().encodedOcspResponse);
        }
        oout.writeBoolean(false);
    }

    private void readEntriesFromStreamAsync(InputStream in) {
        new Thread(() -> {
            try {
                readEntriesFromStream(in);
            } catch (IOException e) {
                STATUS_LOG.error("failed restoring entries", e);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    STATUS_LOG.error("closing stream failed");
                }
            }
        }).start();
    }

    private void readEntriesFromStream(InputStream in) throws IOException {
        int readEntries = 0;
        DataInputStream oin = new DataInputStream(in);
        boolean hasEntry = oin.readBoolean();
        while (hasEntry) {
            String key = oin.readUTF();
            long expires = oin.readLong();
            int length = oin.readInt();
            byte[] value = new byte[length];
            int readBytes = oin.read(value);
            if (readBytes != length) {
                throw new IOException("expected " + length + " bytes but got " + readBytes);
            }
            ocspResponses.put(key, new Entry(value, expires));
            hasEntry = oin.readBoolean();
            ++readEntries;
        }
        STATUS_LOG.info("loaded {} cached crls", readEntries);
    }

    private Entry ocspQuery(String url, X509Certificate issuerCertificate, BigInteger serialNumber) throws OcspException {
        long start = System.currentTimeMillis();
        OCSPReq request = createOcspRequest(issuerCertificate, serialNumber);
        long requestCreated = System.currentTimeMillis();
        byte[] response = issueOcspRequest(url, request);
        long requestIssued = System.currentTimeMillis();
        Entry entry = createEntry(response);
        long stop = System.currentTimeMillis();
        log.debug("ocsp query ({}) for {}: {} took {}ms  (creation: {}ms request: {}ms entry mapping: {}", response[0], url, serialNumber, (stop - start), (requestCreated - start), (requestIssued - requestCreated), (stop - requestIssued));
        return entry;
    }

    private String getOcspResponderUrl(X509Certificate certificate) throws IOException {
        byte[] extensionValue = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
        if (extensionValue == null) {
            return null;
        }

        ASN1OctetString octetString = ASN1OctetString.getInstance(extensionValue);
        ASN1Primitive primitive = ASN1Primitive.fromByteArray(octetString.getOctets());
        AuthorityInformationAccess authorityInformationAccessExtension = AuthorityInformationAccess.getInstance(primitive);
        for (AccessDescription i : authorityInformationAccessExtension.getAccessDescriptions()) {
            if (i.getAccessMethod().equals(OCSPObjectIdentifiers.id_pkix_ocsp)) {
                return i.getAccessLocation().getName().toString();
            }
        }

        return null;
    }

    private OCSPReq createOcspRequest(X509Certificate issuerCertificate, BigInteger serialNumber) throws OcspException {
        try {
            X509CertificateHolder holder = new X509CertificateHolder(issuerCertificate.getEncoded());
            CertificateID id = new CertificateID(digestCalculatorProvider.get(CertificateID.HASH_SHA1), holder, serialNumber);
            return new OCSPReqBuilder().addRequest(id).build();
        } catch (CertificateEncodingException | OperatorCreationException | IOException | OCSPException e) {
            throw new OcspException("creating ocsp request failed: ", e);
        }
    }

    private byte[] issueOcspRequest(String url, OCSPReq request) throws OcspException {
        HttpURLConnection connection = null;
        try {
            connection = connectionBuilderFactory.create()
                    .setUrl(url)
                    .setRequestProperty("Content-Type", "application/ocsp-request")
                    .setRequestProperty("Accept", "application/ocsp-response")
                    .post(request.getEncoded());

            if (connection.getResponseCode() != 200) {
                throw new OcspException("http request failed: " + connection.getResponseCode());
            }

            return ByteStreams.toByteArray(connection.getInputStream());
        } catch (IOException e) {
            throw new OcspException("http request i/o error", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Entry createEntry(byte[] b) throws OcspException {
        try {
            OCSPResp ocspResponse = new OCSPResp(b);
            if (ocspResponse.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
                return new Entry(ocspResponse.getEncoded(), clock.millis() + maximumAgeError * 1000);
            }

            BasicOCSPResp response = (BasicOCSPResp) ocspResponse.getResponseObject();
            SingleResp[] responses = response.getResponses();
            if (responses.length != 1) {
                log.warn("expected only 1 response but got {}", responses.length);
                if (responses.length == 0) {
                    throw new OcspException("no response found");
                }
            }

            return new Entry(b, Math.min(responses[0].getNextUpdate().getTime(), clock.millis() + maximumAgeSuccess * 1000));
        } catch (IOException | OCSPException e) {
            throw new OcspException("failed to parse response", e);
        }
    }

    private class OcspException extends Exception {
        public OcspException(String message) {
            super(message);
        }

        public OcspException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private class Entry {
        private byte[] encodedOcspResponse;
        private long expires;

        public Entry(byte[] encodedOcspResponse, long expires) {
            this.encodedOcspResponse = encodedOcspResponse;
            this.expires = expires;
        }
    }
}
