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

import org.bouncycastle.util.io.TeeInputStream;
import org.bouncycastle.util.io.TeeOutputStream;
import org.eblocker.certificate.validator.http.DefaultHttpUrlConnectionBuilderFactory;
import org.eblocker.certificate.validator.http.HttpUrlConnectionBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a SSL / TLS validator, which communicates via stdin and stdout with squid (compiled with SSL features).
 * This external validator is called by squid with the whole certificate chain, AFTER it did a validation with OpenSSL.
 * <p>
 * For the specifications of the protocol, that is spoken with squid take a look here:
 * http://wiki.squid-cache.org/Features/SslServerCertValidator
 * Note: Please take a look at the class 'CertificateValidationRequestReader', to get an idea of how the protocol really works (and not how it should work).
 * <p>
 * This standalone program (which is launched by squid) can have one argument:
 * CertificateValidatorApp [useConcurrency: true or false]
 * <p>
 * To really understand the protocol (wiki is wrong), take a look into squid's sourcecode:
 * cert_validate_message.cc,
 * cert_validate_message.h,
 * ResultCode.h,
 * ErrorDetail.h
 * <p>
 * http://www.squid-cache.org/Doc/config/sslcrtvalidator_program/
 * http://www.squid-cache.org/Doc/config/sslcrtvalidator_children/
 */
public class CertificateValidatorApp {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidatorApp.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private static final String DEBUG_MESSAGES_IN_FILE_NAME = "requests.log";
    private static final String DEBUG_MESSAGES_OUT_FILE_NAME = "responses.log";

    private final int serverPort;
    private final boolean useConcurrency;
    private final CertificateValidationRequestReader reader;
    private final CertificateValidator validator;
    private final String debugMessagesPath;

    private final HttpUrlConnectionBuilderFactory connectionBuilderFactory;
    private final CrlCache crlCache;
    private final OcspCache ocspCache;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutorService;

