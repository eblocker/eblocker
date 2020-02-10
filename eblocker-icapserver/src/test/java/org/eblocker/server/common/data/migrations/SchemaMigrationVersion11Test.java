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
import org.eblocker.server.common.data.UserModuleOld;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SchemaMigrationVersion11Test {
	private DataSource dataSource;
	List<UserModuleOld> userModules = new ArrayList<>();
    private SchemaMigrationVersion11 migration;
	private UserMigrationService userMigrationService;
//    private JedisPool jedisPool;
//    private Jedis jedis;
    private ObjectMapper objectMapper;
	private UserModuleOld userAlice, userBob, userAliceExpected, userBobExpected;

	@Before
	public void setup() {
		dataSource = Mockito.mock(DataSource.class);

//        jedis = Mockito.mock(Jedis.class);
//        jedisPool = Mockito.mock(JedisPool.class);
//        objectMapper = new ObjectMapper();
		userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);

		migration = new SchemaMigrationVersion11(dataSource, userMigrationService);

		// Existing users
		userAlice = new UserModuleOld(1337, 1337, "alice", "alice-key", null, null,false, null, null, null, null, null);
//        String aliceJson = objectMapper.writeValueAsString(userAlice);
		userBob = new UserModuleOld(4711, 4711, "bob", "bob-key", null, null, false, null, new HashMap<>(), null, null, null);
//        String bobJson = objectMapper.writeValueAsString(userBob);
		userModules.add(userAlice);
		userModules.add(userBob);

//        TreeSet<String> keys = new TreeSet<>();
//        keys.add("UserModule:1337");
//        keys.add("UserModule:4711");

        // Mock direct access to data store
//        Mockito.when(jedis.get("UserModule:1337")).thenReturn(aliceJson);
//        Mockito.when(jedis.get("UserModule:4711")).thenReturn(bobJson);
//        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
//        Mockito.when(jedis.keys(any(String.class))).thenReturn(keys);

		// Expected users
        userAliceExpected = new UserModuleOld(1337, 1337, "alice", "alice-key", null, null, false, null, Collections.emptyMap(), null, null, null);
        userBobExpected = new UserModuleOld(4711, 4711, "bob", "bob-key", null, null, false, null, Collections.emptyMap(), null, null, null);
	}

	@Test
	public void getSourceVersion() throws Exception {
		Assert.assertEquals("10", migration.getSourceVersion());
	}

	@Test
	public void getTargetVersion() throws Exception {
		Assert.assertEquals("11", migration.getTargetVersion());
	}

	@Test
	public void testSetWhitelistConfigByDomains() {
	    Mockito.when(userMigrationService.getAll()).thenReturn(userModules);

	    migration.migrate();

	    Mockito.verify(userMigrationService).save(userAliceExpected, userAliceExpected.getId());
	    Mockito.verify(userMigrationService).save(userBobExpected, userBobExpected.getId());
	}

}
