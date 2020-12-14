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
package org.eblocker.server.icap.resources;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TemplateExpanderTest {

    private static final SimpleResource SAMPLE_INLAY = new SimpleResource("classpath:test-data/sample-inlay.xhtml");

    @Test
    public void testLoadResourceWithContext() {
        Map<String, String> context = new HashMap<>();
        context.put("@LARUM@", "vero");
        context.put("@LIRUM@", "Lorem");
        String page = ResourceHandler.load(SAMPLE_INLAY);
        page = TemplateExpander.expand(page, context);
        assertNotNull(page);
        assertTrue(page.contains("Lorem"));
        assertTrue(page.contains("vero"));
        assertFalse(page.contains("@LIRUM@"));
        assertFalse(page.contains("@LARUM@"));
    }

}
