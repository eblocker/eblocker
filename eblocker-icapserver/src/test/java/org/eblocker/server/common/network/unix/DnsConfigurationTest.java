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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DnsConfigurationTest {
    DnsConfiguration dnsConfiguration;

    @Before
    public void setUp() throws Exception {
        String resolvConfPath = ClassLoader.getSystemResource("test-data/resolv.conf").toURI().getPath();
        dnsConfiguration = new DnsConfiguration(resolvConfPath, null);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRead() throws IOException {
        List<String> result = dnsConfiguration.getNameserverAddresses();
        assertEquals(2, result.size());
        assertEquals("192.168.3.20", result.get(0));
        assertEquals("192.168.3.1", result.get(1));
    }

    @Test
    public void testWrite() throws IOException {
        File tempFile = File.createTempFile("DnsConfigurationTest", "-resolv.conf");
        tempFile.deleteOnExit();

        DnsConfiguration output = new DnsConfiguration(null, tempFile.getAbsolutePath());
        output.setNameserverAddresses(Arrays.asList("192.168.1.23", "192.168.1.42"));

        DnsConfiguration input = new DnsConfiguration(tempFile.getAbsolutePath(), null);
        List<String> result = input.getNameserverAddresses();
        assertEquals(2, result.size());
        assertEquals("192.168.1.23", result.get(0));
        assertEquals("192.168.1.42", result.get(1));
    }
}