    public CertificateValidatorApp(Properties properties) {
        this.serverPort = Integer.parseInt(properties.getProperty("server.port"));
        this.useConcurrency = Boolean.parseBoolean(properties.getProperty("reader.concurrentFormat"));
        executor = Executors.newCachedThreadPool();

        reader = new CertificateValidationRequestReader(useConcurrency);

        connectionBuilderFactory = new DefaultHttpUrlConnectionBuilderFactory();
        boolean crlCacheEnabled = Boolean.parseBoolean(properties.getProperty("cache.crl.enabled"));
        long crlCacheMaxSize = Long.parseLong(properties.getProperty("cache.crl.maxSize"));
        int crlCacheConcurrencyLevel = Integer.parseInt(properties.getProperty("cache.crl.concurrencyLevel"));
        int crlCacheWritePeriod = Integer.parseInt(properties.getProperty("cache.crl.write.period"));
        int crlCacheRefreshPeriod = Integer.parseInt(properties.getProperty("cache.crl.refresh.period"));
        long crlCacheMaximumAge = Long.parseLong(properties.getProperty("cache.crl.maxAge"));
        String crlCacheFile = properties.getProperty("cache.crl.file");

        CertStore crlCacheCertStore;
        try {
            if (crlCacheEnabled) {
                crlCache = initCache(crlCacheFile, crlCacheMaxSize, crlCacheConcurrencyLevel, crlCacheMaximumAge * 1000 * 60);
                crlCacheCertStore = CertStore.getInstance("CrlCacheStore", new CrlCacheCertStore.Parameters(crlCache));
            } else {
                crlCache = null;
                crlCacheCertStore = CertStore.getInstance("Collection", new CollectionCertStoreParameters());
            }
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("unexpected failure creating cert stores", e);
        }

        boolean ocspCacheEnabled = Boolean.parseBoolean(properties.getProperty("cache.ocsp.enabled"));
        long ocspCacheMaxSize = Long.parseLong(properties.getProperty("cache.ocsp.maxSize"));
        int ocspCacheConcurrencyLevel = Integer.parseInt(properties.getProperty("cache.ocsp.concurrencyLevel"));
        int ocspCacheWritePeriod = Integer.parseInt(properties.getProperty("cache.ocsp.write.period"));
        int ocspCacheRefreshPeriod = Integer.parseInt(properties.getProperty("cache.ocsp.refresh.period"));
        long ocspCacheMaximumAgeSuccess = Long.parseLong(properties.getProperty("cache.ocsp.maxAge.success"));
        long ocspCacheMaximumAgeError = Long.parseLong(properties.getProperty("cache.ocsp.maxAge.error"));
        String ocspCacheFile = properties.getProperty("cache.ocsp.file");
        if (ocspCacheEnabled) {
            ocspCache = initOcspCache(ocspCacheFile, ocspCacheMaxSize, ocspCacheConcurrencyLevel, ocspCacheMaximumAgeSuccess, ocspCacheMaximumAgeError);
        } else {
            ocspCache = null;
        }

        Path certificatesFilePath = Paths.get(properties.getProperty("validator.sun.certificates.path"));
        boolean preferCrls = Boolean.parseBoolean(properties.getProperty("validator.sun.preferCrls"));
        SunCertificateValidator sunCertificateValidator = new SunCertificateValidator(certificatesFilePath, preferCrls, crlCacheCertStore, ocspCache, executor);

        Path intermediateCertificateStoreCertificateFilePath = Paths.get(properties.getProperty("intermediate.certificates.path"));
        long intermediateCertificateStoreRefreshPeriod = Long.parseLong(properties.getProperty("intermediate.certificates.refresh.period"));
        IntermediateCertificatesStore intermediateCertificatesStore = new IntermediateCertificatesStore(intermediateCertificateStoreCertificateFilePath);
        IntermediateProvidingValidator intermediateProvidingValidator = new IntermediateProvidingValidator(sunCertificateValidator, intermediateCertificatesStore);

        Path pinTrustStorePath = Paths.get(properties.getProperty("validator.pin.trustStore.path"));
        String pinTrustStorePassword = properties.getProperty("validator.pin.trustStore.password");
        long pinTrustStoreRefreshPeriod = Long.parseLong(properties.getProperty("validator.pin.refresh.period"));
        PinnedCertificatesStore pinnedCertificatesStore = new PinnedCertificatesStore(pinTrustStorePath, pinTrustStorePassword);
        PinnedCertificateValidator pinnedCertificateValidator = new PinnedCertificateValidator(pinnedCertificatesStore, intermediateProvidingValidator);

        String cacheMaxSizeProperty = properties.getProperty("validator.cache.maxSize");
        Integer cacheMaxSize = Integer.parseInt(properties.getProperty("validator.cache.maxSize"));
        int cacheConcurrencyLevel = Integer.parseInt(properties.getProperty("validator.cache.concurrencyLevel"));
        int cacheTtl = Integer.parseInt(properties.getProperty("validator.cache.ttl"));
        if (cacheMaxSizeProperty != null) {
            validator = new CachingValidator(cacheMaxSize, cacheConcurrencyLevel, cacheTtl, pinnedCertificateValidator);
        } else {
            validator = pinnedCertificateValidator;
        }

        debugMessagesPath = properties.getProperty("debug.path");

        STATUS_LOG.info("=[ configuration ]=====================================================");
        STATUS_LOG.info("server port: {}", serverPort);
        STATUS_LOG.info("concurrent format: {}", useConcurrency);
        STATUS_LOG.info("executor: {}", executor.getClass());
        STATUS_LOG.info("certificates file: {}", certificatesFilePath);
        STATUS_LOG.info("prefer crls: {}", preferCrls);
        STATUS_LOG.info("validator: {}", validator.getClass());
        STATUS_LOG.info("validator cache enabled: {}", cacheMaxSize != null);
        if (cacheMaxSize != null) {
            STATUS_LOG.info("validator cache max size: {}", cacheMaxSize);
            STATUS_LOG.info("validator cache concurrency level: {}", cacheConcurrencyLevel);
            STATUS_LOG.info("validator cache ttl: {}", cacheTtl);
        }
        STATUS_LOG.info("crl cache enabled: {}", crlCacheEnabled);
        if (crlCacheEnabled) {
            STATUS_LOG.info("crl cache size: {}", crlCache.size());
            STATUS_LOG.info("crl cache max size: {}", crlCacheMaxSize);
            STATUS_LOG.info("crl cache concurrency level: {}", crlCacheConcurrencyLevel);
            STATUS_LOG.info("crl cache entry max age: {}", crlCacheMaximumAge);
            STATUS_LOG.info("crl cache refresh period: {}", crlCacheRefreshPeriod);
            STATUS_LOG.info("crl cache write to disk period: {}", crlCacheWritePeriod);
            STATUS_LOG.info("crl cache file: {}", crlCacheFile);
        }
        STATUS_LOG.info("ocsp cache enabled: {}", ocspCacheEnabled);
        if (ocspCacheEnabled) {
            STATUS_LOG.info("ocsp cache size: {}", ocspCache.size());
            STATUS_LOG.info("ocsp cache max size: {}", ocspCacheMaxSize);
            STATUS_LOG.info("ocsp cache concurrency level: {}", ocspCacheConcurrencyLevel);
            STATUS_LOG.info("ocsp cache entry max age success: {}", ocspCacheMaximumAgeSuccess);
            STATUS_LOG.info("ocsp cache entry max age error: {}", ocspCacheMaximumAgeError);
            STATUS_LOG.info("ocsp cache refresh period: {}", ocspCacheRefreshPeriod);
            STATUS_LOG.info("ocsp cache write to disk period: {}", ocspCacheWritePeriod);
            STATUS_LOG.info("ocsp cache file: {}", ocspCacheFile);
        }
        STATUS_LOG.info("-----------------------------------------------------------------------");

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        if (cacheMaxSizeProperty != null) {
            scheduledExecutorService.scheduleAtFixedRate(((CachingValidator) validator)::logStats, 1, 1, TimeUnit.HOURS);
        }

        if (crlCacheEnabled) {
            scheduledExecutorService.scheduleAtFixedRate(new CacheWriter(crlCacheFile, crlCache::writeToStream), crlCacheWritePeriod, crlCacheWritePeriod, TimeUnit.MINUTES);
            scheduledExecutorService.scheduleAtFixedRate(crlCache::logStats, 1, 1, TimeUnit.HOURS);
            scheduledExecutorService.scheduleAtFixedRate(crlCache::refresh, 0, crlCacheRefreshPeriod, TimeUnit.MINUTES);
        }

        if (ocspCacheEnabled) {
            scheduledExecutorService.scheduleAtFixedRate(new CacheWriter(ocspCacheFile, ocspCache::writeToStream), ocspCacheWritePeriod, ocspCacheWritePeriod, TimeUnit.MINUTES);
            scheduledExecutorService.scheduleAtFixedRate(ocspCache::logStats, 1, 1, TimeUnit.HOURS);
            scheduledExecutorService.scheduleAtFixedRate(ocspCache::refresh, 0, ocspCacheRefreshPeriod, TimeUnit.MINUTES);
        }

        scheduledExecutorService.scheduleAtFixedRate(intermediateCertificatesStore::refresh, 0, intermediateCertificateStoreRefreshPeriod, TimeUnit.MINUTES);
        scheduledExecutorService.scheduleAtFixedRate(pinnedCertificatesStore::refresh, 0, pinTrustStoreRefreshPeriod, TimeUnit.MINUTES);
    }

