/*
 * Copyright 2026 eBlocker Open Source GmbH
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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.ConfigurableModule;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.unix.NetworkInterfaceConfiguration;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.system.unix.ScriptRunnerUnix;
import org.eblocker.server.http.backup.NetworkBackupProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * This app runs as a Systemd service before network interfaces are set up.
 *
 * It looks for a USB drive with a FAT partition and an eBlocker backup file on it.
 * If it exists the network configuration (e.g. a fixed IPv4 address) is restored.
 */
public class NetworkConfigImportApp {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfigImportApp.class);

    private final NetworkInterfaceConfiguration interfaceConfiguration;
    private final ScriptRunner scriptRunner;
    private final String mountpoint;
    private final int mountpointWaitTimeout;
    private final String mountpointWaitCommand;
    private final String applyNetworkConfigurationCommand;
    private final String backupFilename;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try {
            Injector injector = Guice.createInjector(new NetworkConfigImportApp.NetworkConfigModule());
            injector.getInstance(NetworkConfigImportApp.class).run();
        } catch (Exception e) {
            LOG.error("Could not run NetworkConfigImportApp.",  e);
        }
    }

    @Inject
    public NetworkConfigImportApp(NetworkInterfaceConfiguration interfaceConfiguration,
                                  ScriptRunner scriptRunner,
                                  @Named("external.disk.mountpoint") String mountpoint,
                                  @Named("external.disk.backup.filename") String backupFilename,
                                  @Named("external.disk.mountpoint_wait.timeout_ms") int mountpointWaitTimeout,
                                  @Named("external.disk.mountpoint_wait.command") String mountpointWaitCommand,
                                  @Named("network.unix.apply.configuration.command") String applyNetworkConfigurationCommand) {
        this.interfaceConfiguration = interfaceConfiguration;
        this.scriptRunner = scriptRunner;
        this.mountpoint = mountpoint;
        this.backupFilename = backupFilename;
        this.mountpointWaitTimeout = mountpointWaitTimeout;
        this.mountpointWaitCommand = mountpointWaitCommand;
        this.applyNetworkConfigurationCommand = applyNetworkConfigurationCommand;
    }

    /**
     * Provide a custom module that has a script runner
     */
    private static class NetworkConfigModule extends ConfigurableModule {
        public NetworkConfigModule() throws IOException {
            super();
        }

        @Override
        protected void configure() {
            super.configure();
            bind(ScriptRunner.class).to(ScriptRunnerUnix.class);
        }

        // Needed for ScriptRunnerUnix:
        @Provides
        @Named("unlimitedCachePoolExecutor")
        @Singleton
        public Executor provideUnlimitedCachePoolExecutor() {
            return executorService;
        }
    }

    public void run() {
        try {
            int result = scriptRunner.runScript(mountpointWaitCommand, mountpoint, Integer.toString(mountpointWaitTimeout));
            if (result == 0) {
                Path configFile = Paths.get(mountpoint, backupFilename);
                if (Files.exists(configFile)) {
                    importNetworkConfig(configFile);
                } else {
                    LOG.info("No backup file found at {}. Not importing network configuration.", configFile);
                }
            } else {
                LOG.info("No external disk mounted at {}. Not importing network configuration.", backupFilename);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Could not import network configuration.", e);
        } finally {
            executorService.shutdown();
        }
    }

    private void importNetworkConfig(Path configFile) throws IOException, InterruptedException {
        LOG.info("Trying to import network configuration from {}", configFile);
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            try (JarInputStream jarStream = new JarInputStream(inputStream)) {
                JarEntry entry;
                while ((entry = jarStream.getNextJarEntry()) != null) {
                    if (NetworkBackupProvider.NETWORK_ENTRY.equals(entry.getName())) {
                        JsonFactory jsonFactory = new MappingJsonFactory();
                        jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
                        ObjectMapper objectMapper = new ObjectMapper(jsonFactory);
                        objectMapper
                                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                                .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                                .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
                        NetworkConfiguration networkConfiguration = objectMapper.readValue(jarStream, NetworkConfiguration.class);
                        LOG.info("Importing network configuration: {}", networkConfiguration);
                        if (networkConfiguration.isAutomatic()) {
                            interfaceConfiguration.enableDhcp();
                        } else {
                            interfaceConfiguration.enableStatic(networkConfiguration.getIpAddress(), networkConfiguration.getNetworkMask(), networkConfiguration.getGateway());
                        }
                        int status = scriptRunner.runScript(applyNetworkConfigurationCommand);
                        if (status != 0) {
                            throw new EblockerException("Command '" + applyNetworkConfigurationCommand + "' terminated with exit status: " + status);
                        }
                    }
                }
            }
        }
    }
}
