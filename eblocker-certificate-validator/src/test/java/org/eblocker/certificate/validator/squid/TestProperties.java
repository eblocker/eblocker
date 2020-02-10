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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Properties;

class TestProperties {

    static String getCaCertificatesFilePath() {
        try {
            return Paths.get(ClassLoader.getSystemResource("ca-certificates.crt").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("test ca-certificates configured wrong");
        }
    }

    static Properties createTestProperties(boolean readerConcurrentFormat) {
        try {
            Properties properties = new Properties();
            properties.load(TestProperties.class.getClassLoader().getResourceAsStream("certificate-validator.properties"));
            properties.setProperty("reader.concurrentFormat", Boolean.toString(readerConcurrentFormat));
            properties.setProperty("validator.sun.certificates.path", getCaCertificatesFilePath());
            properties.setProperty("intermediate.certificates.path",  "/dev/null");
            properties.setProperty("intermediate.certificates.refresh.period",  "60");
            properties.setProperty("validator.pin.trustStore.path", "/non-existing");
            properties.setProperty("validator.pin.trustStore.password", "");
            properties.setProperty("validator.pin.refresh.period", "60");
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("test setup failure", e);
        }
    }

}
