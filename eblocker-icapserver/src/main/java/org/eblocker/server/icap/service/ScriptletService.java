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
package org.eblocker.server.icap.service;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsible for:
 * <ul>
 *     <li>Finding and reading scriptlets</li>
 *     <li>Inserting parameters into scriptlets</li>
 *     <li>Wrapping scriptlets in an exception handler</li>
 * </ul>
 */
public class ScriptletService {
    public static final String SCRIPTLET_PREFIX = "try {\n";
    public static final String SCRIPTLET_POSTFIX = "\n} catch ( e ) { console.error(`eBlocker scriptlet failed with ${e.name}: ${e.message}`); }\n";
    private final Path scriptletDirectory;

    @Inject
    public ScriptletService(@Named("scriptlets.directory") String scriptletDirectory) {
        this.scriptletDirectory = Path.of(scriptletDirectory);
    }

    public String resolve(String parameterList) throws IOException {
        // Split parameter list by comma + whitespace, except when preceded by a backslash
        String[] parameters = parameterList.split("(?<!\\\\),\\s*");
        if (parameters[0].isEmpty()) {
            throw new IllegalArgumentException("Expected at least a scriptlet name in parameter list");
        }
        String scriptletFilename = parameters[0] + ".js";
        String scriptlet = Files.readString(scriptletDirectory.resolve(scriptletFilename));
        for (int i = 1; i < parameters.length; i++) {
            String value = parameters[i].replace("\\,", ",");
            value = escapeBackslashesInRegExps(value);
            scriptlet = StringUtils.replaceOnce(scriptlet, "{{" + i + "}}", value);
        }
        return SCRIPTLET_PREFIX + scriptlet + SCRIPTLET_POSTFIX;
    }

    private String escapeBackslashesInRegExps(String value) {
        if ((value.startsWith("/") || value.startsWith("!/")) && value.endsWith("/")) {
            return value.replace("\\", "\\\\");
        }
        return value;
    }

}
