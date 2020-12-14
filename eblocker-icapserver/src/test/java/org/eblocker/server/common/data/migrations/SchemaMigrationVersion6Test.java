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
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserProfileModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.eq;

public class SchemaMigrationVersion6Test {
    private DataSource dataSource;
    private SchemaMigrationVersion6 migration;

    private UserProfileModule mediumProfile;
    List<UserProfileModule> userProfileModules = new ArrayList<>();

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);

        migration = new SchemaMigrationVersion6(dataSource);

        // Expected objects

        // Existing user profiles
        mediumProfile = Mockito.mock(UserProfileModule.class);
        Mockito.when(mediumProfile.getId()).thenReturn(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("5", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("6", migration.getTargetVersion());
    }

    @Test
    public void testFacebookRemovedFromMediumProfile() {
        Mockito.when(dataSource.get(eq(UserProfileModule.class),
                Mockito.eq(DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID))).thenReturn(mediumProfile);

        migration.migrate();

        Mockito.verify(dataSource).save(mediumProfile, DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID);

        Set<InternetAccessContingent> expectedContingent = new HashSet<>();
        expectedContingent.add(new InternetAccessContingent(8, 16 * 60, 17 * 60, 60)); // Every Weekday, 16-17
        expectedContingent.add(new InternetAccessContingent(9, 10 * 60, 11 * 60, 60)); // Every Weekend, 10-11
        expectedContingent.add(new InternetAccessContingent(9, 16 * 60, 18 * 60, 120)); // Every Weekend, 16-18

        Mockito.verify(mediumProfile).setInternetAccessContingents(eq(expectedContingent));
    }

}
