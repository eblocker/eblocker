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
package org.eblocker.server.common.network.unix;

import java.io.IOException;

import org.eblocker.server.common.network.PacketLogger;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class PacketLoggerUnix implements PacketLogger {
	private final ScriptRunner scriptRunner;
	private final String scriptName;
	private LoggingProcess loggingProcess = null;
	
	@Inject
	public PacketLoggerUnix(ScriptRunner scriptRunner, @Named("diagnostics.packet.logging.command") String scriptName) {
		this.scriptRunner = scriptRunner;
		this.scriptName = scriptName;
	}

	@Override
	public void startLogging() throws IOException {
		loggingProcess = scriptRunner.startScript(scriptName);
	}

	@Override
	public boolean isLogging() {
		return loggingProcess != null && loggingProcess.isAlive();
	}

	@Override
	public void stopLogging() throws InterruptedException, IOException {
		if (loggingProcess != null) {
			scriptRunner.stopScript(loggingProcess);
		}
	}
}
