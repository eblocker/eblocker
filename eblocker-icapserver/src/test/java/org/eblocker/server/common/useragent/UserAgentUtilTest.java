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
package org.eblocker.server.common.useragent;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class UserAgentUtilTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserAgentUtilTest.class);

    private static final SimpleResource USERAGENT_SAMPLE = new SimpleResource("classpath:useragent-samples/userAgents.txt");

    private static final String ID_REGEX = "[0-9a-fA-F.:_-]{4,}";
    private static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    private static final String VERSION_REGEX = "[0-9.,_]{3,}";
    private static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEX);

    private static final String NUMBERS_REGEX = "[0-9]{1,}";
    private static final Pattern NUMBERS_PATTERN = Pattern.compile(NUMBERS_REGEX);

    private static final String WHITESPACE_REGEX = "[\\s#]{1,}";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile(WHITESPACE_REGEX);

    private static final String BROWSER_REGEX = "Mozilla/[0-9.]+\\s.*";
    private static final Pattern BROWSER_PATTERN = Pattern.compile(BROWSER_REGEX);

    private static final String FIREFOX_REGEX = "(^|.*\\s)Firefox/[0-9.]+(\\s.*|$)";
    private static final Pattern FIREFOX_PATTERN = Pattern.compile(FIREFOX_REGEX);

    private static final String SEAMONKEY_REGEX = "(^|.*\\s)Seamonkey/[0-9.]+(\\s.*|$)";
    private static final Pattern SEAMONKEY_PATTERN = Pattern.compile(SEAMONKEY_REGEX);

    private static final String CHROME_REGEX = "(^|.*\\s)Chrome/[0-9.]+(\\s.*|$)";
    private static final Pattern CHROME_PATTERN = Pattern.compile(CHROME_REGEX);

    private static final String CHROMIUM_REGEX = "(^|.*\\s)Chromium/[0-9.]+(\\s.*|$)";
    private static final Pattern CHROMIUM_PATTERN = Pattern.compile(CHROMIUM_REGEX);

    private static final String SAFARI_REGEX = "(^|.*\\s)Safari/[0-9.]+(\\s.*|$)";
    private static final Pattern SAFARI_PATTERN = Pattern.compile(SAFARI_REGEX);

    private static final String OPERA_REGEX = "(^|.*\\s)(Opera|OPR)/[0-9.]+(\\s.*|$)";
    private static final Pattern OPERA_PATTERN = Pattern.compile(OPERA_REGEX);

    private static final String MSIE_REGEX = ".*(^|;|\\s)MSIE [0-9.]+(\\s|;|$).*";
    private static final Pattern MSIE_PATTERN = Pattern.compile(MSIE_REGEX);

    private static final String PARENTHESIS_REGEX = "\\([^()]*\\)";

    private Map<String, List<String>> loadUserAgentSample() {
        Map<String, List<String>> userAgents = new TreeMap<>();
        BufferedReader reader = new BufferedReader(new StringReader(ResourceHandler.load(USERAGENT_SAMPLE)));
        String userAgent = null;
        do {
            try {
                userAgent = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (userAgent != null) {
                userAgents.put(userAgent, null);
            }
        } while (userAgent != null);
        return userAgents;
    }

    @Test
    public void testNormalize() {
        Map<String, List<String>> userAgents = loadUserAgentSample();
        //dump("ORIGINAL", userAgents);

        userAgents = evaporate(userAgents, new BrowserDetector());
        //dump("DETECTED BROWSER", userAgents);

        userAgents = evaporate(userAgents, new Normalizer() {
            @Override
            public String normalize(String s) {
                return ID_PATTERN.matcher(s).replaceAll("##");
            }
        });
        //dump("REPLACED IDS", userAgents);

        userAgents = evaporate(userAgents, new Normalizer() {
            @Override
            public String normalize(String s) {
                return VERSION_PATTERN.matcher(s).replaceAll("#");
            }
        });
        //dump("REPLACED VERSIONS", userAgents);

        userAgents = evaporate(userAgents, new Normalizer() {
            @Override
            public String normalize(String s) {
                return NUMBERS_PATTERN.matcher(s).replaceAll("#");
            }
        });
        //dump("REPLACED NUMBERS", userAgents);

        userAgents = evaporate(userAgents, new TokenReplacer(PARENTHESIS_REGEX, "#"));
        //dump("REPLACED NUMBERS", userAgents);

        userAgents = evaporate(userAgents, new Normalizer() {
            @Override
            public String normalize(String s) {
                return WHITESPACE_PATTERN.matcher(s).replaceAll("*");
            }
        });
        dump("REPLACED WHITESPACE", userAgents);
    }

    //@Test
    public void testRemoveIds() {
        assertEquals("#", removeIds("0123456789abcdef"));
        assertEquals("#g#", removeIds("01234567g89abcdef"));
        assertEquals("#", removeIds("01:23:45:67:89:ab:cd:ef"));
        assertEquals("# #", removeIds("01:23:45:67 89:ab:cd:ef"));
        assertEquals("# #", removeIds("01.23.45.67 89-ab-cd-ef"));
        assertEquals("012 345 678 9ab cde", removeIds("012 345 678 9ab cde"));
    }

    //@Test
    public void testBrowserDetector() {
        Normalizer normalizer = new BrowserDetector();
        assertEquals("BROWSER Firefox", normalizer.normalize("Mozilla/5.0 (X11; U; SunOS i86pc; en-US; rv:1.9.0.10) Gecko/1994123019 Firefox/3.0.10"));
    }

    @Test
    public void testPatternNormalizer() {
        Normalizer normalizer = new TokenReplacer("a", "x");
        assertEquals("xxx", normalizer.normalize("aaa"));
        assertEquals("bbxxxbb", normalizer.normalize("bbaaabb"));

        normalizer = new TokenReplacer("aa", "a");
        assertEquals("a", normalizer.normalize("aaaa"));
        assertEquals("bbabb", normalizer.normalize("bbaaaaaaaaaaaaabb"));
    }

    private String removeIds(String s) {
        return ID_PATTERN.matcher(s).replaceAll("#");
    }

    private Map<String, List<String>> evaporate(Map<String, List<String>> userAgents, Normalizer normalizer) {
        Map<String, List<String>> evaporated = new TreeMap<>();
        for (Entry<String, List<String>> entry : userAgents.entrySet()) {
            String normalized = normalizer.normalize(entry.getKey());
            if (!evaporated.containsKey(normalized)) {
                evaporated.put(normalized, new ArrayList<>());
            }
            if (entry.getValue() != null) {
                for (String userAgent : entry.getValue()) {
                    //evaporated.get(normalized).add(entry.getKey()+" [[ "+userAgent+" ]]");
                    evaporated.get(normalized).add(userAgent);
                }
            } else {
                evaporated.get(normalized).add(entry.getKey());
            }
        }
        return evaporated;
    }

    private void dump(String name, Map<String, List<String>> userAgents) {
        LOG.debug("==========================================================");
        LOG.debug(name + " (" + userAgents.size() + ")");
        LOG.debug("----------------------------------------------------------");

        for (Entry<String, List<String>> entry : userAgents.entrySet()) {
            LOG.debug(entry.getKey());
            if (entry.getValue() != null) {
                for (String userAgent : entry.getValue()) {
                    LOG.debug("    " + userAgent);
                }
            }
        }
    }

    public interface Normalizer {
        String normalize(String s);
    }

    public static class TokenReplacer implements Normalizer {

        private final Pattern tokenDetector;

        private final Pattern tokenReplacer;

        private final String replacement;

        public TokenReplacer(String tokenRegex, String replacement) {
            this.tokenDetector = Pattern.compile(".*" + tokenRegex + ".*");
            this.tokenReplacer = Pattern.compile(tokenRegex);
            this.replacement = replacement;
        }

        @Override
        public String normalize(String s) {
            do {
                if (!tokenDetector.matcher(s).matches()) {
                    break;
                }
                s = tokenReplacer.matcher(s).replaceAll(replacement);
            } while (true);
            return s;
        }

    }

    public static class BrowserDetector implements Normalizer {

        @Override
        public String normalize(String s) {
            if (!BROWSER_PATTERN.matcher(s).matches()) {
                return s;
            }
            if (FIREFOX_PATTERN.matcher(s).matches() && !SEAMONKEY_PATTERN.matcher(s).matches()) {
                return "BROWSER Firefox";
            }
            if (SEAMONKEY_PATTERN.matcher(s).matches()) {
                return "BROWSER Seamonkey";
            }
            if (CHROME_PATTERN.matcher(s).matches() && !CHROMIUM_PATTERN.matcher(s).matches()) {
                return "BROWSER Chrome";
            }
            if (CHROMIUM_PATTERN.matcher(s).matches()) {
                return "BROWSER Chromium";
            }
            if (SAFARI_PATTERN.matcher(s).matches() && !CHROMIUM_PATTERN.matcher(s).matches() && !CHROME_PATTERN.matcher(s).matches()) {
                return "BROWSER Safari";
            }
            if (OPERA_PATTERN.matcher(s).matches()) {
                return "BROWSER Opera";
            }
            if (MSIE_PATTERN.matcher(s).matches()) {
                return "BROWSER InternetExplorer";
            }
            return "BROWSER ***unknown***";
        }

    }
}
