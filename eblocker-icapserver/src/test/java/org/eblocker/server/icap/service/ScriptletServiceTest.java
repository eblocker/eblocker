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

import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class ScriptletServiceTest {
    private ScriptletService service;
    private Path scriptletDirectory;
    private String helloScriptlet = "(function() { alert('{{1}} {{1}} {{2}}'); })();";

    @Before
    public void setUp() throws Exception {
        scriptletDirectory = Files.createTempDirectory(ScriptletServiceTest.class.getSimpleName());
        service = new ScriptletService(scriptletDirectory.toString());
        Files.writeString(scriptletDirectory.resolve("message.js"), helloScriptlet);
    }

    @Test
    public void testResolve() throws IOException {
        String scriptlet = service.resolve("message, Hello\\, world!");

        String expected = ScriptletService.SCRIPTLET_PREFIX +
                "(function() { alert('Hello, world! {{1}} {{2}}'); })();" +
                ScriptletService.SCRIPTLET_POSTFIX;

        Assert.assertEquals(expected, scriptlet);
    }

    @Test
    public void testEscapeBackslashesInRegexps() throws IOException {
        String scriptlet = service.resolve("message, /\\/foo/, !/bar\\//");

        String expected = ScriptletService.SCRIPTLET_PREFIX +
                "(function() { alert('/\\\\/foo/ {{1}} !/bar\\\\//'); })();" +
                ScriptletService.SCRIPTLET_POSTFIX;

        Assert.assertEquals(expected, scriptlet);
    }

    @Test
    public void testDontEscapeBackslashesInStrings() throws IOException {
        String scriptlet = service.resolve("message, Hello\\nworld!");

        String expected = ScriptletService.SCRIPTLET_PREFIX +
                "(function() { alert('Hello\\nworld! {{1}} {{2}}'); })();" +
                ScriptletService.SCRIPTLET_POSTFIX;

        Assert.assertEquals(expected, scriptlet);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoScriptlet() throws IOException {
        service.resolve("");
    }

    @Test(expected = NoSuchFileException.class)
    public void testUnknownScriptlet() throws IOException {
        service.resolve("foo, bar, baz");
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(scriptletDirectory);
    }
}