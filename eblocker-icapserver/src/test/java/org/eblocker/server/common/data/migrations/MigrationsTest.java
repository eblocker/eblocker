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

// import static org.junit.Assert.*;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

public class MigrationsTest {
	private DataSource dataSource;

	@Before
	public void setUp() throws Exception {
		dataSource = Mockito.mock(DataSource.class);
    }

    @Test
    public void testMigrationsEmptyDataBase() {
        Migrations migrations = new Migrations(dataSource, createSchemaMigrations(0, 5));

        Mockito.when(dataSource.getVersion()).thenReturn(null);
        migrations.migrateToLatestSchema();

        InOrder inOrder = Mockito.inOrder(dataSource);
        inOrder.verify(dataSource).setVersion("1");
        inOrder.verify(dataSource).setVersion("2");
        inOrder.verify(dataSource).setVersion("3");
        inOrder.verify(dataSource).setVersion("4");
        inOrder.verify(dataSource).setVersion("5");
        inOrder.verify(dataSource).getVersion();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testMigrations() {
        Migrations migrations = new Migrations(dataSource, createSchemaMigrations(0, 5));

        Mockito.when(dataSource.getVersion()).thenReturn("2");
        migrations.migrateToLatestSchema();

        InOrder inOrder = Mockito.inOrder(dataSource);
        inOrder.verify(dataSource).setVersion("3");
        inOrder.verify(dataSource).setVersion("4");
        inOrder.verify(dataSource).setVersion("5");
        inOrder.verify(dataSource).getVersion();
        inOrder.verifyNoMoreInteractions();
    }

    @Test(expected = EblockerException.class)
    public void testMissingStartMigration() {
        Migrations migrations = new Migrations(dataSource, createSchemaMigrations(1, 5));

        Mockito.when(dataSource.getVersion()).thenReturn(null);
        migrations.migrateToLatestSchema();
    }

    @Test(expected = EblockerException.class)
    public void testIncompletePath() {
        Set<SchemaMigration> schemaMigrations = new HashSet<>();
        schemaMigrations.addAll(createSchemaMigrations(0, 3));
        schemaMigrations.addAll(createSchemaMigrations(4, 5));
        Migrations migrations = new Migrations(dataSource, schemaMigrations);

        Mockito.when(dataSource.getVersion()).thenReturn(null);
        migrations.migrateToLatestSchema();
    }

    @Test(expected = EblockerException.class)
    public void testAmbigousPath() {
        Set<SchemaMigration> schemaMigrations = new HashSet<>();
        schemaMigrations.addAll(createSchemaMigrations(0, 5));
        schemaMigrations.addAll(createSchemaMigrations(4, 5));
        Migrations migrations = new Migrations(dataSource, schemaMigrations);

        Mockito.when(dataSource.getVersion()).thenReturn(null);
        migrations.migrateToLatestSchema();
    }

    @Test(expected = EblockerException.class)
    public void testMigrationPathMissingCurrentDatabaseVersion() {
        Migrations migrations = new Migrations(dataSource, createSchemaMigrations(0, 5));

        Mockito.when(dataSource.getVersion()).thenReturn("6");
        migrations.migrateToLatestSchema();
    }

    @Test
    public void testDatabaseUpToDate() {
        Migrations migrations = new Migrations(dataSource, createSchemaMigrations(0, 5));

        Mockito.when(dataSource.getVersion()).thenReturn("5");
        migrations.migrateToLatestSchema();
        InOrder inOrder = Mockito.inOrder(dataSource);
        inOrder.verify(dataSource).getVersion();
        inOrder.verifyNoMoreInteractions();
    }

    private Set<SchemaMigration> createSchemaMigrations(int source, int target) {
        Set<SchemaMigration> migrations = new HashSet<>();
        for(int i = source; i < target; ++i) {
            migrations.add(new TestMigration(i == 0 ? null : String.valueOf(i), String.valueOf(i + 1)));
        }
        return migrations;
    }

    private class TestMigration implements SchemaMigration {
        private String sourceVersion;
        private String targetVersion;

        public TestMigration(String sourceVersion, String targetVersion) {
            this.sourceVersion = sourceVersion;
            this.targetVersion = targetVersion;
        }

        @Override
        public String getSourceVersion() {
            return sourceVersion;
        }

        @Override
        public String getTargetVersion() {
            return targetVersion;
        }

        @Override
        public void migrate() {
            dataSource.setVersion(targetVersion);
        }
    }
}
