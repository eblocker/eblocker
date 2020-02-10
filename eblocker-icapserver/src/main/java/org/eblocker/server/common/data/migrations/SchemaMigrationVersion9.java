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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserProfileModule;
import com.google.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class SchemaMigrationVersion9 implements SchemaMigration {

	private final DataSource dataSource;
	private final JedisPool jedisPool;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion9(DataSource dataSource,
                                   JedisPool jedisPool,
                                   UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "8";
    }

    @Override
    public String getTargetVersion() {
        return "9";
    }

    @Override
    public void migrate() {
    	createStandardUser();
    	assignStandardUserToAllDevices();
        dataSource.setVersion("9");
    }

    private void createStandardUser(){
        UserModuleOld standardUser = new UserModuleOld(DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID,
                DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
                DefaultEntities.PARENTAL_CONTROL_STANDARD_USER_NAME,
                DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_DEFAULT_PROFILE,
            null, null,
            true, null,
                Collections.emptyMap(), null, null, null);
    	userMigrationService.save(standardUser, standardUser.getId());

    	dataSource.setIdSequence(UserModule.class, DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE);
    }

    private void assignStandardUserToAllDevices(){
    	Map<Integer, Integer> profileToUser = new HashMap<>();
    	Set<Integer> usedProfiles = new HashSet<>();



    	// Find out which profiles are in use
    	for (Device device : dataSource.getDevices()) {
    		usedProfiles.add(getAssignedUserProfile(device));
    	}

    	// Create
    	for (UserProfileModule profile : dataSource.getAll(UserProfileModule.class)){
    		// Is the profile in use?
			if (usedProfiles.contains(profile.getId())
					// User for standard profile already created
					&& profile.getId() != DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID) {
				// Create User for this profile
                UserModuleOld userForProfile;
				if (profile.isBuiltin()){
					userForProfile = new UserModuleOld(
							profile.getId(),
							profile.getId(),
							"",
							getTranslationForBuiltinProfile(profile.getId()),
                        null, null,
                        false,
							null,
							Collections.emptyMap(),
                        null,
                        null,
                        null
					);
				}else{
					userForProfile = new UserModuleOld(
							dataSource.nextId(UserModule.class),
							profile.getId(),
							profile.getName(),
							DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_CUSTOMER_CREATED_PROFILE,
                        null, null,
                        false,
							null,
							Collections.emptyMap(),
                        null,
                        null,
                        null
					);
				}
				// Save ID of User and profile in Map
				profileToUser.put(profile.getId(), userForProfile.getId());
				// Save new user
				userMigrationService.save(userForProfile, userForProfile.getId());
			}
    	}
    	// There is now a user for every profile. Now assign those users to the respective devices
    	for (Device device : dataSource.getDevices()) {
			device.setAssignedUser(profileToUser.getOrDefault(
					getAssignedUserProfile(device),
					// In case the device is assigned the default profile
					// Or in the unexpected case there is no ID/User
					DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID));
    		dataSource.save(device);
    	}
    }

	private String getTranslationForBuiltinProfile(int profileId) {
		String result;
		switch (profileId) {
		case DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID: // Default profile
			result = DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_DEFAULT_PROFILE;
			break;
		case DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID: // Full profile
			result = DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_FULL_PROFILE;
			break;
		case DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID: // Medium profile
			result = DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_MEDIUM_PROFILE;
			break;
		case DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID: // Low profile
			result = DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_LOW_PROFILE;
			break;

		default:
			result = DefaultEntities.PARENTAL_CONTROL_NAME_KEY_USER_FOR_UNKNOWN_PROFILE;
		}

		return result;
	}

	private Integer getAssignedUserProfile(Device device) {
		try (Jedis jedis = jedisPool.getResource()) {
			String profileId = jedis.hget(device.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID);
			if (profileId == null) {
				return DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID;
			} else {
				return Integer.parseInt(profileId);
			}
		}
	}
}
