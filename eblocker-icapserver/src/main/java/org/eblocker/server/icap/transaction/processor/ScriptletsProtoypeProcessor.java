/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.transaction.processor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.regex.Pattern;

/**
 * This is a prototype for testing scriptlets.
 *
 * <ul>
 *     <li>Create a configuration file <code>/opt/eblocker-icap/conf/scriptlets.json</code></li>
 *     <li>The configuration contains a list of matchers, e.g.
 *     <pre>
 *         [
 *           {
 *             "pattern": "https://example.com/",
 *             "scriptlet": "test.js"
 *           },
 *           ...
 *         ]
 *     </pre>
 *     </li>
 *     <li>Each pattern is a regular expression that the entire URL must match</li>
 *     <li>Each scriptlet is a file in the directory <code>/opt/eblocker-icap/conf/scriptlets/</code></li>
 *     <li>If the scriptlet name ends with <code>.js</code>, it is enclosed in a JavaScript tag</li>
 *     <li>If the scriptlet name ends with <code>.css</code>, it is enclosed in a style tag</li>
 *     <li>Otherwise, the scriptlet is injected "as is" into the HTML page (before the closing body tag)</li>
 *     <li>All scriptlet files must be encoded in UTF-8</li>
 *     <li>To reload the configuration and all scriptlets, modify the JSON file. Within one minute, all files should be reloaded.</li>
 * </ul>
 */
@Singleton
public class ScriptletsProtoypeProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScriptletsProtoypeProcessor.class);

    private final Path scriptletsConfigurationFile;
    private final String scriptletsDirectory;
    private final ObjectMapper objectMapper;
    private ScriptletMatcher[] matchers = {};
    private FileTime configurationLastModified;
    private long lastConfigurationCheck = 0;

    @Inject
    public ScriptletsProtoypeProcessor(@Named("scriptlets.prototype.configuration.path") String scriptletsConfigurationFile,
                                       @Named("scriptlets.prototype.directory") String scriptletsDirectory,
                                       ObjectMapper objectMapper) {
        this.scriptletsConfigurationFile = Path.of(scriptletsConfigurationFile);
        this.scriptletsDirectory = scriptletsDirectory;
        this.objectMapper = objectMapper;

        parseConfiguration();
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!transaction.isResponse()) {
            return true; // only makes sense for responses
        }

        PageContext pageContext = transaction.getPageContext();

        refreshConfiguration();

        for (ScriptletMatcher matcher: matchers) {
            if (matcher.matches(pageContext.getUrl())) {
                try {
                    transaction.getInjections().inject(matcher.getInjection(scriptletsDirectory));
                } catch (Exception e) {
                    log.error("Could not inject scriptlet into URL {}.", pageContext.getUrl(), e);
                }
            }
        }
        return true;
    }

    /**
     * Check whether the configuration file has been modified, but only once per minute.
     */
    private synchronized void refreshConfiguration() {
        if (System.currentTimeMillis() < lastConfigurationCheck + 60000) {
            return;
        }
        lastConfigurationCheck = System.currentTimeMillis();
        if (!Files.exists(scriptletsConfigurationFile)) {
            return;
        }
        try {
            if (!Files.getLastModifiedTime(scriptletsConfigurationFile).equals(configurationLastModified)) {
                parseConfiguration();
            }
        } catch (Exception e) {
            log.error("Could not get last modified time of configuration file {}. NOT refreshing configuration.", scriptletsConfigurationFile, e);
        }
    }

    private void parseConfiguration() {
        if (Files.exists(scriptletsConfigurationFile)) {
            try {
                matchers = objectMapper.readValue(scriptletsConfigurationFile.toFile(), ScriptletMatcher[].class);
                configurationLastModified = Files.getLastModifiedTime(scriptletsConfigurationFile);
            } catch (Exception e) {
                log.error("Could not read scriptlet configuration {}.", scriptletsConfigurationFile, e);
                return;
            }
        }
    }

    public static class ScriptletMatcher {
        private Pattern pattern;
        private String scriptlet;
        private String injection = null;

        @JsonCreator
        public ScriptletMatcher(@JsonProperty("pattern") String pattern,
                                @JsonProperty("scriptlet") String scriptlet) {
            this.pattern = Pattern.compile(pattern);
            this.scriptlet = scriptlet;
        }

        public boolean matches(String url) {
            return pattern.matcher(url).matches();
        }

        public String getInjection(String scriptletsDirectory) throws IOException {
            if (injection == null) {
                String content = Files.readString(Path.of(scriptletsDirectory, scriptlet), StandardCharsets.UTF_8);
                if (scriptlet.endsWith(".js")) {
                    injection = "<script type=\"text/javascript\">\n" + content + "\n</script>";
                } else if (scriptlet.endsWith(".css")) {
                    injection = "<style>\n" + content + "\n</style>";
                } else {
                    injection = content;
                }
            }
            return injection;
        }
    }
}
