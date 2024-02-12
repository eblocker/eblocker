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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;

class CertificateValidatorAppTest {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidatorAppTest.class);

    private static final boolean useConcurrency = false;
    private static final PipedOutputStream stdinWriter = new PipedOutputStream();
    private static final PipedInputStream stdoutReader = new PipedInputStream();

    private static CertificateValidationResponseReader responseReader = new CertificateValidationResponseReader(useConcurrency);

    @BeforeAll
    static void setUp() throws Exception {
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
    void testRandom() throws Exception {
        //
        // Submit some (sequential) certificate validation requests
        //
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            if (random.nextBoolean()) {
                checkValidCert();
            } else {
                checkInvalidCert();
            }
        }
    }

    @Test
    void testCertError() throws Exception {
        checkInvalidCertWithErrors();
    }

    private void checkValidCert() throws Exception {
        CertificateValidationRequest request = CertificateValidatorTestUtil.createValidRequest(useConcurrency);
        submitRequest(request, true);
    }

    private void checkInvalidCert() throws Exception {
        CertificateValidationRequest request = CertificateValidatorTestUtil.createInvalidRequest(useConcurrency);
        submitRequest(request, false);
    }

    private void checkInvalidCertWithErrors() throws Exception {
        CertificateValidationRequest request = CertificateValidatorTestUtil.createInvalidRequestWithErrors(useConcurrency);
        submitRequest(request, false);
    }

    private void submitRequest(CertificateValidationRequest request, boolean valid) throws IOException {
        stdinWriter.write(request.toString().getBytes(StandardCharsets.UTF_8));
        stdinWriter.write('\n');
        stdinWriter.flush();

        LOG.info("Reading response...");
        CertificateValidationResponse response = responseReader.readSquidFormat(stdoutReader);
        LOG.info("Response arrived");
        Assertions.assertTrue(response != null);
        Assertions.assertEquals(valid, response.isSuccess());

    }

    /**
     * Simulates the CertificateValidatorApp running as stand alone app,
     * by providing piped streams as "standard" in and out
     */
    public static class CertificateValidationRunner implements Runnable {

        private final InputStream mockStdin;
        private final OutputStream mockStdout;
        private final boolean useConcurrency;

        public CertificateValidationRunner(InputStream mockStdin, OutputStream mockStdout, boolean useConcurrency) {
            this.mockStdin = mockStdin;
            this.mockStdout = mockStdout;
            this.useConcurrency = useConcurrency;
        }

        /**
         * Compare this to the main() method of CertificateValidatorApp!
         */
        @Override
        public void run() {
            Properties properties = TestProperties.createTestProperties(useConcurrency);
            CertificateValidatorApp app = new CertificateValidatorApp(properties);
            app.serve(mockStdin, mockStdout);
        }
    }
}
