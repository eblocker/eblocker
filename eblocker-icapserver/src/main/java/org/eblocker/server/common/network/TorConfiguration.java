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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.server.icap.resources.TemplateExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is responsible for writing the Tor configuration file
 */
public class TorConfiguration {
    private static final Logger log = LoggerFactory.getLogger(TorController.class);

    //exit node constants
    private final SimpleResource torConfigFileTemplate;
    private final String torConfigFilePath;
    private static final String TOR_RESTRICT_EXIT_NODES = "@SET_EXIT_NODES@";
    private static final String TOR_EXIT_NODES = "@EXIT_NODES@";

    @Inject
    public TorConfiguration(
        @Named("tor.config.template.file.path") String torConfigTemplateFilePath,
        @Named("tor.config.file.path") String torConfigFilePath
    ) {
        this.torConfigFileTemplate = new SimpleResource(torConfigTemplateFilePath);//tor config template
        this.torConfigFilePath = torConfigFilePath;
    }

    public void update(Set<String> selectedCountryCodes) {
        String configExitNodeString = getConfigExitNodeString(selectedCountryCodes);
        log.info("Set {} as country codes string in Tor config...", configExitNodeString);
        if (!writeTorConfig(configExitNodeString)) {
            log.error("Init: Error while writing the Tor config file: {}", torConfigFilePath);
        }
    }

    /**
     * Creates a list of country codes for the Tor configuration, separated by commas,
     * where each code is enclosed in brackets
     *
     * @param countryCodes
     * @return country code list for Tor configuration, e.g. {de},{fr},{no}
     */
    public static String getConfigExitNodeString(Set<String> countryCodes) {
        return countryCodes
            .stream()
            .map(code -> "{" + code + "}")
            .collect(Collectors.joining(","));
    }

    /**
     * Use the tor config template and fill in the appropiate information; and then override the "real" tor configuration file with the
     * finished template information
     *
     * @param exitNodeCountries
     * @return
     */
    private synchronized boolean writeTorConfig(String exitNodeCountries) {
        Map<String, String> substitute = new HashMap<>();

        //prepare map for templateexpander
        if (!"".equals(exitNodeCountries)) {//specify which ExitNode countries to use
            substitute.put(TOR_RESTRICT_EXIT_NODES, "");
            substitute.put(TOR_EXIT_NODES, exitNodeCountries);
        } else {//use Tor instance without setting specific Exit Nodes, in standard mode!
            substitute.put(TOR_RESTRICT_EXIT_NODES, "#");
            substitute.put(TOR_EXIT_NODES, "");
        }

        //template exists?
        if (!ResourceHandler.exists(torConfigFileTemplate)) {
            log.error("Tor config template file can not be found here {}", torConfigFileTemplate.getPath());
            return false;
        }

        //load content of template configuration file
        String confTemplate = ResourceHandler.load(torConfigFileTemplate);

        //fill the template with the information
        String result = TemplateExpander.expand(confTemplate, substitute);

        //overwrite real tor config file with finished template file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(torConfigFilePath))) {
            writer.write(result);
        } catch (IOException e) {
            log.error("Overriding the Tor config file {} did not work", torConfigFilePath, e);
            return false;
        }
        return true;
    }
}
