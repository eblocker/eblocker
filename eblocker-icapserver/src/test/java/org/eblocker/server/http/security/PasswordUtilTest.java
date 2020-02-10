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

import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordUtilTest {

    @Test
    public void test() {

        String password = "Hello World";
        byte[] salt = PasswordUtil.generateSalt();

        byte[] hash1 = PasswordUtil.hashPassword(password.toCharArray(), salt);
        byte[] hash2 = PasswordUtil.hashPassword(password.toCharArray(), salt);

        assertArrayEquals(hash1, hash2);

        assertTrue(PasswordUtil.verifyPassword(password, hash1));
        assertTrue(PasswordUtil.verifyPassword(password, hash2));

        assertFalse(PasswordUtil.verifyPassword(password+"x", hash2));
        assertFalse(PasswordUtil.verifyPassword("x", hash2));
        assertFalse(PasswordUtil.verifyPassword("", hash2));
     }
}