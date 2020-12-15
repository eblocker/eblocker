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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class OpenVpnFileOptionValidator {

    private Map<String, String> expectedFormatByOption = new HashMap<>();

    @Inject
    public OpenVpnFileOptionValidator(@Named("openvpn.configuration.options.file") String fileOptions) {
        expectedFormatByOption = readExpectedFormats(fileOptions);
        expectedFormatByOption.put("auth-user-pass", "usernamePassword");
    }

    public void validate(String option, byte[] content) throws ValidationException {
        String format = expectedFormatByOption.get(option);
        if (format == null) {
            throw new ValidationException("format for option " + option + " unknown!");
        }

        switch (format) {
            case "pem":
                validatePem(content);
                break;
            case "dh-pem":
                validateDhPem(content);
                break;
            case "base64":
                validateBase64(content);
                break;
            case "openVpnStaticKey":
                validateOpenVpnKey(content);
                break;
            case "usernamePassword":
                validateUsernamePassword(content);
                break;
            default:
                throw new ValidationException("format " + format + " for option " + option + " unknown!");
        }
    }

    private Map<String, String> readExpectedFormats(String fileOptions) {
        return ResourceHandler.readLinesAsSet(new SimpleResource(fileOptions)).stream()
                .map(l -> l.split(";"))
                .collect(Collectors.toMap(t -> t[0], t -> t[1]));
    }

    private void validatePem(byte[] content) throws ValidationException {
        List<Object> objects = new ArrayList<>();
        try (PEMParser parser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(content)))) {
            Object object = parser.readObject();
            if (object != null) {
                objects.add(object);
            }
        } catch (DecoderException e) {
            throw new ValidationException("encoding error", e);
        } catch (IOException e) {
            throw new ValidationException("no pem", e);
        }
        if (objects.isEmpty()) {
            throw new ValidationException("pem contains no certificate");
        }
    }

    private void validateDhPem(byte[] content) throws ValidationException {
        try (PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(content)))) {
            PemObject object = reader.readPemObject();
            if (!"DH PARAMETERS".equals(object.getType())) {
                throw new ValidationException("pem contains no dh parameter");
            }
        } catch (IOException e) {
            throw new ValidationException("no dh pem", e);
        }
    }

    private void validateBase64(byte[] content) throws ValidationException {
        try {
            Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("no base64", e);
        }
    }

    private void validateOpenVpnKey(byte[] content) throws ValidationException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content)))) {
            String line;
            while ((line = reader.readLine()) != null && !"-----BEGIN OpenVPN Static key V1-----".equals(line)) {
                // skip lines until start of key
            }

            if (line == null) {
                throw new ValidationException("no openvpn static key");
            }

            while ((line = reader.readLine()) != null && !"-----END OpenVPN Static key V1-----".equals(line)) {
                if (!line.matches("[0-9a-fA-F]*")) {
                    throw new ValidationException("invalid openvpn static key encoding");
                }
            }

            if (line == null) {
                throw new ValidationException("incomplete openvpn static key");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ValidationException("failed to validate openvpn static key", e);
        }
    }

    private void validateUsernamePassword(byte[] content) throws ValidationException {
        // username/password files must contain at least two line containing the username and password
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content)))) {
            String username = reader.readLine();
            String password = reader.readLine();
            if (username == null || password == null) {
                throw new ValidationException("missing username or password");
            }
        } catch (IOException e) {
            throw new ValidationException("no username/password file", e);
        }
    }

    public class ValidationException extends IOException {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
