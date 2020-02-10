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

import org.eblocker.server.common.data.UserModuleOld;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserProfileModule;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class SchemaMigrationVersion10Test {
	private DataSource dataSource;
	private Jedis jedis;
	private SchemaMigrationVersion10 migration;
	private UserMigrationService userMigrationService;

	private UserModuleOld expectedLimboUser;
	private UserProfileModule expectedLimboProfile;
	private Device expectedDeviceA, expectedDeviceB;

	@Before
	public void setup() {
		dataSource = Mockito.mock(DataSource.class);
		userMigrationService = Mockito.mock(UserMigrationService.class);
        Mockito.when(dataSource.nextId(UserModuleOld.class))
                .thenReturn(DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE + 1);

		jedis = Mockito.mock(Jedis.class);
		JedisPool jedisPool = Mockito.mock(JedisPool.class);
		Mockito.when(jedisPool.getResource()).thenReturn(jedis);

		migration = new SchemaMigrationVersion10(dataSource, userMigrationService);

		// Expected objects
		expectedLimboUser = SchemaMigrationVersion10.createLimboUserModule();
		expectedLimboProfile = SchemaMigrationVersion10.createLimboProfile();

		// Existing devices
		Set<Device> devices = new HashSet<>();
		Device deviceA = new Device();
		deviceA.setAssignedUser(1234);
		devices.add(deviceA);
		Device deviceB = new Device();
		deviceB.setAssignedUser(4711);
		devices.add(deviceB);

		Mockito.when(dataSource.getDevices()).thenReturn(devices);

		// Expected devices
		expectedDeviceA = new Device();
		expectedDeviceA.setAssignedUser(1234);
		expectedDeviceA.setOperatingUser(1234);
		expectedDeviceB = new Device();
		expectedDeviceB.setAssignedUser(4711);
		expectedDeviceB.setOperatingUser(4711);
	}

	@Test
	public void getSourceVersion() {
		Assert.assertEquals("9", migration.getSourceVersion());
	}

	@Test
	public void getTargetVersion() {
		Assert.assertEquals("10", migration.getTargetVersion());
	}

	@Test
	public void testLimboUserCreated() {
		// run migration
		migration.migrate();

		// verify migration has been run correctly

		// verify limbo profile was stored
		Mockito.verify(dataSource).save(expectedLimboProfile,
				expectedLimboProfile.getId());
		// verify limbo user was stored
		Mockito.verify(userMigrationService).save(expectedLimboUser,
				expectedLimboUser.getId());

		// Verify version has been updated correctly
		Mockito.verify(dataSource).setVersion("10");
	}

	@Test
	public void testOperatingUserSet(){
		// run migration
		migration.migrate();

		// verify migration has been run correctly
		Mockito.verify(dataSource).save(expectedDeviceA);
		Mockito.verify(dataSource).save(expectedDeviceB);

	}

}
