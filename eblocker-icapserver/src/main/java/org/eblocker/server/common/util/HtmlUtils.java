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
package org.eblocker.server.common.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class HtmlUtils {
    private static final String BODY_END_TAG = "</body>";
    private static final String HTML_END_TAG = "</html>";
    private static final StringReplacer REPLACER;

    static {
        REPLACER = new StringReplacer();
        generateLowerUppercaseVariants(BODY_END_TAG).forEach(s -> REPLACER.add(s, ""));
        generateLowerUppercaseVariants(HTML_END_TAG).forEach(s -> REPLACER.add(s, ""));
    }

	private HtmlUtils() {
    }

    /**
     * Inserts the inlay fragment at the end of the &lt;body&gt; content.
     * <p>
     * end-of-body may be implicit as a closing tag may be omitted and correctly doing so requires parsing the dom.
     * To avoid this it is assumed it is closed by (in that order):
     * <ul>
     * <li>explicit: &lt;/body&gt;</li>
     * <li>implicit: &lt;/html&gt;</li>
     * <li>implicit by end-of-file</li>
     * </ul>
     *
     * More details on omitting closing body and html tags:
     * <ul>
     * <li>https://developer.mozilla.org/en-US/docs/Web/HTML/Element/body</li>
     * <li>https://developer.mozilla.org/en-US/docs/Web/HTML/Element/html</li>
     * </ul>
     *
     */
    public static String insertBeforeBodyEnd(String html, String inlay) {
        StringBuilder sb = new StringBuilder(html);
        insertBeforeBodyEnd(sb, inlay);
        return sb.toString();
    }

    /**
     * Inserts the inlay fragment at the end of the &lt;body&gt; content.
     * <p>
     * end-of-body may be implicit as a closing tag may be omitted and correctly doing so requires parsing the dom.
     * To avoid this it is assumed it is closed by (in that order):
     * <ul>
     * <li>explicit: &lt;/body&gt;</li>
     * <li>implicit: &lt;/html&gt;</li>
     * </ul>
     *
     * More details on omitting closing body and html tags:
     * <ul>
     * <li>https://developer.mozilla.org/en-US/docs/Web/HTML/Element/body</li>
     * <li>https://developer.mozilla.org/en-US/docs/Web/HTML/Element/html</li>
     * </ul>
     *
     */
    public static void insertBeforeBodyEnd(StringBuilder html, String inlay) {
	    int position = findLastMatch(html);
        if (position != -1) {
            html.insert(position, inlay);
        }
    }

    private static int findLastMatch(CharSequence html) {
        Integer lastBodyIndex = null;
        Integer lastHtmlIndex = null;
        for(StringReplacer.Match match : REPLACER.findAll(html)) {
            if (BODY_END_TAG.equalsIgnoreCase(match.getTarget())) {
                lastBodyIndex = match.getStartIndex();
            } else {
                lastHtmlIndex = match.getStartIndex();
            }
        }
        if (lastBodyIndex != null) {
            return lastBodyIndex;
        }
        if (lastHtmlIndex != null) {
            return lastHtmlIndex;
        }
        return -1;
    }

    private static Collection<String> generateLowerUppercaseVariants(String s) {
        int n = 1 << s.length();
        Set<String> out = new HashSet<>(n);
        char[] variant = new char[s.length()];
        for(int i = 0; i < n; ++i) {
            for(int j = 0; j < s.length(); ++j) {
                int bit = 1 << j;
                boolean upperCase = (i & bit) == bit;
                variant[j] = upperCase ? Character.toUpperCase(s.charAt(j)) : Character.toLowerCase(s.charAt(j));
            }
            out.add(new String(variant, 0, s.length()));
        }
        return out;
    }
}
