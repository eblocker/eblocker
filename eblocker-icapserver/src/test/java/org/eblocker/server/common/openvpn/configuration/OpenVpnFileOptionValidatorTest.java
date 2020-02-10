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
package org.eblocker.server.common.openvpn.configuration;

import com.google.common.io.ByteStreams;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.Random;

public class OpenVpnFileOptionValidatorTest {

    private OpenVpnFileOptionValidator validator;

    @Before
    public void setup() {
        validator = new OpenVpnFileOptionValidator("classpath:test-data/vpn/openvpn-test.options.inline");
    }

    @Test
    public void validatePemCertificate() throws Exception {
        validator.validate("option-pem", read("test-data/vpn/certificate.pem"));
    }

    @Test
    public void validatePemPrivateKey() throws Exception {
        validator.validate("option-pem", read("test-data/vpn/private-key.pem"));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validatePemInvalidType() throws Exception {
        validator.validate("option-pem", read("test-data/vpn/invalid-type.pem"));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validatePemInvalidEncoding() throws Exception {
        validator.validate("option-pem", read("test-data/vpn/certificate-invalid-encoding.pem"));
    }

    @Test
    public void validatePemDiffieHellman() throws Exception {
        validator.validate("option-dh-pem", read("test-data/vpn/dh1024.pem"));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validatePemDiffieHellmanInvalidType() throws Exception {
        validator.validate("option-dh-pem", read("test-data/vpn/certificate.pem"));
    }

    @Test
    public void validateOpenVpnStaticKey() throws Exception {
        validator.validate("option-openVpnStaticKey", read("test-data/vpn/openVpnStatic.key"));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validateOpenVpnStaticKeyInvalidEncoding() throws Exception {
        validator.validate("option-openVpnStaticKey", read("test-data/vpn/openVpnStaticInvalid.key"));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validateOpenVpnStaticKeyInvalid() throws Exception {
        validator.validate("option-openVpnStaticKey", read("test-data/vpn/certificate.pem"));
    }

    @Test
    public void validateBase64() throws Exception {
        validator.validate("option-base64", Base64.getEncoder().encode(randomBytes(1024)));
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void validateBase64Invalid() throws Exception {
        validator.validate("option-base64", "NOT BASE 64 !".getBytes());
    }

    @Test
    public void testPasswordValidation() throws Exception {
        validator.validate("auth-user-pass", "username\npassword".getBytes());
        validator.validate("auth-user-pass", "username\npassword\n".getBytes());
        validator.validate("auth-user-pass", "username\npassword\nexcesscontent".getBytes());
        validator.validate("auth-user-pass", "\n\n".getBytes());
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void testPasswordValidationEmpty() throws Exception {
        validator.validate("auth-user-pass", new byte[0]);
    }

    @Test(expected = OpenVpnFileOptionValidator.ValidationException.class)
    public void testPasswordValidationSingleLine() throws Exception {
        validator.validate("auth-user-pass", "\n".getBytes());
    }

    private byte[] read(String path) throws IOException {
        return ByteStreams.toByteArray(OpenVpnFileOptionValidatorTest.class.getClassLoader().getResourceAsStream(path));
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        Random rnd = new Random();
        rnd.nextBytes(bytes);
        return bytes;
    }
}
