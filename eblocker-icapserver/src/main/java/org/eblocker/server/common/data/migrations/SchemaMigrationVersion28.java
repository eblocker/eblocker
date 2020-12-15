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
import org.eblocker.server.common.data.UserProfileModule;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SchemaMigrationVersion28 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion28(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "27";
    }

    @Override
    public String getTargetVersion() {
        return "28";
    }

    @Override
    public void migrate() {
        Map<DayOfWeek, Integer> maxUsageTimeByDay = new HashMap<>();
        maxUsageTimeByDay.put(DayOfWeek.MONDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.TUESDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.WEDNESDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.THURSDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.FRIDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.SATURDAY, 120);
        maxUsageTimeByDay.put(DayOfWeek.SUNDAY, 120);

        UserProfileModule profile = new UserProfileModule(
                DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_ID,
                null,
                null,
                DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_NAME_KEY,
                DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_DESCRIPTION_KEY,
                false,
                false,
                Collections.singleton(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FRAG_FINN),
                Collections.emptySet(),
                UserProfileModule.InternetAccessRestrictionMode.WHITELIST,
                Collections.emptySet(),
                maxUsageTimeByDay,
                null,
                false,
                null);
        profile.setBuiltin(true);
        profile.setControlmodeMaxUsage(true);
        profile.setControlmodeTime(false);
        profile.setControlmodeUrls(true);
        dataSource.save(profile, profile.getId());

        dataSource.setVersion("28");
    }

}
