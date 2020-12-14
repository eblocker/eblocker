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

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.TreeSet;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class TorConfigurationTest extends ConfigurationTestBase {
    private TorConfiguration configuration;
    private File torConfig;

    @Before
    public void setUp() throws Exception {
        torConfig = File.createTempFile("tor", ".conf");
        torConfig.deleteOnExit();
        String torConfigTemplateFilePath = "classpath:test-data/tor/torrc.template";
        this.configuration = new TorConfiguration(torConfigTemplateFilePath, getOutFilePath());
    }

    @Test
    public void testUpdateAllExitNodes() throws IOException {
        configuration.update(Collections.emptySet());
        compareOutFileWith("test-data/tor/torrc-allCountries.txt");
    }

    @Test
    public void testUpdateSomeExitNodes() throws IOException {
        // Must use a SortedSet for this test:
        configuration.update(new TreeSet<String>(asList("aa", "ba", "bb")));
        compareOutFileWith("test-data/tor/torrc-someCountries.txt");
    }

    @Test
    public void configExitNodeString() {
        assertEquals("", TorConfiguration.getConfigExitNodeString(Collections.emptySet()));
        assertEquals("{no}", TorConfiguration.getConfigExitNodeString(Collections.singleton("no")));

        // Must use a SortedSet for this test:
        assertEquals("{en},{me},{mu}", TorConfiguration.getConfigExitNodeString(new TreeSet<>(asList("en", "me", "mu"))));
    }
}
