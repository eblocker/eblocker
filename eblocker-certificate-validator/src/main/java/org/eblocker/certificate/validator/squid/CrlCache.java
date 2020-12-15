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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.time.Clock;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CrlCache {
    private static final Logger log = LoggerFactory.getLogger(CrlCache.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");
    private final Clock clock;
    private final HttpUrlConnectionBuilderFactory connectionBuilderFactory;
    private final long maximumAge;
    private final LoadingCache<String, Entry> cache;

    public CrlCache(long maximumSize, int concurrencyLevel, long maximumAge, Clock clock, HttpUrlConnectionBuilderFactory connectionBuilderFactory) {
        this.maximumAge = maximumAge;
        this.clock = clock;
        this.connectionBuilderFactory = connectionBuilderFactory;

        cache = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .concurrencyLevel(concurrencyLevel)
                .removalListener(notification -> log.info("evicted entry: {}", notification.getKey()))
                .recordStats()
                .build(new CacheLoader<String, Entry>() {
                    @Override
                    public Entry load(String url) throws Exception {
                        return downloadCrl(url, null);
                    }
                });
    }

    public CrlCache(long maximumSize, int concurrencyLevel, long maximumAge, Clock clock, HttpUrlConnectionBuilderFactory connectionBuilderFactory, InputStream in) {
        this(maximumSize, concurrencyLevel, maximumAge, clock, connectionBuilderFactory);
        readEntriesFromStreamAsync(in);
    }

    public CRL get(String url) {
        try {
            return cache.get(url).crl;
        } catch (ExecutionException e) {
            log.warn("failed to load crl from {}", url, e);
            return null;
        }
    }

    public long size() {
        return cache.size();
    }

    public void refresh() {
        log.info("starting refresh");
        long start = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> i = cache.asMap().entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Entry> e = i.next();
            if (!refreshEntry(e.getKey(), e.getValue(), clock.millis())) {
                i.remove();
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        STATUS_LOG.info("finished refresh in {}ms", elapsed);
    }

    private boolean refreshEntry(String url, Entry e, long now) {
        X509CRL crl = (X509CRL) e.crl;
        if (now > crl.getNextUpdate().getTime() || now > e.lastCheck + maximumAge) {
            log.debug("refreshing {} (next update: {}, last modified: {}, next check: {})", url, crl.getNextUpdate(), new Date(e.lastModified), new Date(now + maximumAge));
            try {
                Entry newEntry = downloadCrl(url, e.lastModified);
                if (newEntry == null) {
                    log.debug("entry {} is unmodified", url);
                    e.lastCheck = clock.millis();
                    return true;
                }
                e.lastCheck = newEntry.lastCheck;
                e.lastModified = newEntry.lastModified;
                e.crl = newEntry.crl;
                log.info("refreshed {} (next update: {}, last modified: {}, next check: {})", url, crl.getNextUpdate(), new Date(e.lastModified), new Date(now + maximumAge));
                return true;
            } catch (Exception ex) {
                log.warn("refreshing crl {} failed", ex);
                return false;
            }
        }
        log.debug("entry {} needs no check (next update: {}, last modified: {}, next check: {})", url, crl.getNextUpdate(), new Date(e.lastModified), new Date(now + maximumAge));
        return true;
    }

    public void logStats() {
        CacheStats stats = cache.stats();
        STATUS_LOG.info("size: {} hits: {} loads: {} miss: {} evictions: {}", cache.size(), stats.hitCount(), stats.loadCount(), stats.missCount(), stats.evictionCount());
    }

    public void writeToStream(OutputStream out) throws IOException {
        DataOutputStream oout = new DataOutputStream(out);
        for (Map.Entry<String, Entry> e : cache.asMap().entrySet()) {
            oout.writeBoolean(true);
            oout.writeUTF(e.getKey());
            oout.writeLong(e.getValue().lastCheck);
            oout.writeLong(e.getValue().lastModified);
            X509CRL x509Crl = (X509CRL) e.getValue().crl;
            try {
                byte[] encodedCrl = x509Crl.getEncoded();
                oout.writeInt(encodedCrl.length);
                oout.write(encodedCrl);
            } catch (CRLException ex) {
                log.warn("failed to serialize crl for {}", x509Crl.getIssuerDN(), ex);
                throw new IOException("failed to serialize crl ", ex);
            }
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
        DataInputStream oin = new DataInputStream(in);
        try {
            boolean hasEntry = oin.readBoolean();
            while (hasEntry) {
                String key = oin.readUTF();
                long lastCheck = oin.readLong();
                long lastModified = oin.readLong();
                int length = oin.readInt();
                byte[] encodedValue = new byte[length];
                int readBytes = oin.read(encodedValue);
                if (readBytes != length) {
                    throw new IOException("expected " + length + " bytes but got " + readBytes);
                }
                log.debug("found {} bytes for entry {}", length, key);
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                CRL crl = certificateFactory.generateCRL(new ByteArrayInputStream(encodedValue));
                cache.put(key, new Entry(lastCheck, lastModified, crl));
                hasEntry = oin.readBoolean();
            }
            STATUS_LOG.info("loaded {} cached crls", cache.size());
        } catch (GeneralSecurityException e) {
            throw new IOException("crl deserialization failed", e);
        }
    }

    private Entry downloadCrl(String url, Long lastModified) throws IOException, CRLException, CertificateException {
        HttpURLConnection connection = null;
        try {
            log.debug("downloading crl: {}", url);
            connection = connectionBuilderFactory.create()
                    .setUrl(url)
                    .setIfModifiedSince(lastModified)
                    .get();

            if (connection.getResponseCode() == 304) {
                return null;
            }

            if (connection.getResponseCode() != 200) {
                throw new IOException("downloading crl " + url + " failed: " + connection.getResponseCode());
            }

            long lastModifiedResponse = connection.getLastModified();
            if (lastModifiedResponse == 0) {
                lastModifiedResponse = clock.millis();
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            CRL crl = certificateFactory.generateCRL(connection.getInputStream());
            return new Entry(clock.millis(), lastModifiedResponse, crl);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private class Entry {
        private long lastCheck;
        private long lastModified;
        private CRL crl;

        public Entry(long lastCheck, long lastModified, CRL crl) {
            this.lastCheck = lastCheck;
            this.lastModified = lastModified;
            this.crl = crl;
        }
    }

}
