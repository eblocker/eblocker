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

import com.google.inject.Inject;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserProfileModule;
import redis.clients.jedis.JedisPool;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SchemaMigrationVersion13 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion13(DataSource dataSource, JedisPool jedisPool) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "12";
    }

    @Override
    public String getTargetVersion() {
        return "13";
    }

    static UserProfileModule createMedium2Profile() {
        // Full 2 Profile
        Set<Integer> blacklistedSites = new HashSet<>();
        // List "Inappropriate Sites"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES);
        // List "Gambling"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMBLING);
        // List "Porn"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_PORN);

        Map<DayOfWeek, Integer> maxUsage = new HashMap<>();
        maxUsage.put(DayOfWeek.MONDAY, 120);
        maxUsage.put(DayOfWeek.TUESDAY, 120);
        maxUsage.put(DayOfWeek.WEDNESDAY, 120);
        maxUsage.put(DayOfWeek.THURSDAY, 120);
        maxUsage.put(DayOfWeek.FRIDAY, 120);
        maxUsage.put(DayOfWeek.SATURDAY, 180);
        maxUsage.put(DayOfWeek.SUNDAY, 180);

        Set<InternetAccessContingent> contingent = new HashSet<>();
        contingent.add(new InternetAccessContingent(1, 7 * 60, 21 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(2, 7 * 60, 21 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(3, 7 * 60, 21 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(4, 7 * 60, 21 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(5, 7 * 60, 22 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(6, 7 * 60, 22 * 60, 0)); // Every Weekday, 7-21
        contingent.add(new InternetAccessContingent(7, 7 * 60, 21 * 60, 0)); // Every Weekday, 7-21

        UserProfileModule profile = new UserProfileModule(
            DefaultEntities.PARENTAL_CONTROL_MEDIUM_2_PROFILE_ID,
            null,
            null,
            DefaultEntities.PARENTAL_CONTROL_MEDIUM_2_PROFILE_NAME_KEY,
            DefaultEntities.PARENTAL_CONTROL_MEDIUM_2_PROFILE_DESCRIPTION_KEY,
            false,
            false,
            Collections.emptySet(),
            blacklistedSites,
            UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
            contingent,
            maxUsage,
            null,
            false,
            null
        );
        profile.setBuiltin(true);
        profile.setControlmodeTime(true);
        profile.setControlmodeUrls(true);
        profile.setControlmodeMaxUsage(true);

        return profile;
    }

    static UserProfileModule createFull2Profile() {
        // Medium 2 Profile
        Set<Integer> blacklistedSites = new HashSet<>();
        // List "Inappropriate Sites"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES);
        // List "Gambling"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMBLING);
        // List "Gaming"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_GAMING);
        // List "Music"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_MUSIC);
        // List "Porn"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_PORN);
        // List "Social Media"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_SOCIAL_MEDIA);
        // List "Video"
        blacklistedSites.add(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_VIDEO);

        Set<InternetAccessContingent> contingent = new HashSet<>();
        contingent.add(new InternetAccessContingent(8, 7 * 60, 20 * 60, 0)); // Every Weekday, 7-20
        contingent.add(new InternetAccessContingent(9, 7 * 60, 20 * 60, 0)); // Every Weekend, 7-20

        Map<DayOfWeek, Integer> maxUsage = new HashMap<>();
        maxUsage.put(DayOfWeek.MONDAY, 60);
        maxUsage.put(DayOfWeek.TUESDAY, 60);
        maxUsage.put(DayOfWeek.WEDNESDAY, 60);
        maxUsage.put(DayOfWeek.THURSDAY, 60);
        maxUsage.put(DayOfWeek.FRIDAY, 60);
        maxUsage.put(DayOfWeek.SATURDAY, 60);
        maxUsage.put(DayOfWeek.SUNDAY, 60);

        UserProfileModule profile = new UserProfileModule(
            DefaultEntities.PARENTAL_CONTROL_FULL_2_PROFILE_ID,
            null,
            null,
            DefaultEntities.PARENTAL_CONTROL_FULL_2_PROFILE_NAME_KEY,
            DefaultEntities.PARENTAL_CONTROL_FULL_2_PROFILE_DESCRIPTION_KEY,
            false,
            false,
            Collections.emptySet(),
            blacklistedSites,
            UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
            contingent,
            maxUsage,
            null,
            false,
            null
        );
        profile.setBuiltin(true);
        profile.setControlmodeTime(true);
        profile.setControlmodeUrls(true);
        profile.setControlmodeMaxUsage(true);

        return profile;
    }

    static UserProfileModule createRestrictedProfile() {
        Map<DayOfWeek, Integer> maxUsage = new HashMap<>();
        maxUsage.put(DayOfWeek.MONDAY, 0);
        maxUsage.put(DayOfWeek.TUESDAY, 0);
        maxUsage.put(DayOfWeek.WEDNESDAY, 0);
        maxUsage.put(DayOfWeek.THURSDAY, 0);
        maxUsage.put(DayOfWeek.FRIDAY, 0);
        maxUsage.put(DayOfWeek.SATURDAY, 0);
        maxUsage.put(DayOfWeek.SUNDAY, 0);

        UserProfileModule profile = new UserProfileModule(
            DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_ID,
            null,
            null,
            DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_NAME_KEY,
            DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_DESCRIPTION_KEY,
            false,
            true,
            Collections.emptySet(),
            Collections.emptySet(),
            UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
            Collections.emptySet(),
            maxUsage,
            null,
            false,
            null
        );
        profile.setBuiltin(true);
        profile.setControlmodeTime(false);
        profile.setControlmodeUrls(false);
        profile.setControlmodeMaxUsage(true);

        return profile;
    }

    public void migrate() {
        // Store the full profile
        UserProfileModule full2Profile = createFull2Profile();
        dataSource.save(full2Profile, full2Profile.getId());

        // Store the medium profile
        UserProfileModule medium2Profile = createMedium2Profile();
        dataSource.save(medium2Profile, medium2Profile.getId());

        // Update the restricted profile
        UserProfileModule restrictedProfile = createRestrictedProfile();
        dataSource.save(restrictedProfile, restrictedProfile.getId());

        dataSource.setVersion("13");
    }
}
