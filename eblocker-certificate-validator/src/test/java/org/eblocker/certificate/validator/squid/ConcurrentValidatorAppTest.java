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

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConcurrentValidatorAppTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentValidatorAppTest.class);

    private static final boolean useConcurrency = true;
    private static final PipedOutputStream stdinWriter = new PipedOutputStream();
    private static final PipedInputStream stdoutReader = new PipedInputStream();
    private static final Random random = new Random();

    private static CertificateValidationResponseReader responseReader = new CertificateValidationResponseReader(useConcurrency);

    private Semaphore concurrentRequestsSemaphore = new Semaphore(16);

    @BeforeClass
    public static void setUp() throws Exception {
        //
        // Create piped streams as replacement for STDIN and STDOUT
        //
        final PipedInputStream stdinReader = new PipedInputStream(stdinWriter);
        final PipedOutputStream stdoutWriter = new PipedOutputStream(stdoutReader);

        //
        // Start the validator app in it's own thread with the piped streams.
        //
        Thread validator = new Thread(new CertificateValidationRunner(stdinReader, stdoutWriter, useConcurrency));
        validator.start();

    }

    @Test
    public void testRandomConcurrent() throws Exception {
        //
        // Submit some (concurrent) certificate validation requests
        //
        final int n = 100;
        TestData data = generateTestData(n);

        new Thread() {
            @Override
            public void run() {
                LOG.info("waiting for validations ....");
                List<CertificateValidationResponse> responses = new ArrayList<>(n);
                for (int i = 0; i < n; ++i) {
                    responses.add(readResponse());
                }

                for (CertificateValidationResponse response : responses) {
                    assertNotNull(response);

                    CertificateValidationRequest request = data.requestsById.get(response.getId());
                    assertNotNull(request);
                    assertEquals(request.getId(), response.getId());
                    assertEquals(data.validRequests.contains(request), response.isSuccess());
                }
            }
        }.start();

        for (CertificateValidationRequest request : data.requests) {
            submitRequest(request);
        }
    }

    private TestData generateTestData(int n) throws Exception {
        TestData data = new TestData();
        data.requests = new ArrayList<>(n);
        data.validRequests = new HashSet<>(n);
        for (int i = 0; i < n; ++i) {
            CertificateValidationRequest request;
            if (random.nextBoolean()) {
                request = CertificateValidatorTestUtil.createValidRequest(useConcurrency);
                data.validRequests.add(request);
            } else {
                request = CertificateValidatorTestUtil.createInvalidRequest(useConcurrency);
            }
            data.requests.add(request);
        }

        data.requestsById = data.requests.stream().collect(Collectors.toMap(CertificateValidationRequest::getId, Function.identity()));

        return data;
    }

    private void submitRequest(CertificateValidationRequest request) throws IOException, InterruptedException {
        concurrentRequestsSemaphore.acquire();

        byte[] requestAsBytes = request.toString().getBytes(StandardCharsets.UTF_8);
        stdinWriter.write(requestAsBytes);
        stdinWriter.write('\n');
        stdinWriter.flush();
        LOG.info("Request {} written.", request.getId());
    }

    private CertificateValidationResponse readResponse() {
        LOG.debug("Waiting for next response...");
        CertificateValidationResponse response = responseReader.readSquidFormat(stdoutReader);
        concurrentRequestsSemaphore.release();

        LOG.info("Response {} read.", response.getId());

        return response;
    }

    /**
     * Simulates the CertificateValidatorApp running as stand alone app,
     * by providing piped streams as "standard" in and out
     */
    public static class CertificateValidationRunner implements Runnable {

        private final InputStream mockStdin;
        private final OutputStream mockStdout;

        public CertificateValidationRunner(InputStream mockStdin, OutputStream mockStdout, boolean useConcurrency) {
            this.mockStdin = mockStdin;
            this.mockStdout = mockStdout;
        }

        /**
         * Compare this to the main() method of CertificateValidatorApp!
         */
        @Override
        public void run() {
            Properties properties = TestProperties.createTestProperties(true);
            CertificateValidatorApp app = new CertificateValidatorApp(properties);
            app.serve(mockStdin, mockStdout);
        }
    }

    private class TestData {
        public List<CertificateValidationRequest> requests;
        public Map<Long, CertificateValidationRequest> requestsById;
        public Set<CertificateValidationRequest> validRequests;
    }
}
