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
package org.eblocker.server.http.security;

import org.restexpress.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Random;

public class PasswordUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordUtil.class);

    private static final Random RANDOM = new SecureRandom();

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int ITERATIONS = 3;
    private static final int KEY_LENGTH = 32;
    private static final int SALT_LENGTH = 32;

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] hashPassword(String password) {
        return hashPassword(password.toCharArray(), generateSalt());
    }

    public static boolean verifyPassword(String password, byte[] passwordHash) {
        byte[] salt = Arrays.copyOfRange(passwordHash, 0, SALT_LENGTH);
        return Arrays.equals(passwordHash, hashPassword(password.toCharArray(), salt));
    }

    public static byte[] hashPassword(final char[] password, final byte[] salt) {
        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM);
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH * 8);
            SecretKey key = secretKeyFactory.generateSecret(keySpec);
            byte[] saltedHash = new byte[SALT_LENGTH + KEY_LENGTH];
            System.arraycopy(salt, 0, saltedHash, 0, SALT_LENGTH);
            System.arraycopy(key.getEncoded(), 0, saltedHash, SALT_LENGTH, KEY_LENGTH);
            return saltedHash;

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOG.error("Cannot hash password.", e);
            throw new ServiceException("error.credentials.cannotHashPassword");
        }
    }

}
