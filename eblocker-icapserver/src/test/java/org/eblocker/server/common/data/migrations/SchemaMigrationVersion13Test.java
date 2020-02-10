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
import static org.mockito.ArgumentMatchers.eq;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserProfileModule;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class SchemaMigrationVersion13Test {
	private DataSource dataSource;
	private Jedis jedis;
	private SchemaMigrationVersion13 migration;
	
	List<UserModuleOld> userModules = new ArrayList<>();
	private UserModuleOld userAlice, userBob;

	@Before
	public void setup() {
        dataSource = Mockito.mock(DataSource.class);

        jedis = Mockito.mock(Jedis.class);
        JedisPool jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

		migration = new SchemaMigrationVersion13(dataSource, jedisPool);

		// Existing users
		userAlice = Mockito.mock(UserModuleOld.class);
		Mockito.when(userAlice.getId()).thenReturn(1337);
		userBob = Mockito.mock(UserModuleOld.class);
		Mockito.when(userBob.getId()).thenReturn(4711);
		userModules.add(userAlice);
		userModules.add(userBob);
	}

	@Test
	public void getSourceVersion() throws Exception {
		Assert.assertEquals("12", migration.getSourceVersion());
	}

	@Test
	public void getTargetVersion() throws Exception {
		Assert.assertEquals("13", migration.getTargetVersion());
	}

	@Test
	public void testMigrate() {
	    migration.migrate();
	    
        UserProfileModule expectedFullProfile = SchemaMigrationVersion13.createFull2Profile();
        UserProfileModule expectedMediumProfile = SchemaMigrationVersion13.createMedium2Profile();
        UserProfileModule expectedRestrictedProfile = SchemaMigrationVersion13.createRestrictedProfile();

        Mockito.verify(dataSource).save(eq(expectedFullProfile), eq(expectedFullProfile.getId()));
        Mockito.verify(dataSource).save(eq(expectedMediumProfile), eq(expectedMediumProfile.getId()));
        Mockito.verify(dataSource).save(eq(expectedRestrictedProfile), eq(expectedRestrictedProfile.getId()));
	}

}
