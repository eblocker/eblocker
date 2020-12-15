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
package org.eblocker.server.common.network.unix;

import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class IpSetsTest {

    private static final String IP_SUPPORT_CHECK_SCRIPT = "ipset-support";
    private static final String IP_RESTORE_SCRIPT = "ipset-restore";

    private IpSetConfig config;
    private ScriptRunner scriptRunner;
    private IpSets ipSets;

    private String ipsetSaveFileName;
    private String ipsetSaveContent;

    @Before
    public void setup() throws IOException, InterruptedException {
        config = new IpSetConfig("unit-test", "hash:ip,port", 1024);

        scriptRunner = Mockito.mock(ScriptRunner.class);
        Mockito.when(scriptRunner.runScript(Mockito.eq(IP_RESTORE_SCRIPT), Mockito.anyString())).then(im -> {
            ipsetSaveFileName = im.getArgument(1);
            ipsetSaveContent = IOUtils.toString(new FileInputStream(ipsetSaveFileName));
            return 0;
        });

        ipSets = new IpSets(IP_SUPPORT_CHECK_SCRIPT, IP_RESTORE_SCRIPT, scriptRunner);
    }

    @After
    public void tearDown() throws IOException {
        if (ipsetSaveFileName != null) {
            Files.deleteIfExists(Paths.get(ipsetSaveFileName));
        }
    }

    @Test
    public void testTestInitializeSupported() throws IOException, InterruptedException {
        ipSets.initialize();

        Mockito.verify(scriptRunner).runScript(IP_SUPPORT_CHECK_SCRIPT);
        Assert.assertTrue(ipSets.isSupportedByOperatingSystem());
    }

    @Test
    public void testTestInitializeUnsupported() throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(IP_SUPPORT_CHECK_SCRIPT)).thenReturn(1);

        ipSets.initialize();

        Mockito.verify(scriptRunner).runScript(IP_SUPPORT_CHECK_SCRIPT);
        Assert.assertFalse(ipSets.isSupportedByOperatingSystem());
    }

    @Test
    public void testCreateIpSet() throws Exception {
        ipSets.createIpSet(config);
        assertContent("test-data/ipsets-create.ipset", ipsetSaveContent);
    }

    @Test
    public void testUpdateIpSet() throws Exception {
        Set<String> entries = new LinkedHashSet<>(); // ensure iteration order for test
        entries.add("8.8.8.8,udp:53");
        entries.add("8.8.4.4,udp:53");
        ipSets.updateIpSet(config, entries);
        assertContent("test-data/ipsets-update.ipset", ipsetSaveContent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateIpSetTooLarge() throws IOException {
        Set<String> entries = new HashSet<>();
        for (int i = 0; i <= config.getMaxSize(); ++i) {
            entries.add(String.valueOf(i));
        }
        ipSets.updateIpSet(config, entries);
    }

    private void assertContent(String expectedClassPathResource, String actual) throws IOException {
        String expected = IOUtils.toString(ClassLoader.getSystemResource(expectedClassPathResource));
        Assert.assertEquals(expected, actual);
    }
}
