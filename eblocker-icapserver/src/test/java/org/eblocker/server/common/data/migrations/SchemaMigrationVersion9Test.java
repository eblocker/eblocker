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

import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserProfileModule.InternetAccessRestrictionMode;

public class SchemaMigrationVersion9Test {
	private DataSource dataSource;
	private Jedis jedis;
	private SchemaMigrationVersion9 migration;

    private UserMigrationService userMigrationService;

	private UserModuleOld expectedStandardUser, expectedFullProfileUser,
			expectedMediumProfileUser, expectedLowProfileUser, expectedUser,
			unexpectedUser;
	private UserProfileModule defaultProfile, fullProfile, mediumProfile, lowProfile, usedCustomerCreatedProfile;
	private Device deviceDefaultProfile, expectedDeviceDefaultProfile,
			deviceFullProfile, expectedDeviceFullProfile, deviceMediumProfile,
			expectedDeviceMediumProfile, deviceLowProfile,
			expectedDeviceLowProfile, deviceCustomerCreatedProfile,
			expectedDeviceCustomerCreatedProfile;

	@Before
	public void setup() {
		dataSource = Mockito.mock(DataSource.class);
		Mockito.when(dataSource.nextId(UserModuleOld.class)).thenReturn(DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE + 1);

		jedis = Mockito.mock(Jedis.class);
		JedisPool jedisPool = Mockito.mock(JedisPool.class);
		Mockito.when(jedisPool.getResource()).thenReturn(jedis);

		// Mock current language
		Language curLang = new Language("en", "english");
		Mockito.when(dataSource.getCurrentLanguage()).thenReturn(curLang);

		// Mock existing profiles

		// built-in profiles
		List<UserProfileModule> profiles = new ArrayList<>();
		defaultProfile = SchemaMigrationVersion3.createDefaultProfile();
		profiles.add(defaultProfile);
		fullProfile = SchemaMigrationVersion3.createFullProfile();
		profiles.add(fullProfile);
		mediumProfile = SchemaMigrationVersion3.createMediumProfile();
		profiles.add(mediumProfile);
		lowProfile = SchemaMigrationVersion3.createLowProfile();
		profiles.add(lowProfile);
		// user-created profiles
		usedCustomerCreatedProfile = new UserProfileModule(
		        DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE + 1,
				"my profile", "my profile", null, null, false, false,
				Collections.emptySet(), Collections.emptySet(), InternetAccessRestrictionMode.NONE,
				Collections.emptySet(), new HashMap<>(), null, false, null);
		profiles.add(usedCustomerCreatedProfile);
		UserProfileModule unusedCustomerCreatedProfile = new UserProfileModule(
		        DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE + 2,
				"my profile", "my profile", null, null, false, false,
				Collections.emptySet(), Collections.emptySet(), InternetAccessRestrictionMode.NONE,
				Collections.emptySet(), new HashMap<>(), null, false, null);
		profiles.add(unusedCustomerCreatedProfile);
		Mockito.when(dataSource.getAll(UserProfileModule.class)).thenReturn(profiles);

		// expected users for built-in profiles
		expectedFullProfileUser = new UserModuleOld(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID,
				DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID,
				"",
				DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_FULL_PROFILE,
            null, null,
            false,
				null,
                Collections.emptyMap(),
            null,
            null,
            null);
		expectedMediumProfileUser = new UserModuleOld(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID,
				DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID,
				"",
				DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_MEDIUM_PROFILE,
            null, null,
            false,
				null,
                Collections.emptyMap(),
            null,
            null,
            null);
		expectedLowProfileUser = new UserModuleOld(DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID,
				DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID,
				"",
				DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_LOW_PROFILE,
            null, null,
            false,
				null,
                Collections.emptyMap(),
            null,
            null,
            null);

		// expected user for customer created profile -- but also the expected user for createStandardUser!
		expectedUser = new UserModuleOld(
            DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID,
            DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
            DefaultEntities.PARENTAL_CONTROL_STANDARD_USER_NAME,
            DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_DEFAULT_PROFILE,
            null, null,
            true,
				null,
                Collections.emptyMap()
            , null,
            null,
            null);

		// existing devices
		Set<Device> devices = new HashSet<>();
		deviceDefaultProfile = new Device();
		deviceDefaultProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID));
		Mockito.when(jedis.hget(deviceDefaultProfile.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID)).thenReturn("" + DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID);
		devices.add(deviceDefaultProfile);
		deviceFullProfile = new Device();
		deviceFullProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID));
		Mockito.when(jedis.hget(deviceFullProfile.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID)).thenReturn("" + DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID);
		devices.add(deviceFullProfile);
		deviceMediumProfile = new Device();
		deviceMediumProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID));
		Mockito.when(jedis.hget(deviceMediumProfile.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID)).thenReturn("" + DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID);
		devices.add(deviceMediumProfile);
		deviceLowProfile = new Device();
		deviceLowProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID));
		Mockito.when(jedis.hget(deviceLowProfile.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID)).thenReturn("" + DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID);
		devices.add(deviceLowProfile);
		deviceCustomerCreatedProfile = new Device();
		deviceCustomerCreatedProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE + 1));
		Mockito.when(jedis.hget(deviceCustomerCreatedProfile.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID)).thenReturn("" + (DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE + 1));
		devices.add(deviceCustomerCreatedProfile);

		Mockito.when(dataSource.getDevices()).thenReturn(devices);

		// expected devices (as they are expected to be saved)
		expectedDeviceDefaultProfile = new Device();
		expectedDeviceDefaultProfile.setAssignedUser(DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID);
		expectedDeviceDefaultProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID));
		expectedDeviceFullProfile = new Device();
		expectedDeviceFullProfile.setAssignedUser(DefaultEntities.PARENTAL_CONTROL_FULL_USER_ID);
		expectedDeviceFullProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID));
		expectedDeviceMediumProfile = new Device();
		expectedDeviceMediumProfile.setAssignedUser(DefaultEntities.PARENTAL_CONTROL_MEDIUM_USER_ID);
		expectedDeviceMediumProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID));
		expectedDeviceLowProfile = new Device();
		expectedDeviceLowProfile.setAssignedUser(DefaultEntities.PARENTAL_CONTROL_LOW_USER_ID);
		expectedDeviceLowProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID));
		expectedDeviceCustomerCreatedProfile = new Device();
		expectedDeviceCustomerCreatedProfile.setAssignedUser(DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE + 1);
		expectedDeviceCustomerCreatedProfile.setId(String.valueOf(DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE + 1));

        userMigrationService = Mockito.mock(UserMigrationService.class);

		migration = new SchemaMigrationVersion9(dataSource, jedisPool, userMigrationService);


		// Expected objects
		expectedStandardUser = new UserModuleOld(1,
				1,
				DefaultEntities.PARENTAL_CONTROL_STANDARD_USER_NAME,
				DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_DEFAULT_PROFILE,
            null, null,
            true,  null,
                Collections.emptyMap(),
            null,
            null,
            null);
	}

	@Test
	public void getSourceVersion() throws Exception {
		Assert.assertEquals("8", migration.getSourceVersion());
	}

	@Test
	public void getTargetVersion() throws Exception {
		Assert.assertEquals("9", migration.getTargetVersion());
	}

	@Test
	public void testStandardUserCreated() throws Exception {
		// run migration
		migration.migrate();

		// verify migration has been run correctly

		// verify standard user was stored
		Mockito.verify(userMigrationService).save(expectedStandardUser, expectedStandardUser.getId());
		Mockito.verify(dataSource).setIdSequence(UserModule.class,
		        DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE);
		// verify device has been saved with proper user assigned
		Mockito.verify(dataSource).save(expectedDeviceDefaultProfile);

		// Verify version has been updated correctly
		Mockito.verify(dataSource).setVersion("9");
	}

	@Test
	public void testUserForBuiltinProfilesCreated() {
		// run migration
		migration.migrate();

		// verify migration has been run correctly
		// verify user for full profile was crated
		Mockito.verify(userMigrationService).save(expectedFullProfileUser, expectedFullProfileUser.getId());
		// verify user for medium profile was created
		Mockito.verify(userMigrationService).save(expectedMediumProfileUser, expectedMediumProfileUser.getId());
		// verify user for low profile was created
		Mockito.verify(userMigrationService).save(expectedLowProfileUser, expectedLowProfileUser.getId());
		// verify devices have been saved with proper users assigned
		Mockito.verify(dataSource).save(expectedDeviceFullProfile);
		Mockito.verify(dataSource).save(expectedDeviceMediumProfile);
		Mockito.verify(dataSource).save(expectedDeviceLowProfile);
	}

	@Test
	public void testUserForCustomerCreatedProfilesCreated(){
		// run migration
		migration.migrate();

		// verify migration has been run correctly

		// verify user for used customer created profile has been created
		Mockito.verify(userMigrationService).save(expectedUser, expectedUser.getId());
		// verify user for unused customer created profile has not been created
		Mockito.verify(userMigrationService, never()).save(eq(unexpectedUser), anyInt());
		// verify device has been saved with proper user assigned
		Mockito.verify(dataSource).save(expectedDeviceCustomerCreatedProfile);
	}
}
