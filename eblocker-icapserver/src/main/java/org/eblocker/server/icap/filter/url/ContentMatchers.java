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
package org.eblocker.server.icap.filter.url;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

class ContentMatchers {
    private static Map<String, ContentMatcher> contentMatchersByName = loadMatchers();

    static ContentMatcher get(String name) {
        return contentMatchersByName.get(name);
    }

    private static Map<String, ContentMatcher> loadMatchers() {
        try (InputStream inputStream = ResourceHandler.getInputStream(new SimpleResource("content-matcher.properties"))) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.stringPropertyNames().stream()
                    .map(key -> key.substring(0, key.indexOf(".")))
                    .distinct()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            name -> createMatcher(name, properties)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to initialize content matchers", e);
        }
    }

    private static ContentMatcher createMatcher(String name, Properties properties) {
        String contentTypes[] = properties.getProperty(name + ".contentTypes").split(" ");
        String suffixes[] = properties.getProperty(name + ".suffixes").split(" ");
        return new ContentMatcher(contentTypes, suffixes);
    }
}
