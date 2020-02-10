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
package org.eblocker.server.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class ConfigurableModule extends AbstractModule {
    private static Logger log = LoggerFactory.getLogger(ConfigurableModule.class);

    private static final String DEFAULT_CONFIG_FILE = "configuration.properties";
    private static final String PROJECT_VERSION_STRING_DEVELOP = "Development Version";

    private final Properties properties;
    private final String projectVersion;

    public ConfigurableModule() throws IOException {
        properties = new Properties();
        properties.load(getConfigurationPropertiesInputStream(DEFAULT_CONFIG_FILE));

        loadLocalConfiguration();

        projectVersion = loadCurrentProjectVersion();
    }

    @Override
    protected void configure() {
        Names.bindProperties(binder(), properties);
    }

    private void loadLocalConfiguration() {
        String localConfigurationPath = properties.getProperty("localConfiguration.path");
        if (localConfigurationPath != null) {
            Properties localProperties = new Properties();
            try {
                localProperties.load(new FileInputStream(localConfigurationPath));
                log.info("Loaded additional configuration from {}", localConfigurationPath);
                properties.putAll(localProperties);
            } catch (FileNotFoundException e) {
                log.info("Local configuration file {} does not exist", localConfigurationPath, e);
            } catch (IOException e) {
                log.error("Could not load local configuration file {}", localConfigurationPath, e);
            }
        }
    }

    protected InputStream getConfigurationPropertiesInputStream(String configFile) {
        return ClassLoader.getSystemResourceAsStream(configFile);
    }

    private String loadCurrentProjectVersion(){
        //Read the current project version from the MANIFEST.MF file (maven injects this information while building the jar file)
        String projectVersion = getClass().getPackage().getImplementationVersion();
        if (projectVersion == null) {
            return PROJECT_VERSION_STRING_DEVELOP;
        }
        return projectVersion;
    }

    @Provides @Named("project.version")
    public String getCurrentProjectVersion(){
        return projectVersion;
    }

    protected String getProperty(String key) {
        return properties.getProperty(key);
    }

}
