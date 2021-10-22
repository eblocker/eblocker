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
package org.eblocker.server.icap.filter.content;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.icap.service.ScriptletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 *  Finds matching content filters and creates HTML to inject into the web page
 */
@Singleton
public class ContentFilterService {
    private static final Logger log = LoggerFactory.getLogger(ContentFilterService.class);
    private Map<String, String> cache; // Map hostname to injected HTML
    private ContentFilterList filterList = ContentFilterList.emptyList();
    private final ScriptletService scriptletService;
    private final String elementHidingCss;

    @Inject
    public ContentFilterService(ScriptletService scriptletService, @Named("contentFilter.elementHiding.css") String elementHidingCss) {
        this.scriptletService = scriptletService;
        this.elementHidingCss = elementHidingCss;
        createCache();
    }

    private void createCache() {
        cache = new Hashtable<>();
    }

    public String getHtmlToInject(String hostname) {
        return cache.computeIfAbsent(hostname, this::generateHtmlToInject);
    }

    private String generateHtmlToInject(String hostname) {
        StringBuilder result = new StringBuilder();
        List<ContentFilter> matchingFilters = filterList.getMatchingFilters(hostname);
        generateElementHidingStylesheet(matchingFilters, result);
        generateScriptlets(matchingFilters, result);
        return result.toString();
    }

    private void generateElementHidingStylesheet(List<ContentFilter> matchingFilters, StringBuilder result) {
        StringBuilder styles = new StringBuilder();
        matchingFilters.stream()
                .filter(f -> f instanceof ElementHidingFilter)
                .forEach(f -> {
                    styles.append(((ElementHidingFilter)f).getSelector());
                    styles.append(" {");
                    styles.append(elementHidingCss);
                    styles.append("}\n");
                });
        if (styles.length() > 0) {
            result.append("<style>\n");
            result.append(styles);
            result.append("</style>\n");
        }
    }

    private void generateScriptlets(List<ContentFilter> matchingFilters, StringBuilder result) {
        matchingFilters.stream()
                .filter(f -> f instanceof ScriptletFilter)
                .forEach(f -> {
                    result.append("<script type=\"text/javascript\">\n");
                    result.append(getScriptletCode(((ScriptletFilter)f).getScriptlet()));
                    result.append("</script>\n");
                });
    }

    private String getScriptletCode(String scriptlet) {
        try {
            return scriptletService.resolve(scriptlet);
        } catch (IOException e) {
            log.error("Could not resolve scriptlet: {}", scriptlet, e);
            return "";
        }
    }


    public void setFilterList(ContentFilterList filterList) {
        createCache();
        this.filterList = filterList;
    }
}
