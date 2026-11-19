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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for:
 * <ul>
 *     <li>Finding and reading scriptlets</li>
 *     <li>Inserting parameters into scriptlets</li>
 *     <li>Wrapping scriptlets in an exception handler</li>
 * </ul>
 */
public class ScriptletService {
    private static final String V2_MARKER = "/* eBlocker scriptlet v2;";
    private static final String V2_ARGUMENTS = "__EBLOCKER_SCRIPTLET_ARGS__";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path scriptletDirectory;

    @Inject
    public ScriptletService(@Named("scriptlets.directory") String scriptletDirectory) {
        this.scriptletDirectory = Path.of(scriptletDirectory);
    }

    public String resolve(String parameterList) throws IOException {
        List<String> parameters = splitParameters(parameterList);
        if (parameters.get(0).isEmpty()) {
            throw new IllegalArgumentException("Expected at least a scriptlet name in parameter list");
        }
        String name = parameters.get(0);
        if (!name.matches("[A-Za-z0-9_.-]+") || name.contains("..")) {
            throw new IllegalArgumentException("Invalid scriptlet name");
        }
        String scriptletFilename = name + ".js";
        String scriptlet = Files.readString(scriptletDirectory.resolve(scriptletFilename));
        if (scriptlet.startsWith(V2_MARKER)) {
            if (!scriptlet.contains(V2_ARGUMENTS)) {
                throw new IllegalArgumentException("Scriptlet v2 has no argument placeholder");
            }
            String json = JSON.writeValueAsString(parameters.subList(1, parameters.size()))
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026")
                    .replace("\u2028", "\\u2028")
                    .replace("\u2029", "\\u2029");
            return wrapExceptionHandler(scriptlet.replace(V2_ARGUMENTS, json));
        }
        for (int i = 1; i < parameters.size(); i++) {
            String value = parameters.get(i);
            scriptlet = StringUtils.replaceOnce(scriptlet, "{{" + i + "}}", value);
        }
        return wrapExceptionHandler(scriptlet);
    }

    private List<String> splitParameters(String input) {
        List<String> parameters = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == ',' || next == '\\' || next == quote) {
                    value.append(next);
                    i++;
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                if (quote == 0) { quote = c; continue; }
                if (quote == c) { quote = 0; continue; }
            }
            if (c == ',' && quote == 0) {
                parameters.add(value.toString().trim());
                value.setLength(0);
                continue;
            }
            value.append(c);
        }
        if (quote != 0) throw new IllegalArgumentException("Unclosed scriptlet argument quote");
        parameters.add(value.toString().trim());
        return parameters;
    }

    private String wrapExceptionHandler(String scriptlet) {
        return "try {\n" + scriptlet + "\n} catch ( e ) { }\n";
    }

    public boolean isVersion2(String resolvedCode) {
        return resolvedCode.startsWith("try {\n" + V2_MARKER);
    }
}
