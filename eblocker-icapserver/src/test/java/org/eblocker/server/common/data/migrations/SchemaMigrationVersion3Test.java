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
package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserProfileModule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class SchemaMigrationVersion3Test {

    private DataSource dataSource;
    private JedisPool jedisPool;
    private Jedis jedis;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    public void getSourceVersion() throws Exception {
        SchemaMigrationVersion3 migration = new SchemaMigrationVersion3(dataSource, jedisPool);
        assertEquals("2", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        SchemaMigrationVersion3 migration = new SchemaMigrationVersion3(dataSource, jedisPool);
        assertEquals("3", migration.getTargetVersion());
    }

    @Test
    public void testBuiltinProfiles() {
        UserProfileModule defaultProfile = SchemaMigrationVersion3.createDefaultProfile();
        assertEquals(true, defaultProfile.isBuiltin());
        assertEquals(false, defaultProfile.isControlmodeTime());
        assertEquals(false, defaultProfile.isControlmodeUrls());
        assertEquals(UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, defaultProfile.getInternetAccessRestrictionMode());
        assertEquals(Integer.valueOf(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID), defaultProfile.getId());

        UserProfileModule fullProfile = SchemaMigrationVersion3.createFullProfile();
        assertEquals(true, fullProfile.isBuiltin());
        assertEquals(false, fullProfile.isControlmodeTime());
        assertEquals(true, fullProfile.isControlmodeUrls());
        assertEquals(UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, fullProfile.getInternetAccessRestrictionMode());
        assertEquals(Integer.valueOf(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID), fullProfile.getId());

        UserProfileModule mediumProfile = SchemaMigrationVersion3.createMediumProfile();
        assertEquals(true, mediumProfile.isBuiltin());
        assertEquals(true, mediumProfile.isControlmodeTime());
        assertEquals(true, mediumProfile.isControlmodeUrls());
        assertEquals(UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, mediumProfile.getInternetAccessRestrictionMode());
        assertEquals(Integer.valueOf(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID), mediumProfile.getId());

        UserProfileModule lowProfile = SchemaMigrationVersion3.createLowProfile();
        assertEquals(true, lowProfile.isBuiltin());
        assertEquals(true, lowProfile.isControlmodeTime());
        assertEquals(true, lowProfile.isControlmodeUrls());
        assertEquals(UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, lowProfile.getInternetAccessRestrictionMode());
        assertEquals(Integer.valueOf(DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID), lowProfile.getId());
    }

    @Test
    public void testProfilesAreSaved() {
        SchemaMigrationVersion3 migration = new SchemaMigrationVersion3(dataSource, jedisPool);
        migration.migrate();

        Mockito.verify(dataSource, times(1)).save(any(UserProfileModule.class), ArgumentMatchers.eq(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID));
        Mockito.verify(dataSource, times(1)).save(any(UserProfileModule.class), ArgumentMatchers.eq(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID));
        Mockito.verify(dataSource, times(1)).save(any(UserProfileModule.class), ArgumentMatchers.eq(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID));
        Mockito.verify(dataSource, times(1)).save(any(UserProfileModule.class), ArgumentMatchers.eq(DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID));

        Mockito.verify(dataSource).setVersion("3");
        Mockito.verify(dataSource).setIdSequence(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE);
    }

    @Test
    public void testDevicesAreUpdated() {
        Set<Device> deviceStore = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Device tmpDevice = new Device();
            tmpDevice.setId(String.valueOf(i));
            deviceStore.add(tmpDevice);
        }

        when(dataSource.getDevices()).thenReturn(deviceStore);

        SchemaMigrationVersion3 migration = new SchemaMigrationVersion3(dataSource, jedisPool);
        migration.migrate();

        Mockito.verify(dataSource).getDevices();
        for (Device device : deviceStore) {
            Mockito.verify(jedis).hset(device.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID, String.valueOf(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID));
        }
    }

}
