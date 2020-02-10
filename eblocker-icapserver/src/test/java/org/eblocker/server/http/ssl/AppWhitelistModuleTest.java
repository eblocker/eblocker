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
package org.eblocker.server.http.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Test parsing AppWhitelistModules from and to JSON
 * Created by work on 15.04.16.
 */
public class AppWhitelistModuleTest {

    private static final Logger LOG = LoggerFactory.getLogger(AppWhitelistModuleTest.class);

    private static String TEST_JSON_OUTPUT_FILE="test-data/appwhitelistmodules/app-whitelist-modules-test.json";
    private static ObjectMapper objectMapper;

    private Set<AppWhitelistModule> modules;

    @Before
    public void setup(){
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        modules = new HashSet<>();

        //Create test modules
        List<String> urls1 = Arrays.asList( "swcdn.apple.com",
                "swscan.apple.com",
                "swquery.apple.com",
                "swdownload.apple.com");
        Map<String,String> descMap1 = new HashMap<>();
        descMap1.put("en","OSX Softwareupdates");
        descMap1.put("de","OSX Softwareerneuerungen");
        AppWhitelistModule osxUpdates = createAppModule(1,"OSX Softwareupdates",urls1,descMap1,true, false);

        List<String> urls2 = Arrays.asList(
                "b-graph.facebook.com",
                "b-api.facebook.com",
                "edge-mqtt.facebook.com",
                "api.facebook.com",
                "z-p1-external-frt3-1.xx.fbcdn.net");
        Map<String,String> descMap2 = new HashMap<>();
        descMap2.put("en","Facebook Messenger App");
        descMap2.put("de","Facebook Messenger App");
        AppWhitelistModule facebookMessengerApp = createAppModule(2,"Facebook Messenger App",urls2,descMap2,false, false);


        List<String> urls3 = Arrays.asList("s.mzstatic.com",
                "swscan.apple.com",
                "xp.apple.com",
                "itunes.apple.com");

        Map<String,String> descMap3 = new HashMap<>();
        descMap3.put("en","Apple OSX App Store");
        descMap3.put("de","Apple OSX App Store");
        AppWhitelistModule macAppStore = createAppModule(3,"OSX App Store",urls3,descMap3,true, false);

        List<String> urls4 = Arrays.asList("windowsupdate.com",
                "update.microsoft.com");
        AppWhitelistModule windows10Updates = createAppModule(4,"Windows 10 Updates",urls4, null, false, true);

        modules.add(osxUpdates);
        modules.add(facebookMessengerApp);
        modules.add(macAppStore);
        modules.add(windows10Updates);
    }

    @Test
    public void testParseFromJSONFile() throws IOException{
        String jsonInput = IOUtils.toString(ClassLoader.getSystemResource(TEST_JSON_OUTPUT_FILE));
        List<AppWhitelistModule> parsedModules = objectMapper.readValue(jsonInput, new TypeReference<Collection<AppWhitelistModule>>(){});

        assertEquals(7, parsedModules.size());
        assertTrue(parsedModules.containsAll(modules));

    }

    private AppWhitelistModule createAppModule(int id, String name, List<String> domains, Map<String, String> description, boolean enabledbyDefault, boolean enabled) {
        return new AppWhitelistModule(
                id,
                name,
                description,
                domains,
                null,
                null,
                null,
                enabledbyDefault,
                enabled,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    public void testSetWhitelistedDomains(){
		AppWhitelistModule module = new AppWhitelistModule(1, "name", null,
				null, null, null, null, false, false, false, false, "v1.0",
				false, false);
		
		List<String> whitelistedDomains = new ArrayList<>();
		List<String> expectedDomains = new ArrayList<>();
		List<String> notExpectedDomains = new ArrayList<>();

		String url = "http://www.foo.bar/fourty.seven?eleven=true";
		whitelistedDomains.add(url);
		expectedDomains.add("www.foo.bar");

		url = "https://username:password@members.paysite.com/services/purchase.php?item=47&variation=11";
		whitelistedDomains.add(url);
		expectedDomains.add("members.paysite.com");

		url = "http://teatime.uk/serve.php&milk=true";
		whitelistedDomains.add(url);
		expectedDomains.add("teatime.uk");

		url = "bla.de";
		whitelistedDomains.add(url);
		expectedDomains.add("bla.de");

		url = ".";
		whitelistedDomains.add(url);

		url = "..";
		whitelistedDomains.add(url);

		url = "de";
		whitelistedDomains.add(url);
		notExpectedDomains.add("de");

		url = "http://www.bla2.de";
		whitelistedDomains.add(url);
		expectedDomains.add("www.bla2.de");

		url = "http://www.bla3.de/";
		whitelistedDomains.add(url);
		expectedDomains.add("www.bla3.de");

		url = "http://www.bla4..de/";
		whitelistedDomains.add(url);
		notExpectedDomains.add("www.bla4.de");
		notExpectedDomains.add("bla4.de");

		url = "bla5.de/blubb";
		whitelistedDomains.add(url);
		expectedDomains.add("bla5.de");

		url = "http://www.bla6.de/blubb.html#fasel";
		whitelistedDomains.add(url);
		expectedDomains.add("www.bla6.de");

		url = "http://user:pass@bla7.de/blubb";
		whitelistedDomains.add(url);
		expectedDomains.add("bla7.de");

		url = "https://user:pass@bla8.de/blubb.php?param1=foo&param2=bar";
		whitelistedDomains.add(url);
		expectedDomains.add("bla8.de");
		
		module.setWhitelistedDomains(whitelistedDomains);
		
		assertTrue(module.getWhitelistedDomains().containsAll(expectedDomains));
		// To make sure expectedDomains contains enough entries
		assertEquals(expectedDomains.size(), module.getWhitelistedDomains().size());
		
		for (String domain : notExpectedDomains) {
			assertFalse(module.getWhitelistedDomains().contains(domain));
		}
    }
}
