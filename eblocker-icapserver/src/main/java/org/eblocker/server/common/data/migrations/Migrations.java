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

import org.eblocker.server.common.BaseModule;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.exceptions.EblockerException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Migrations can update the Redis database during installation of software updates.
 * They are run oncy by the postinstall script.
 *
 * Migrations can only update the database to higher version numbers.
 *
 * Since the user can skip software updates, migrations must also handle updates
 * that span more than one release.
 */
public class Migrations {
	private static final Logger log = LoggerFactory.getLogger(Migrations.class);

	private DataSource dataSource;
	private Map<String, SchemaMigration> migrationsBySourceVersion;
	private String targetVerison;

	@Inject
	public Migrations(DataSource dataSource, Set<SchemaMigration> schemaMigrations) {
		this.dataSource = dataSource;
		setupSchemaMigrations(schemaMigrations);
	}

	private void setupSchemaMigrations(Set<SchemaMigration> schemaMigrations) {
		buildSchemaMigrationsMap(schemaMigrations);
		checkMigrationsPath();
	}

	public String migrateToLatestSchema() {
		String version = dataSource.getVersion();
		log.info("data source version: {}", version);
		log.info("migrations version: {}", targetVerison);
		if (targetVerison.equals(version)) {
			log.info("nothing to do");
			return version;
		}

		SchemaMigration migration = migrationsBySourceVersion.get(version);
		if (migration == null) {
			log.error("no migration path found for source version {} to {}!", version, targetVerison);
			throw new EblockerException("no migration path found for source version " + version + " to " + targetVerison);
		}

		while(migration != null) {
			log.info("running migration from {} to {}", migration.getSourceVersion(), migration.getTargetVersion());
			migration.migrate();
			migration = migrationsBySourceVersion.get(migration.getTargetVersion());
		}
		log.info("migrations done!");
		return dataSource.getVersion();
	}


	/**
	 * Builds map by source version and checks each source verison is just used once
	 */
	private void buildSchemaMigrationsMap(Set<SchemaMigration> schemaMigrations) {
		migrationsBySourceVersion = new HashMap<>();
		for(SchemaMigration migration : schemaMigrations) {
			if (migrationsBySourceVersion.put(migration.getSourceVersion(), migration) != null) {
				log.error("duplicate schema migration with source version {}", migration.getSourceVersion());
				throw new EblockerException("duplicate schema migration with source version " + migration.getSourceVersion());
			}
		}
	}

	/**
	 * Checks if migrations are linear
	 */
	private void checkMigrationsPath() {
		Set<SchemaMigration> usedMigrations = new HashSet<>();

		SchemaMigration migration = migrationsBySourceVersion.get(null);
		if (migration == null) {
			log.error("no migration for empty database found!");
			throw new EblockerException("no migration for empty database found!");
		}

		while(migration != null) {
            targetVerison = migration.getTargetVersion();
            usedMigrations.add(migration);
			SchemaMigration nextMigration = migrationsBySourceVersion.get(migration.getTargetVersion());
            log.info("found migration from {} to {}", migration.getSourceVersion(), migration.getTargetVersion());
            migration = nextMigration;
		}

		if (usedMigrations.size() != migrationsBySourceVersion.size()) {
			log.error("expected {} migrations but found {}!", migrationsBySourceVersion.size(), usedMigrations.size());
			throw new EblockerException("unused migration found!");
		}
	}

	public static String run() throws IOException {
		Injector injector = Guice.createInjector(new MigrationsModule());
		return injector.getInstance(Migrations.class).migrateToLatestSchema();
	}

	private static class MigrationsModule extends BaseModule {
		MigrationsModule() throws IOException {
			super();
		}

        @Override
        protected void configure() {
            super.configure();
            Multibinder<SchemaMigration> migrationMultiBinder = Multibinder.newSetBinder(binder(), SchemaMigration.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion1.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion2.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion3.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion4.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion5.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion6.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion7.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion8.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion9.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion10.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion11.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion12.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion13.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion14.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion15.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion16.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion17.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion18.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion19.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion20.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion21.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion22.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion23.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion24.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion25.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion26.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion27.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion28.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion29.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion30.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion31.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion32.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion33.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion34.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion35.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion36.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion37.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion38.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion39.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion40.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion41.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion42.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion43.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion44.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion45.class);
            migrationMultiBinder.addBinding().to(SchemaMigrationVersion46.class);
			migrationMultiBinder.addBinding().to(SchemaMigrationVersion47.class);
        }
    }
}
