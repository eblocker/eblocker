/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Runs an external IPv6 address monitor and notifies the NetworkInterfaceWrapper when
 * IPv6 addresses are added to or deleted from the interface
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class Ip6AddressMonitor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Ip6AddressMonitor.class);

    private static final Pattern ADDRESS_UPDATE = Pattern.compile("inet6 [:a-f0-9]+/\\d+");
    private final ScriptRunner scriptRunner;
    private final String monitorCommand;
    private final String killCommand;
    private final NetworkInterfaceWrapper networkInterface;

    private LoggingProcess process;

    @Inject
    public Ip6AddressMonitor(@Named("network.ip.monitor.command") String monitorCommand,
                             @Named("kill.process.command") String killCommand,
                             ScriptRunner scriptRunner,
                             NetworkInterfaceWrapper networkInterface) {
        this.scriptRunner = scriptRunner;
        this.monitorCommand = monitorCommand;
        this.killCommand = killCommand;
        this.networkInterface = networkInterface;
    }

    @Override
    public void run() {
        try {
            process = scriptRunner.startScript(monitorCommand, networkInterface.getInterfaceName());
            String line;
            while ((line = process.takeStdout()) != null) {
                handleLogLine(line);
            }
        } catch (IOException e) {
            log.error("Could not start IP address monitor", e);
        } catch (InterruptedException e) {
            log.error("Interrupted. Stopping IP address monitor.", e);
            Thread.currentThread().interrupt();
        }
    }

    private void handleLogLine(String line) {
        if (ADDRESS_UPDATE.matcher(line).find()) {
            log.debug("IPv6 address update detected: {}", line);
            networkInterface.notifyIp6AddressChanged();
        }
    }

    public void shutdown() {
        if (process != null) {
            try {
                scriptRunner.runScript(killCommand, String.valueOf(process.getPid()));
            } catch (IOException e) {
                log.error("Could not stop external IP address monitor", e);
            } catch (InterruptedException e) {
                log.error("Interrupted while stopping external IP address monitor", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
