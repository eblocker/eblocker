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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

@Singleton
@SubSystemService(value = SubSystem.NETWORK_STATE_MACHINE, initPriority = 25)
public class IpSets {
    private static final Logger log = LoggerFactory.getLogger(IpSets.class);

    private final String ipSetCheckSupportScript;
    private final String ipsetRestoreScript;
    private final ScriptRunner scriptRunner;

    private boolean ipSetsSupported;

    @Inject
    public IpSets(@Named("network.unix.ipset.checkSupport.command") String ipSetCheckSupportScript,
                  @Named("network.unix.ipset.restore.command") String ipsetRestoreScript,
                  ScriptRunner scriptRunner) {
        this.ipSetCheckSupportScript = ipSetCheckSupportScript;
        this.ipsetRestoreScript = ipsetRestoreScript;
        this.scriptRunner = scriptRunner;
    }

    @SubSystemInit
    public void initialize() {
        try {
            int exitValue = scriptRunner.runScript(ipSetCheckSupportScript);
            ipSetsSupported = exitValue == 0;
            if (ipSetsSupported) {
                log.info("ipset modules loaded");
            } else {
                log.warn("ipset modules failed to loaded - functionality will not be available.");
            }
        } catch (Exception e) {
            log.error("failed to run ip-set support check", e);
        }
    }

    public boolean isSupportedByOperatingSystem() {
        return ipSetsSupported;
    }

    public void createIpSet(IpSetConfig ipSetConfig) throws IOException {
        ipSetRestore(ipSetConfig.getName(), writer -> writer.format("create %s %s family inet hashsize 0 maxelem %d -exist\n", ipSetConfig.getName(), ipSetConfig.getType(), ipSetConfig.getMaxSize()));
    }

    public void updateIpSet(IpSetConfig ipSetConfig, Set<String> entries) throws IOException {
        if (entries.size() > ipSetConfig.getMaxSize()) {
            throw new IllegalArgumentException("ip set has " + entries.size() + " elements but is limited to " + ipSetConfig.getMaxSize());
        }

        ipSetRestore(ipSetConfig.getName(), writer -> {
            writer.format("create %s %s family inet hashsize %d maxelem %d -exist\n", ipSetConfig.getName(), ipSetConfig.getType(), entries.size(), ipSetConfig.getMaxSize());
            writer.format("create %s_tmp %s family inet hashsize %d maxelem %d -exist\n", ipSetConfig.getName(), ipSetConfig.getType(), entries.size(), ipSetConfig.getMaxSize());
            writer.format("flush %s_tmp\n", ipSetConfig.getName());
            entries.forEach(e -> writer.format("add %s_tmp %s\n", ipSetConfig.getName(), e));
            writer.format("swap %s_tmp %s\n", ipSetConfig.getName(), ipSetConfig.getName());
            writer.format("destroy %s_tmp\n", ipSetConfig.getName());
        });
    }

    private void ipSetRestore(String name, Consumer<PrintWriter> ipSetCreator) throws IOException {
        Path path = Files.createTempFile(name, ".ipset");
        try (PrintWriter writer = new PrintWriter(path.toFile())) {
            ipSetCreator.accept(writer);
            writer.flush();
            try {
                scriptRunner.runScript(ipsetRestoreScript, path.toString());
            } catch (InterruptedException e) {
                log.warn("updating ipset has been interrupted!", e);
                Thread.currentThread().interrupt();
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
