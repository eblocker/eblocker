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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HtmlUtilsTest {

    @Test
    public void insertBeforeBodyEnd() {
        String html = "<html><head></head><body><h1>Hello</h1></body></html>";
        String inlay = "<INLAY>";
        String result = HtmlUtils.insertBeforeBodyEnd(html, inlay);
        assertEquals("<html><head></head><body><h1>Hello</h1><INLAY></body></html>", result);
    }

    @Test
    public void insertBeforeBodyEndCaseInsensitive() {
        String html = "<html><head></head><body><h1>Hello</h1></bOdY></html>";
        String inlay = "<INLAY>";
        String result = HtmlUtils.insertBeforeBodyEnd(html, inlay);
        assertEquals("<html><head></head><body><h1>Hello</h1><INLAY></bOdY></html>", result);
    }

    @Test
    public void insertBeforeBodyEndImplicitBodyEndHtml() {
        String html = "<html><head></head><body><h1>Hello</h1></html>";
        String inlay = "<INLAY>";
        String result = HtmlUtils.insertBeforeBodyEnd(html, inlay);
        assertEquals("<html><head></head><body><h1>Hello</h1><INLAY></html>", result);
    }

    @Test
    public void doNotImplicitlyCloseBodyAtEndOfFile() {
        String html = "<html><head></head><body><h1>Hello</h1>";
        String inlay = "<INLAY>";
        String result = HtmlUtils.insertBeforeBodyEnd(html, inlay);
        assertEquals("<html><head></head><body><h1>Hello</h1>", result);
    }

    /**
     * Motivation for this test: some web pages contain closing body tags in JavaScript code.
     * Therefore, we want to insert our inlay before the last closing body tag.
     * See Ticket #69.
     */
    @Test
    public void insertBeforeLastBodyEnd() {
        String html = "<html><head><script>var foo = '</body>';</script></head><body><h1>Hello</h1></body></html>";
        String inlay = "<INLAY>";
        String result = HtmlUtils.insertBeforeBodyEnd(html, inlay);
        assertEquals("<html><head><script>var foo = '</body>';</script></head><body><h1>Hello</h1><INLAY></body></html>", result);
    }
}
