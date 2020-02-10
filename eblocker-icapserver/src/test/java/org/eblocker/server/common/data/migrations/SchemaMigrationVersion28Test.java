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
import org.eblocker.server.common.data.UserProfileModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.DayOfWeek;
import java.util.Collections;

public class SchemaMigrationVersion28Test {

    private DataSource dataSource;
    private SchemaMigrationVersion28 migration;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion28(dataSource);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("27", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("28", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();

        ArgumentCaptor<UserProfileModule> profileCaptor = ArgumentCaptor.forClass(UserProfileModule.class);
        Mockito.verify(dataSource).save(profileCaptor.capture(), Mockito.eq(DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_ID));
        Assert.assertEquals(Integer.valueOf(DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_ID), profileCaptor.getValue().getId());
        Assert.assertNull(profileCaptor.getValue().getName());
        Assert.assertNull(profileCaptor.getValue().getDescription());
        Assert.assertEquals(DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_NAME_KEY, profileCaptor.getValue().getNameKey());
        Assert.assertEquals(DefaultEntities.PARENTAL_CONTROL_FRAG_FINN_PROFILE_DESCRIPTION_KEY, profileCaptor.getValue().getDescriptionKey());
        Assert.assertEquals(UserProfileModule.InternetAccessRestrictionMode.WHITELIST, profileCaptor.getValue().getInternetAccessRestrictionMode());
        Assert.assertEquals(Collections.singleton(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FRAG_FINN), profileCaptor.getValue().getAccessibleSitesPackages());
        Assert.assertEquals(Collections.emptySet(), profileCaptor.getValue().getInaccessibleSitesPackages());
        Assert.assertEquals(Collections.emptySet(), profileCaptor.getValue().getInternetAccessContingents());
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.MONDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.TUESDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.WEDNESDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.THURSDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.FRIDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.SATURDAY));
        Assert.assertEquals(Integer.valueOf(120), profileCaptor.getValue().getMaxUsageTimeByDay().get(DayOfWeek.SUNDAY));
        Assert.assertFalse(profileCaptor.getValue().isStandard());
        Assert.assertTrue(profileCaptor.getValue().isBuiltin());
        Assert.assertTrue(profileCaptor.getValue().isControlmodeMaxUsage());
        Assert.assertFalse(profileCaptor.getValue().isControlmodeTime());
        Assert.assertTrue(profileCaptor.getValue().isControlmodeUrls());

        Mockito.verify(dataSource).setVersion("28");
    }
}
