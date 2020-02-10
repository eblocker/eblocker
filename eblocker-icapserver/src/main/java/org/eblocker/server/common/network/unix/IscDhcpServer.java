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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.DhcpServer;
import org.eblocker.server.common.network.DhcpServerConfiguration;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Controls the isc-dhcp-server service on Debian
 */
public class IscDhcpServer implements DhcpServer {
	private final ScriptRunner scriptRunner;
	private final String disableCommand;
	private final String enableCommand;
	private final String startParam="start";
	private final String configPath;
	private final String applyConfigCommand;

	@Inject
	public IscDhcpServer(
			ScriptRunner scriptRunner,
			@Named("network.unix.dhcpd.config.path") String configPath,
			@Named("network.unix.dhcpd.apply.config.command") String applyConfigCommand,
			@Named("network.unix.dhcpd.enable.command")  String enableComand,
			@Named("network.unix.dhcpd.disable.command") String disableComand
	) {
		this.configPath = configPath;
		this.scriptRunner = scriptRunner;
		this.applyConfigCommand = applyConfigCommand;
		this.disableCommand = disableComand;
		this.enableCommand = enableComand;
	}
	
	private void writeConfig(DhcpServerConfiguration config) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath))) {
			writer.append(IscDhcpServerConfiguration.format(config));
			writer.close();
		}
	}

	@Override
	public void enable(boolean start) {
		int result;
		try {
            if (start) {
                result = scriptRunner.runScript(enableCommand, startParam);
            } else {
                result = scriptRunner.runScript(enableCommand);
            }
		} catch (Exception e) {
			throw new EblockerException("Could not enable DHCP server", e);
		}
		
		if (result != 0) {
			throw new EblockerException("Could not enable DHCP server. Return value: " + result);
		}
	}

	@Override
	public void disable() {
		int result;
		try {
			result = scriptRunner.runScript(disableCommand);
		} catch (Exception e) {
			throw new EblockerException("Could not disable DHCP server", e);
		}

		if (result != 0) {
			throw new EblockerException("Could not disable DHCP server. Return value: " + result);
		}
	}

	@Override
	public void setConfiguration(DhcpServerConfiguration configuration) {
		try {
			writeConfig(configuration);
		} catch (Exception e) {
			throw new EblockerException("Could not write DHCP server configuration", e);
		}
		
		int result;
		try {
			result = scriptRunner.runScript(applyConfigCommand);
		} catch (Exception e) {
			throw new EblockerException("Could not apply DHCP server configuration", e);
		}

		if (result != 0) {
			throw new EblockerException("Could not apply DHCP server configuration. Return value: " + result);
		}
	}

}