    public static void main(String[] args) {
        try {
            configureSystemProperties();
            CertificateValidatorApp app = new CertificateValidatorApp(loadProperties());
            app.runServer();
        } catch (Exception e) {
            LOG.error("unexpected error, terminating validator", e);
        }
    }

    private static void configureSystemProperties() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
        Security.setProperty("ocsp.enable", "true");
        Security.addProvider(new CertificateValidatorProvider());
    }

    private static Properties loadProperties() throws IOException {
        Properties defaultProperties = new Properties();
        defaultProperties.load(ClassLoader.getSystemResourceAsStream("certificate-validator.properties"));

        Path localPropertiesPath = Paths.get(defaultProperties.getProperty("local.propertiesPath"));
        if (!Files.exists(localPropertiesPath)) {
            return defaultProperties;
        }

        Properties localProperties = new Properties(defaultProperties);
        localProperties.load(Files.newInputStream(localPropertiesPath));
        return localProperties;
    }

    private CrlCache initCache(String fileName, long maximumSize, int concurrencyLevel, long maximumAge) {
        if (Files.exists(Paths.get(fileName))) {
            try {
                FileInputStream in = new FileInputStream(fileName);
                return new CrlCache(maximumSize, concurrencyLevel, maximumAge, Clock.systemUTC(), connectionBuilderFactory, in);
            } catch (IOException e) {
                STATUS_LOG.warn("loading on-disk cache failed", e);
            }
        }
        return new CrlCache(maximumSize, concurrencyLevel, maximumAge, Clock.systemUTC(), connectionBuilderFactory);
    }

    private OcspCache initOcspCache(String fileName, long maximumSize, int concurrencyLevel, long maximumAgeSuccess, long maximumAgeError) {
        if (Files.exists(Paths.get(fileName))) {
            try {
                FileInputStream in = new FileInputStream(fileName);
                return new OcspCache(maximumSize, concurrencyLevel, maximumAgeSuccess, maximumAgeError, Clock.systemUTC(), connectionBuilderFactory, in);
            } catch (IOException e) {
                STATUS_LOG.warn("loading on-disk cache failed", e);
            }
        }
        return new OcspCache(maximumSize, concurrencyLevel, maximumAgeSuccess, maximumAgeError, Clock.systemUTC(), connectionBuilderFactory);
    }

    void runServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(serverPort, 0, InetAddress.getByName("127.0.0.1"));
        STATUS_LOG.info("listening on port {}", serverPort);
        while (true) { // NOSONAR, serve until killed
            try {
                Socket socket = serverSocket.accept();
                STATUS_LOG.info("connection from {}", socket.getRemoteSocketAddress());
                serve(socket.getInputStream(), socket.getOutputStream());
            } catch (SocketException e) {
                STATUS_LOG.warn("socket closed ?", e);
            } catch (IOException e) {
                STATUS_LOG.warn("i/o error", e);
            }
        }
    }

    void serve(InputStream in, OutputStream out) {
        // wrap in / out stream optionally to log all requests / responses
        // TODO: generate unique name
        if (debugMessagesPath != null) {
            try {
                // SONAR "resource-leak" warnings are disabled here because both streams lifetime exceeds this method,
                // so closing them here will be an error.
                in = new TeeInputStream(in, new FileOutputStream(debugMessagesPath + "/" + DEBUG_MESSAGES_IN_FILE_NAME)); //NOSONAR
                out = new TeeOutputStream(out, new FileOutputStream(debugMessagesPath + "/" + DEBUG_MESSAGES_OUT_FILE_NAME)); //NOSONAR
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("could not open file for debug, please check " + debugMessagesPath + " setting", e);
            }
        }

        BlockingQueue<CertificateValidationResponse> responseQueue = new LinkedBlockingQueue<>();
        executor.execute(new Reader(in, responseQueue));
        executor.execute(new Writer(out, responseQueue));
    }

    private class Reader implements Runnable {
        private final InputStream in;
        private final BlockingQueue<CertificateValidationResponse> responseQueue;

        public Reader(InputStream in, BlockingQueue<CertificateValidationResponse> responseQueue) {
            this.in = in;
            this.responseQueue = responseQueue;
        }

        @Override
        public void run() {
            try {
                BufferedReader ioReader = new BufferedReader(new InputStreamReader(in), 32768);
                while (true) {
                    LOG.info("Reading request...");
                    CertificateValidationRequest request = reader.read(ioReader);
                    if (request != null) {
                        LOG.info("Finished reading request.");
                        executor.execute(new Evaluator(request, responseQueue));
                    } else {
                        STATUS_LOG.info("Empty request received, shutting down.");
                        responseQueue.add(new ShutdownMarker());
                        break;
                    }
                }
                STATUS_LOG.info("reader terminated");
                ioReader.close();
            } catch (Exception e) {
                STATUS_LOG.error("unexpected error", e);
            }
        }
    }

    private class Evaluator implements Runnable {
        private final CertificateValidationRequest request;
        private final BlockingQueue<CertificateValidationResponse> responseQueue;

        public Evaluator(CertificateValidationRequest request, BlockingQueue<CertificateValidationResponse> responseQueue) {
            this.request = request;
            this.responseQueue = responseQueue;
        }

        @Override
        public void run() {
            try {
                LOG.info("Validating request {} ...", request.getId());
                long start = System.currentTimeMillis();
                CertificateValidationResponse response = validator.validate(request, useConcurrency);
                long elapsed = System.currentTimeMillis() - start;
                LOG.info("Finished validation {} for {} in {} ms...", request.getId(), request.getCert()[0].getSubjectDN(), elapsed);
                if (response != null) {
                    responseQueue.add(response);
                } else {
                    LOG.error("parsed response was null!");
                }
            } catch (Exception e) {
                STATUS_LOG.error("unexpected error", e);
            }
        }
    }

    private class Writer implements Runnable {
        private final OutputStream out;
        private final BlockingQueue<CertificateValidationResponse> responseQueue;

        public Writer(OutputStream out, BlockingQueue<CertificateValidationResponse> responseQueue) {
            this.out = out;
            this.responseQueue = responseQueue;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    CertificateValidationResponse response = responseQueue.take();
                    if (response instanceof ShutdownMarker) {
                        LOG.info("received shutdown request, terminating");
                        break;
                    }

                    LOG.debug("Result for {}: {}", response.getId(), response.isSuccess());
                    LOG.debug("Sending response:{}", response.toString());
                    out.write(response.toString().getBytes(StandardCharsets.UTF_8));
                    out.write(0x01);
                    out.flush();
                }
            } catch (InterruptedException e) {
                LOG.info("thread interrupted", e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new IllegalStateException("failed to write response, terminating", e);
            }
            LOG.info("writer terminated");
            try {
                out.close();
            } catch (IOException e) {
                STATUS_LOG.warn("failed to close stream", e);
            }
        }
    }

    private class CacheWriter implements Runnable {
        private String fileName;
        private CacheStreamWriter writer;

        public CacheWriter(String fileName, CacheStreamWriter writer) {
            this.fileName = fileName;
            this.writer = writer;
        }

        @Override
        public void run() {
            try (FileOutputStream out = new FileOutputStream(fileName)) {
                writer.writeToStream(out);
            } catch (Exception e) {
                STATUS_LOG.warn("writing cache to disk failed", e);
            }
        }
    }

    private interface CacheStreamWriter {
        void writeToStream(OutputStream out) throws IOException;
    }

    private class ShutdownMarker extends CertificateValidationResponse {
    }
}
