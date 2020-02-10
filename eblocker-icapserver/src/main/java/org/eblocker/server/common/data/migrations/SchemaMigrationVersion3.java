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
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserProfileModule;
import com.google.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SchemaMigrationVersion3 implements SchemaMigration {

	private final DataSource dataSource;
    private final JedisPool jedisPool;

    @Inject
    public SchemaMigrationVersion3(DataSource dataSource, JedisPool jedisPool) {
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
    }

    @Override
    public String getSourceVersion() {
        return "2";
    }

    @Override
    public String getTargetVersion() {
        return "3";
    }
    
    static UserProfileModule createFullProfile(){
		// Full Profile
		Set<Integer> fullSites = new HashSet<>();
		// List "Various Sites"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES);
		// List "Facebook"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FACEBOOK);
		// List "Gambling"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMBLING);
		// List "Gaming"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMING);
		// List "Music"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_MUSIC);
		// List "Porn"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_PORN);
		// List "Social Media"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_SOCIAL_MEDIA);
		// List "Video"
		fullSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VIDEO);
		UserProfileModule fullProfile = new UserProfileModule(
				DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID,
				null, 
				null, 
				"PARENTAL_CONTROL_FULL_PROFILE_NAME", 
				"PARENTAL_CONTROL_FULL_PROFILE_DESCRIPTION",
				false,
				false,
				Collections.emptySet(), 
				fullSites, 
				UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
				Collections.emptySet(),
				Collections.emptyMap(),
            null,
            false,
            null
		);
		fullProfile.setBuiltin(true);
		fullProfile.setControlmodeTime(false);
		fullProfile.setControlmodeUrls(true);

		return fullProfile;
    }
    
    static UserProfileModule createMediumProfile(){
		// Medium Profile
		Set<Integer> medSites = new HashSet<>();
		// List "Various Sites"
		medSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES);
		// List "Gambling"
		medSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMBLING);
		// List "Porn"
		medSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_PORN);
		Set<InternetAccessContingent> medContingent = new HashSet<>();
		medContingent.add(new InternetAccessContingent(8, 16 * 60, 17 * 60, 60)); // Every Weekday, 16-17
		medContingent.add(new InternetAccessContingent(9, 16 * 60, 17 * 60, 60)); // Every Weekend, 16-17
		UserProfileModule medProfile = new UserProfileModule(
				DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID,
				null, 
				null, 
				"PARENTAL_CONTROL_MED_PROFILE_NAME", 
				"PARENTAL_CONTROL_MED_PROFILE_DESCRIPTION",
				false,
				false,
				Collections.emptySet(),
				medSites,
				UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
				medContingent,
				Collections.emptyMap(),
            null,
            false,
            null
		);
		medProfile.setBuiltin(true);
		medProfile.setControlmodeTime(true);
		medProfile.setControlmodeUrls(true);

		return medProfile;
    }

	static UserProfileModule createLowProfile(){
		// Low Profile
		Set<Integer> lowSites = new HashSet<>();
		// List "Various Sites"
		lowSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES);
		// List "Gambling"
		lowSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMBLING);
		// List "Porn"
		lowSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_PORN);
		Set<InternetAccessContingent> lowContingent = new HashSet<>();
		lowContingent.add(new InternetAccessContingent(8, 6 * 60, 8 * 60, 2 * 60)); // Every Weekday, 06-08
		lowContingent.add(new InternetAccessContingent(8, 14 * 60, 22 * 60, 8 * 60)); // Every Weekday, 14-22
		lowContingent.add(new InternetAccessContingent(9, 6 * 60, 22 * 60, 16 * 60)); // Every Weekend, 06-22
		UserProfileModule lowProfile = new UserProfileModule(
				DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID,
				null,
				null,
				"PARENTAL_CONTROL_LOW_PROFILE_NAME",
				"PARENTAL_CONTROL_LOW_PROFILE_DESCRIPTION",
				false,
				false,
				Collections.emptySet(),
				lowSites,
				UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
				lowContingent,
				Collections.emptyMap(),
            null,
            false,
            null
		);
		lowProfile.setBuiltin(true);
		lowProfile.setControlmodeTime(true);
		lowProfile.setControlmodeUrls(true);

		return lowProfile;
    }

    static UserProfileModule createDefaultProfile(){
    	// Default Profile
		UserProfileModule defaultProfile = new UserProfileModule(
				DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
				null,
				null,
				"PARENTAL_CONTROL_DEFAULT_PROFILE_NAME",
				"PARENTAL_CONTROL_DEFAULT_PROFILE_DESCRIPTION",
				true,
				false,
				Collections.emptySet(),
				Collections.emptySet(),
				UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
				Collections.emptySet(),
				Collections.emptyMap(),
            null,
            false,
            null
		);
		defaultProfile.setBuiltin(true);
		defaultProfile.setControlmodeTime(false);
		defaultProfile.setControlmodeUrls(false);

		return defaultProfile;
    }

    public void migrate() {
    	// Store the new default profile
    	UserProfileModule defaultProfile = createDefaultProfile();
		dataSource.save(defaultProfile, defaultProfile.getId());

		// Assign default profile to all existing devices
		Set<Device> devices = dataSource.getDevices();
		for (Device device : devices) {
			try (Jedis jedis = jedisPool.getResource()) {
				jedis.hset(device.getId(), DefaultEntities.PARENTAL_CONTROL_KEY_USER_PROFILE_ID, String.valueOf(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID));
			}
		}

		// Store the full profile
		UserProfileModule fullProfile = createFullProfile();
		dataSource.save(fullProfile, fullProfile.getId());
		// Store the medium profile
		UserProfileModule mediumProfile = createMediumProfile();
		dataSource.save(mediumProfile, mediumProfile.getId());
		// Store the low profile
		UserProfileModule lowProfile = createLowProfile();
		dataSource.save(lowProfile, lowProfile.getId());

		dataSource.setIdSequence(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE);

        dataSource.setVersion("3");
    }
}
