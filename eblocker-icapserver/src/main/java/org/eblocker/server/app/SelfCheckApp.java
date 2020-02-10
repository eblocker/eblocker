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
package org.eblocker.server.app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eblocker.server.common.ConfigurableModule;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.JedisDataSource;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.network.TelnetConnection;
import org.eblocker.server.common.network.TelnetConnectionImpl;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.StatusLedService;
import org.eblocker.server.common.system.unix.ScriptRunnerUnix;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * eBlocker self-check app.
 *
 * This program dumps the self-check result to the console and quits.
 */
public class SelfCheckApp {
    private final DataSource dataSource;
    private final String version;
    private final ScriptRunner scriptRunner;
    private final String showResultScript;
    private final StatusLedService statusLedService;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

	public static void main(String[] args) throws Exception {
		Injector injector = Guice.createInjector(new SelfCheckModule());
		injector.getInstance(SelfCheckApp.class).run();
	}

	/**
	 * Provide a custom module that has a script runner
	 */
	private static class SelfCheckModule extends ConfigurableModule {
        public SelfCheckModule() throws IOException {
            super();
        }

        @Override
        protected void configure() {
            super.configure();
            bind(ScriptRunner.class).to(ScriptRunnerUnix.class);
            bind(DataSource.class).to(JedisDataSource.class);
            bind(TelnetConnection.class).to(TelnetConnectionImpl.class);
        }

        // Needed for ScriptRunnerUnix:
        @Provides
        @Named("unlimitedCachePoolExecutor") @Singleton
        public Executor provideUnlimitedCachePoolExecutor() {
            return executorService;
        }
	}

	@Inject
	public SelfCheckApp(DataSource dataSource,
	                    @Named("project.version") String version,
	                    ScriptRunner scriptRunner,
	                    @Named("show.selfcheck.result.command") String showResultScript,
	                    StatusLedService statusLedService) {
		this.dataSource = dataSource;
		this.version = version;
		this.scriptRunner = scriptRunner;
		this.showResultScript = showResultScript;
		this.statusLedService = statusLedService;
	}

	public void run() throws IOException, InterruptedException {
		File result = File.createTempFile("eblocker-selfcheck", ".txt");
		result.deleteOnExit();

		try (BufferedWriter w = new BufferedWriter(new FileWriter(result))) {

			w.newLine();
			w.newLine();
			w.append("***********************************\n");
			w.append("*       eBlocker Self-Check       *\n");
			w.append("***********************************\n");
			try {
				w.append("eBlocker OS version: ");
				w.append(version);
				w.newLine();

				w.append("eBlocker DB version: ");
				w.append(dataSource.getVersion());
				w.newLine();

				w.append("Self-check result:   OK");
				w.newLine();
				statusLedService.setStatus(ExecutionState.SELF_CHECK_OK);
			} catch (Exception e) {
				w.append("ERROR during self-check: " + e.toString());
				w.newLine();

				w.append("Self-check result:   NOT OK");
				w.newLine();
                statusLedService.setStatus(ExecutionState.SELF_CHECK_NOT_OK);
			}
			w.append("***********************************\n\n");

		}

		scriptRunner.runScript(showResultScript, result.getAbsolutePath());
		executorService.shutdown();
	}
}
