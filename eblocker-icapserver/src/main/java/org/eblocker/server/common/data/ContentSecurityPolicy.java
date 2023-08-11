/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses the HTTP header Content-Security-Policy and inserts/edits the directives that allow the
 * ControlBar to work.
 */
public class ContentSecurityPolicy {
    private List<CspDirective> directives;
    private CspDirective frameSrc, childSrc, defaultSrc, connectSrc, imgSrc, scriptSrc, scriptSrcElem;

    public ContentSecurityPolicy(List<CspDirective> directives) {
        this.directives = directives;
        for (CspDirective directive : directives) {
            String name = directive.getName();
            if ("frame-src".equals(name)) frameSrc = directive;
            else if ("child-src".equals(name)) childSrc = directive;
            else if ("default-src".equals(name)) defaultSrc = directive;
            else if ("connect-src".equals(name)) connectSrc = directive;
            else if ("img-src".equals(name)) imgSrc = directive;
            else if ("script-src".equals(name)) scriptSrc = directive;
            else if ("script-src-elem".equals(name)) scriptSrcElem = directive;
        }
    }

    @Override
    public String toString() {
        return directives.stream()
                .map(CspDirective::toString)
                .collect(Collectors.joining("; "));
    }

    /**
     * Parses Content-Security-Policy from a string
     * @param csp
     * @return
     */
    public static ContentSecurityPolicy from(String csp) {
        return new ContentSecurityPolicy(Arrays.stream(csp.split(";\\s*"))
                .map(str -> CspDirective.from(str))
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    /**
     * The following actions must be allowed for the ControlBar to work:
     * <ul>
     *     <li>Load icons from eBlocker</li>
     *     <li>Allow XMLHttpRequests to eBlocker</li>
     *     <li>Allow loading the ControlBar as an iframe</li>
     *     <li>Allow execution of the injected ControlBar JavaScript</li>
     * </ul>
     *
     * @param controlBarUrl the eBlocker URL from which to load icons, XMLHttpRequests and the ControlBar iframe
     * @param nonce the nonce of the ControlBar JavaScript
     */
    public void allowControlBar(String controlBarUrl, String nonce) {
        if (frameSrc != null) {
            frameSrc.appendValue(controlBarUrl);
        } else {
            addDirective("frame-src", childSrc != null ? childSrc : defaultSrc, controlBarUrl);
        }

        if (connectSrc != null) {
            connectSrc.appendValue(controlBarUrl);
        } else {
            addDirective("connect-src", defaultSrc, controlBarUrl);
        }

        if (imgSrc != null) {
            imgSrc.appendValue(controlBarUrl);
        } else {
            addDirective("img-src", defaultSrc, controlBarUrl);
        }

        String nonceValue = "'nonce-" + nonce + "'";
        if (scriptSrcElem != null && (!scriptSrcElem.hasUnsafeInline() || scriptSrcElem.hasNonce())) {
            scriptSrcElem.appendValue(nonceValue);
        } else {
            CspDirective parent = scriptSrc != null ? scriptSrc : defaultSrc;
            if (parent != null && (!parent.hasUnsafeInline() || parent.hasNonce())) {
                addDirective("script-src-elem", parent, nonceValue);
            }
        }
    }

    private void addDirective(String name, CspDirective parent, String value) {
        if (parent != null) {
            CspDirective newDirective = parent.derive(name);
            newDirective.appendValue(value);
            directives.add(newDirective);
        }
    }

    public static class CspDirective {
        private String[] elements;

        private CspDirective(String[] elements) {
            this.elements = elements;
            if (elements.length > 0) {
                elements[0] = elements[0].toLowerCase();
            }
        }

        public String toString() {
            return String.join(" ", elements);
        }

        public static CspDirective from(String str) {
            return new CspDirective(str.split("\\s+"));
        }

        public boolean isNone() {
            return elements.length == 2 && elements[1].equals("'none'");
        }

        public boolean hasUnsafeInline() {
            for (String element : elements) {
                if ("'unsafe-inline'".equals(element)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasNonce() {
            for (String element : elements) {
                if (element.startsWith("'nonce-")) {
                    return true;
                }
            }
            return false;
        }

        public String getName() {
            return elements[0];
        }

        public void appendValue(String value) {
            if (isNone()) {
                elements[1] = value;
            } else {
                elements = Arrays.copyOf(elements, elements.length + 1);
                elements[elements.length - 1] = value;
            }
        }

        public CspDirective derive(String newName) {
            String[] newElements = Arrays.copyOf(elements, elements.length);
            newElements[0] = newName;
            return new CspDirective(newElements);
        }
    }
}
