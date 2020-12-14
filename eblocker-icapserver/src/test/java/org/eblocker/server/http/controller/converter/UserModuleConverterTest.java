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
package org.eblocker.server.http.controller.converter;

import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleTransport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UserModuleConverterTest {

    @Test
    public void testNoPin() {
        UserModule user = new UserModule(
            1000,
            99,
            "a-name",
            "a-name-key",
            null, null,
            false,
            null,
            null,
            null,
            null,
            null
        );

        UserModuleTransport userDto = UserModuleConverter.getUserModuleTransport(user);

        assertEquals(user.getId(), userDto.getId());
        assertEquals(user.getAssociatedProfileId(), userDto.getAssociatedProfileId());
        assertEquals(user.getName(), userDto.getName());
        assertEquals(user.getNameKey(), userDto.getNameKey());
        assertEquals(user.isSystem(), userDto.isSystem());
        assertFalse(userDto.containsPin());
        assertNull(userDto.getNewPin());
        assertNull(userDto.getOldPin());
    }

    @Test
    public void testPin() {
        UserModule user = new UserModule(
            1000,
            99,
            "a-name",
            "a-name-key",
            null, null,
            false,
            new byte[]{ 1, 2, 3, 4, 5, 6 },
            null,
            null,
            null,
            null
        );

        UserModuleTransport userDto = UserModuleConverter.getUserModuleTransport(user);

        assertEquals(user.getId(), userDto.getId());
        assertEquals(user.getAssociatedProfileId(), userDto.getAssociatedProfileId());
        assertEquals(user.getName(), userDto.getName());
        assertEquals(user.getNameKey(), userDto.getNameKey());
        assertEquals(user.isSystem(), userDto.isSystem());
        assertTrue(userDto.containsPin());
        assertNull(userDto.getNewPin());
        assertNull(userDto.getOldPin());
    }

}
